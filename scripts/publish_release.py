#!/usr/bin/env python3
"""Validate and dispatch a MagicUtils release.

Workflow:
  1. Preflight: clean tree, semver bump, no duplicate tag.
  2. Sync gradle.properties (commit + push) on the current branch.
  3. Dispatch release.yml on the default branch.
  4. Optionally watch every workflow in the chain to completion.
  5. Optionally smoke-test the published Maven artifact.

The chain release.yml -> docs.yml -> publish-maven.yml + javadoc.yml
auto-dispatches without us — release.yml's dispatch-downstream job uses
gh workflow run for docs/javadoc, which lets workflow_run on
publish-maven fire normally (the GITHUB_TOKEN-pushed-tag block does not
apply to workflow_dispatch).
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse


SCRIPT_PATH = Path(__file__).resolve()
REPO_ROOT = SCRIPT_PATH.parent.parent
GRADLE_PROPERTIES_PATH = REPO_ROOT / "gradle.properties"
PUBLISHING_PROPERTIES_PATH = REPO_ROOT / "gradle" / "publishing.properties"
SEMVER_PATTERN = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
TAG_PATTERN = re.compile(r"^v(?P<version>(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*))$")
RELEASE_WORKFLOW = "release.yml"
DOCS_WORKFLOW = "docs.yml"
PUBLISH_MAVEN_WORKFLOW = "publish-maven.yml"
JAVADOC_WORKFLOW = "javadoc.yml"
WATCHED_WORKFLOWS = (RELEASE_WORKFLOW, DOCS_WORKFLOW, PUBLISH_MAVEN_WORKFLOW, JAVADOC_WORKFLOW)
RELEASE_REF_WAIT_SECONDS = 30
RELEASE_REF_POLL_SECONDS = 2
WORKFLOW_DETECT_TIMEOUT_SECONDS = 60
SMOKE_TIMEOUT_SECONDS = 20 * 60
SMOKE_POLL_SECONDS = 30


@dataclass(frozen=True)
class PublishingProps:
    group: str
    repo_url: str
    repo_owner: str
    repo_name: str
    smoke_artifact: str

    @property
    def group_path(self) -> str:
        return self.group.replace(".", "/")

    @property
    def gh_repo_slug(self) -> str:
        return f"{self.repo_owner}/{self.repo_name}"

    def smoke_artifact_url(self, version: "Version") -> str:
        return (
            f"{self.repo_url}/{self.group_path}/{self.smoke_artifact}/"
            f"{version}/{self.smoke_artifact}-{version}.pom"
        )

    def gh_pages_artifact_path(self, version: "Version") -> str:
        return f"maven/{self.group_path}/{self.smoke_artifact}/{version}/"


class CliError(RuntimeError):
    pass


@dataclass(frozen=True, order=True)
class Version:
    major: int
    minor: int
    patch: int

    @classmethod
    def parse(cls, raw: str) -> "Version":
        match = SEMVER_PATTERN.fullmatch(raw)
        if match is None:
            raise CliError("Version must use plain semver format X.Y.Z, for example 1.21.4")
        return cls(int(match.group(1)), int(match.group(2)), int(match.group(3)))

    def __str__(self) -> str:
        return f"{self.major}.{self.minor}.{self.patch}"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate, sync, and dispatch a MagicUtils release.",
    )
    parser.add_argument("version", help="Release version in X.Y.Z format")
    parser.add_argument("--ref", help="Git ref for workflow_dispatch (defaults to repo default branch).")
    parser.add_argument("--repo", help="GitHub repository in owner/name format (defaults to origin remote).")
    parser.add_argument("--allow-dirty", action="store_true",
                        help="Skip the clean-worktree check.")
    parser.set_defaults(sync_gradle_version=True)
    parser.add_argument("--no-sync-gradle-version", dest="sync_gradle_version", action="store_false",
                        help="Skip gradle.properties sync (assume someone bumped it already).")
    parser.set_defaults(wait=True)
    parser.add_argument("--no-wait", dest="wait", action="store_false",
                        help="Don't watch downstream workflows after dispatch.")
    parser.set_defaults(smoke_test=True)
    parser.add_argument("--no-smoke-test", dest="smoke_test", action="store_false",
                        help="Don't poll the Maven artifact after publish.")
    parser.add_argument(
        "--remote-ref-wait-seconds", type=int, default=RELEASE_REF_WAIT_SECONDS,
        help=f"Seconds to wait for origin/<ref> to catch up before dispatch (default: {RELEASE_REF_WAIT_SECONDS}).",
    )
    parser.add_argument(
        "--smoke-timeout-seconds", type=int, default=SMOKE_TIMEOUT_SECONDS,
        help=f"Smoke test timeout in seconds (default: {SMOKE_TIMEOUT_SECONDS}).",
    )
    parser.add_argument("--dry-run", action="store_true",
                        help="Validate and print actions without making any changes.")
    return parser.parse_args()


def run_command(*args: str, check: bool = True) -> str:
    completed = subprocess.run(args, cwd=REPO_ROOT, text=True, capture_output=True, check=False)
    if check and completed.returncode != 0:
        details = completed.stderr.strip() or completed.stdout.strip() or f"exit code {completed.returncode}"
        raise CliError(f"Command failed: {' '.join(args)}\n{details}")
    return completed.stdout.strip()


def require_clean_worktree(allow_dirty: bool) -> None:
    if allow_dirty:
        return
    if run_command("git", "status", "--short"):
        raise CliError("Working tree is not clean. Commit or stash, or rerun with --allow-dirty.")


def detect_current_branch() -> str:
    branch = run_command("git", "rev-parse", "--abbrev-ref", "HEAD")
    if branch == "HEAD":
        raise CliError("Detached HEAD detected. Pass --ref <branch> only after checking out a branch.")
    return branch


def read_gradle_version() -> Version:
    try:
        for line in GRADLE_PROPERTIES_PATH.read_text(encoding="utf-8").splitlines():
            if line.startswith("version="):
                return Version.parse(line.split("=", 1)[1].strip())
    except FileNotFoundError as error:
        raise CliError(f"Missing {GRADLE_PROPERTIES_PATH}.") from error
    raise CliError(f"Could not find 'version=' entry in {GRADLE_PROPERTIES_PATH}.")


def read_publishing_props() -> PublishingProps:
    """Parse the publishing.properties single source of truth.

    Mirrors loadPublishingSpec() in build-logic so Gradle, the publish
    workflow, and this script all read the same file.
    """
    try:
        text = PUBLISHING_PROPERTIES_PATH.read_text(encoding="utf-8")
    except FileNotFoundError as error:
        raise CliError(f"Missing {PUBLISHING_PROPERTIES_PATH}.") from error

    values: dict[str, str] = {}
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        values[key.strip()] = value.strip()

    def require(key: str) -> str:
        value = values.get(key, "").strip()
        if not value:
            raise CliError(f"Missing or empty '{key}' in {PUBLISHING_PROPERTIES_PATH}.")
        return value

    return PublishingProps(
        group=require("group"),
        repo_url=require("repo.url").rstrip("/"),
        repo_owner=require("repo.owner"),
        repo_name=require("repo.name"),
        smoke_artifact=require("smoke.artifact"),
    )


def write_gradle_version(version: Version) -> None:
    content = GRADLE_PROPERTIES_PATH.read_text(encoding="utf-8")
    updated, replacements = re.subn(r"(?m)^version=.*$", f"version={version}", content, count=1)
    if replacements != 1:
        raise CliError(f"Could not update 'version=' entry in {GRADLE_PROPERTIES_PATH}.")
    GRADLE_PROPERTIES_PATH.write_text(updated, encoding="utf-8")


def detect_repo_slug(explicit_repo: str | None) -> str:
    if explicit_repo:
        return explicit_repo
    remote_url = run_command("git", "remote", "get-url", "origin")
    if remote_url.startswith("git@github.com:"):
        slug = remote_url.split(":", 1)[1]
    else:
        parsed = urlparse(remote_url)
        if parsed.hostname != "github.com":
            raise CliError("Could not detect a GitHub repository from origin remote. Pass --repo owner/name explicitly.")
        slug = parsed.path.lstrip("/")
    if slug.endswith(".git"):
        slug = slug[:-4]
    if "/" not in slug:
        raise CliError("Resolved GitHub repository slug is invalid. Use --repo owner/name.")
    return slug


def detect_default_branch(repo_slug: str) -> str:
    output = run_command("gh", "api", f"repos/{repo_slug}", "--jq", ".default_branch")
    if not output:
        raise CliError(f"Could not detect default branch for {repo_slug}.")
    return output


def detect_ref(explicit_ref: str | None, repo_slug: str) -> str:
    if explicit_ref:
        return explicit_ref
    return detect_default_branch(repo_slug)


def normalize_tag(raw: str) -> str | None:
    tag = raw.strip()
    if tag.startswith("refs/tags/"):
        tag = tag[len("refs/tags/"):]
    if tag.endswith("^{}"):
        tag = tag[:-3]
    return tag or None


def parse_tag_version(tag: str) -> Version | None:
    normalized = normalize_tag(tag)
    if normalized is None:
        return None
    match = TAG_PATTERN.fullmatch(normalized)
    return Version.parse(match.group("version")) if match else None


def collect_known_versions(repo_slug: str) -> tuple[set[str], list[Version]]:
    local = {tag.strip() for tag in run_command("git", "tag", "--list", "v*").splitlines() if tag.strip()}
    remote_raw = run_command(
        "gh", "api", "--paginate", "--jq", ".[].ref",
        f"repos/{repo_slug}/git/matching-refs/tags/v",
    )
    remote = {n for raw in remote_raw.splitlines() if (n := normalize_tag(raw)) is not None}
    tags = local | remote
    versions = sorted(v for tag in tags if (v := parse_tag_version(tag)) is not None)
    return tags, versions


def local_head_sha() -> str:
    return run_command("git", "rev-parse", "HEAD")


def remote_ref_sha(ref: str) -> str | None:
    output = run_command("git", "ls-remote", "origin", f"refs/heads/{ref}")
    return output.split()[0].strip() if output else None


def wait_for_remote_ref(ref: str, expected_sha: str, timeout: int) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if remote_ref_sha(ref) == expected_sha:
            return
        time.sleep(RELEASE_REF_POLL_SECONDS)
    actual = remote_ref_sha(ref) or "missing"
    raise CliError(f"origin/{ref} did not resolve to {expected_sha} within {timeout}s. Current: {actual}")


def prepare_release_ref(requested: Version, current: Version, ref: str, dry_run: bool, wait_seconds: int) -> None:
    current_branch = detect_current_branch()
    if ref != current_branch:
        raise CliError(
            f"gradle.properties sync requires --ref to match the current branch ({current_branch}). "
            f"Switch to {ref} or pass --no-sync-gradle-version."
        )
    if requested != current:
        if dry_run:
            print(f"[dry-run] would bump gradle.properties from {current} to {requested}")
            print(f"[dry-run] would commit 'chore(release): bump version to {requested}'")
        else:
            write_gradle_version(requested)
            run_command("git", "add", str(GRADLE_PROPERTIES_PATH))
            run_command("git", "commit", "-m", f"chore(release): bump version to {requested}")
            print(f"Committed gradle.properties bump to {requested}")
    if dry_run:
        print(f"[dry-run] would push HEAD to origin/{ref}")
        return
    expected = local_head_sha()
    run_command("git", "push", "origin", f"HEAD:{ref}")
    print(f"Pushed HEAD to origin/{ref}")
    wait_for_remote_ref(ref, expected, wait_seconds)
    print(f"Confirmed origin/{ref} -> {expected}")


def dispatch_release(repo_slug: str, ref: str, version: Version, dry_run: bool) -> None:
    cmd = ["gh", "workflow", "run", RELEASE_WORKFLOW, "--repo", repo_slug, "--ref", ref, "-f", f"version={version}"]
    if dry_run:
        print(f"[dry-run] would run: {' '.join(cmd)}")
        return
    subprocess.run(cmd, cwd=REPO_ROOT, check=True)
    print(f"Dispatched {RELEASE_WORKFLOW} on {ref} for {version}")


def latest_run_id(repo_slug: str, workflow: str, since: float) -> int | None:
    """Latest run id for ``workflow`` created at-or-after ``since`` (unix seconds)."""
    output = run_command(
        "gh", "api", f"repos/{repo_slug}/actions/workflows/{workflow}/runs",
        "--jq", ".workflow_runs[0:5] | map({id, created_at})",
    )
    if not output:
        return None
    try:
        runs = json.loads(output)
    except json.JSONDecodeError:
        return None
    for run in runs:
        created = run.get("created_at", "")
        try:
            ts = time.mktime(time.strptime(created, "%Y-%m-%dT%H:%M:%SZ"))
        except ValueError:
            continue
        if ts >= since - 5:  # small clock skew tolerance
            return int(run["id"])
    return None


def wait_for_run(repo_slug: str, workflow: str, since: float) -> bool:
    print(f"Waiting for {workflow}...")
    deadline = time.monotonic() + WORKFLOW_DETECT_TIMEOUT_SECONDS
    run_id: int | None = None
    while time.monotonic() < deadline and run_id is None:
        run_id = latest_run_id(repo_slug, workflow, since)
        if run_id is None:
            time.sleep(3)
    if run_id is None:
        print(f"  could not detect a run for {workflow} within "
              f"{WORKFLOW_DETECT_TIMEOUT_SECONDS}s — skipping")
        return False
    print(f"  watching run {run_id} (https://github.com/{repo_slug}/actions/runs/{run_id})")
    completed = subprocess.run(
        ["gh", "run", "watch", str(run_id), "--repo", repo_slug, "--exit-status"],
        cwd=REPO_ROOT, check=False,
    )
    if completed.returncode == 0:
        print(f"  {workflow} run {run_id}: success")
        return True
    print(f"  {workflow} run {run_id}: failed (exit {completed.returncode})")
    return False


def smoke_test(props: PublishingProps, version: Version, timeout: int) -> bool:
    url = props.smoke_artifact_url(version)
    print(f"Smoke test: polling {url} (timeout {timeout}s)")
    deadline = time.monotonic() + timeout
    attempt = 0
    while time.monotonic() < deadline:
        attempt += 1
        try:
            req = urllib.request.Request(url, method="HEAD")
            with urllib.request.urlopen(req, timeout=10) as response:
                if response.status == 200:
                    print(f"  attempt {attempt}: 200 OK — artifact available")
                    return True
                print(f"  attempt {attempt}: HTTP {response.status}")
        except urllib.error.HTTPError as exc:
            print(f"  attempt {attempt}: HTTP {exc.code}")
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            print(f"  attempt {attempt}: {exc}")
        time.sleep(SMOKE_POLL_SECONDS)
    print(f"  smoke test timed out after {timeout}s")
    print(f"  GitHub Pages CDN can lag — verify directly: "
          f"git ls-tree origin/gh-pages -- {props.gh_pages_artifact_path(version)}")
    return False


def main() -> int:
    try:
        args = parse_args()
        requested = Version.parse(args.version)
        current = read_gradle_version()
        publishing_props = read_publishing_props()
        if requested < current:
            raise CliError(f"Version {requested} must not be lower than gradle.properties {current}.")
        require_clean_worktree(args.allow_dirty)
        repo_slug = detect_repo_slug(args.repo)
        ref = detect_ref(args.ref, repo_slug)
        tag = f"v{requested}"

        known_tags, known_versions = collect_known_versions(repo_slug)
        if tag in known_tags:
            raise CliError(f"Release tag {tag!r} already exists.")
        latest = known_versions[-1] if known_versions else None
        if latest is not None and requested <= latest:
            raise CliError(f"Version {requested} must be greater than latest release {latest}.")

        print(f"Repository:        {repo_slug}")
        print(f"Ref:               {ref} (default branch detection: {'manual' if args.ref else 'auto'})")
        print(f"Version:           {requested}")
        print(f"Tag:               {tag}")
        print(f"gradle.properties: {current}")
        print(f"Latest release:    {latest or 'none'}")
        print(f"Sync gradle:       {'yes' if args.sync_gradle_version else 'no'}")
        print(f"Wait for chain:    {'yes' if args.wait else 'no'}")
        print(f"Smoke test:        {'yes' if args.smoke_test else 'no'}")

        if args.dry_run:
            if args.sync_gradle_version:
                prepare_release_ref(requested, current, ref, dry_run=True,
                                    wait_seconds=args.remote_ref_wait_seconds)
            dispatch_release(repo_slug, ref, requested, dry_run=True)
            print("[dry-run] would wait for release/docs/publish-maven/javadoc")
            if args.smoke_test:
                print(f"[dry-run] would smoke-test artifact at "
                      f"{publishing_props.smoke_artifact_url(requested)}")
            return 0

        if args.sync_gradle_version:
            prepare_release_ref(requested, current, ref, dry_run=False,
                                wait_seconds=args.remote_ref_wait_seconds)
        dispatch_start = time.time()
        dispatch_release(repo_slug, ref, requested, dry_run=False)

        if not args.wait:
            print("Skipping wait — release dispatched. Track in GitHub Actions tab.")
            return 0

        all_ok = True
        for wf in WATCHED_WORKFLOWS:
            if not wait_for_run(repo_slug, wf, dispatch_start):
                all_ok = False

        if args.smoke_test:
            if smoke_test(publishing_props, requested, args.smoke_timeout_seconds):
                print("Release verified.")
            else:
                print("Release dispatched but smoke verification failed. See pointers above.")
                all_ok = False

        return 0 if all_ok else 1
    except CliError as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1
    except subprocess.CalledProcessError as error:
        print(f"Error: workflow dispatch failed with exit code {error.returncode}.", file=sys.stderr)
        return error.returncode
    except KeyboardInterrupt:
        print("Interrupted.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
