#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse


SCRIPT_PATH = Path(__file__).resolve()
REPO_ROOT = SCRIPT_PATH.parent.parent
GRADLE_PROPERTIES_PATH = REPO_ROOT / "gradle.properties"
SEMVER_PATTERN = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
TAG_PATTERN = re.compile(r"^v(?P<version>(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*))$")
RELEASE_WORKFLOW = "release.yml"
JAVADOC_WORKFLOW = "javadoc.yml"
PUBLISH_MAVEN_WORKFLOW = "publish-maven.yml"
RELEASE_REF_WAIT_SECONDS = 30
RELEASE_REF_POLL_SECONDS = 2


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
            raise CliError(
                "Version must use plain semver format X.Y.Z, for example 1.19.2"
            )
        return cls(
            major=int(match.group(1)),
            minor=int(match.group(2)),
            patch=int(match.group(3)),
        )

    def __str__(self) -> str:
        return f"{self.major}.{self.minor}.{self.patch}"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Validate a MagicUtils release version, optionally sync "
            "gradle.properties, and dispatch the GitHub Actions release workflow."
        )
    )
    parser.add_argument("version", help="Release version in X.Y.Z format")
    parser.add_argument(
        "--ref",
        help="Git ref for workflow_dispatch. Defaults to the current branch.",
    )
    parser.add_argument(
        "--repo",
        help="GitHub repository in owner/name format. Defaults to origin remote.",
    )
    parser.add_argument(
        "--allow-dirty",
        action="store_true",
        help="Allow dispatch even when the local worktree has uncommitted changes.",
    )
    parser.set_defaults(sync_gradle_version=True)
    parser.add_argument(
        "--sync-gradle-version",
        dest="sync_gradle_version",
        action="store_true",
        help="Update gradle.properties, commit it, and push the branch before dispatch.",
    )
    parser.add_argument(
        "--no-sync-gradle-version",
        dest="sync_gradle_version",
        action="store_false",
        help="Skip gradle.properties sync and dispatch the workflow without branch prep.",
    )
    parser.add_argument(
        "--dispatch-javadoc",
        action="store_true",
        help="Also dispatch javadoc.yml manually for the same version after release dispatch.",
    )
    parser.add_argument(
        "--dispatch-publish-maven",
        action="store_true",
        help=(
            "Also dispatch publish-maven.yml manually after release dispatch. "
            "Use this as a fallback when workflow_run does not trigger."
        ),
    )
    parser.add_argument(
        "--remote-ref-wait-seconds",
        type=int,
        default=RELEASE_REF_WAIT_SECONDS,
        help=(
            "How long to wait for origin/<ref> to resolve to the pushed local HEAD "
            f"before dispatch. Default: {RELEASE_REF_WAIT_SECONDS}s."
        ),
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Validate and print the workflow commands without dispatching them.",
    )
    return parser.parse_args()


def run_command(*args: str, check: bool = True) -> str:
    completed = subprocess.run(
        args,
        cwd=REPO_ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    if check and completed.returncode != 0:
        stderr = completed.stderr.strip()
        stdout = completed.stdout.strip()
        details = stderr or stdout or f"exit code {completed.returncode}"
        raise CliError(f"Command failed: {' '.join(args)}\n{details}")
    return completed.stdout.strip()


def require_clean_worktree(allow_dirty: bool) -> None:
    if allow_dirty:
        return
    status = run_command("git", "status", "--short")
    if status:
        raise CliError(
            "Working tree is not clean. Commit or stash changes before releasing, "
            "or rerun with --allow-dirty if you explicitly want to dispatch the "
            "workflow from the remote ref only."
        )


def detect_current_branch() -> str:
    branch = run_command("git", "rev-parse", "--abbrev-ref", "HEAD")
    if branch == "HEAD":
        raise CliError(
            "Detached HEAD detected. Pass --ref <branch> only after checking out a branch."
        )
    return branch


def read_gradle_version() -> Version:
    try:
        for line in GRADLE_PROPERTIES_PATH.read_text(encoding="utf-8").splitlines():
            if line.startswith("version="):
                raw_version = line.split("=", 1)[1].strip()
                return Version.parse(raw_version)
    except FileNotFoundError as error:
        raise CliError(f"Missing {GRADLE_PROPERTIES_PATH}.") from error
    raise CliError(f"Could not find 'version=' entry in {GRADLE_PROPERTIES_PATH}.")


def write_gradle_version(version: Version) -> None:
    content = GRADLE_PROPERTIES_PATH.read_text(encoding="utf-8")
    updated_content, replacements = re.subn(
        r"(?m)^version=.*$",
        f"version={version}",
        content,
        count=1,
    )
    if replacements != 1:
        raise CliError(f"Could not update 'version=' entry in {GRADLE_PROPERTIES_PATH}.")
    GRADLE_PROPERTIES_PATH.write_text(updated_content, encoding="utf-8")


def detect_repo_slug(explicit_repo: str | None) -> str:
    if explicit_repo:
        return explicit_repo

    remote_url = run_command("git", "remote", "get-url", "origin")
    if remote_url.startswith("git@github.com:"):
        slug = remote_url.split(":", 1)[1]
    else:
        parsed = urlparse(remote_url)
        if parsed.hostname != "github.com":
            raise CliError(
                "Could not detect a GitHub repository from origin remote. "
                "Pass --repo owner/name explicitly."
            )
        slug = parsed.path.lstrip("/")

    if slug.endswith(".git"):
        slug = slug[:-4]
    if "/" not in slug:
        raise CliError("Resolved GitHub repository slug is invalid. Use --repo owner/name.")
    return slug


def detect_ref(explicit_ref: str | None) -> str:
    if explicit_ref:
        return explicit_ref
    return detect_current_branch()


def normalize_tag(raw: str) -> str | None:
    tag = raw.strip()
    if tag.startswith("refs/tags/"):
        tag = tag[len("refs/tags/") :]
    if tag.endswith("^{}"):
        tag = tag[:-3]
    return tag or None


def parse_tag_version(tag: str) -> Version | None:
    normalized = normalize_tag(tag)
    if normalized is None:
        return None
    match = TAG_PATTERN.fullmatch(normalized)
    if match is None:
        return None
    return Version.parse(match.group("version"))


def load_local_tags() -> set[str]:
    output = run_command("git", "tag", "--list", "v*")
    return {tag.strip() for tag in output.splitlines() if tag.strip()}


def load_remote_tags(repo_slug: str) -> set[str]:
    output = run_command(
        "gh",
        "api",
        "--paginate",
        "--jq",
        ".[].ref",
        f"repos/{repo_slug}/git/matching-refs/tags/v",
    )
    return {
        normalized
        for raw in output.splitlines()
        if (normalized := normalize_tag(raw)) is not None
    }


def collect_known_versions(repo_slug: str) -> tuple[set[str], list[Version]]:
    tags = load_local_tags() | load_remote_tags(repo_slug)
    versions: list[Version] = []
    for tag in tags:
        version = parse_tag_version(tag)
        if version is not None:
            versions.append(version)
    versions.sort()
    return tags, versions


def local_head_sha() -> str:
    return run_command("git", "rev-parse", "HEAD")


def remote_ref_sha(ref: str) -> str | None:
    output = run_command("git", "ls-remote", "origin", f"refs/heads/{ref}")
    if not output:
        return None
    return output.split()[0].strip()


def wait_for_remote_ref(ref: str, expected_sha: str, timeout_seconds: int) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if remote_ref_sha(ref) == expected_sha:
            return
        time.sleep(RELEASE_REF_POLL_SECONDS)
    actual = remote_ref_sha(ref)
    raise CliError(
        f"origin/{ref} did not resolve to {expected_sha} within {timeout_seconds}s. "
        f"Current remote sha: {actual or 'missing'}"
    )


def build_release_command(repo_slug: str, ref: str, version: Version) -> list[str]:
    return [
        "gh",
        "workflow",
        "run",
        RELEASE_WORKFLOW,
        "--repo",
        repo_slug,
        "--ref",
        ref,
        "-f",
        f"version={version}",
    ]


def build_javadoc_command(repo_slug: str, ref: str, version: Version) -> list[str]:
    return [
        "gh",
        "workflow",
        "run",
        JAVADOC_WORKFLOW,
        "--repo",
        repo_slug,
        "--ref",
        ref,
        "-f",
        f"version={version}",
    ]


def build_publish_maven_command(repo_slug: str, ref: str) -> list[str]:
    return [
        "gh",
        "workflow",
        "run",
        PUBLISH_MAVEN_WORKFLOW,
        "--repo",
        repo_slug,
        "--ref",
        ref,
    ]


def render_command(command: list[str]) -> str:
    return " ".join(command)


def prepare_release_ref(
    requested_version: Version,
    current_gradle_version: Version,
    ref: str,
    dry_run: bool,
    remote_ref_wait_seconds: int,
) -> None:
    current_branch = detect_current_branch()
    if ref != current_branch:
        raise CliError(
            f"gradle.properties sync requires --ref to match the current branch "
            f"({current_branch})."
        )

    if requested_version != current_gradle_version:
        if dry_run:
            print(
                f"Release ref prep: would update gradle.properties from "
                f"{current_gradle_version} to {requested_version}"
            )
            print(
                f"Release ref prep: would commit "
                f"'chore(release): bump version to {requested_version}'"
            )
        else:
            write_gradle_version(requested_version)
            run_command("git", "add", str(GRADLE_PROPERTIES_PATH))
            run_command(
                "git",
                "commit",
                "-m",
                f"chore(release): bump version to {requested_version}",
            )
            print(
                f"Release ref prep: committed gradle.properties bump to {requested_version}"
            )

    if dry_run:
        print(f"Release ref prep: would push HEAD to origin/{ref}")
        print(
            "Release ref prep: would wait for origin/"
            f"{ref} to resolve to the pushed local HEAD before dispatch"
        )
        return

    expected_sha = local_head_sha()
    run_command("git", "push", "origin", f"HEAD:{ref}")
    print(f"Release ref prep: pushed HEAD to origin/{ref}")
    wait_for_remote_ref(ref, expected_sha, remote_ref_wait_seconds)
    print(f"Release ref prep: confirmed origin/{ref} -> {expected_sha}")


def main() -> int:
    try:
        args = parse_args()
        requested_version = Version.parse(args.version)
        current_gradle_version = read_gradle_version()
        if requested_version < current_gradle_version:
            raise CliError(
                f"Version {requested_version} must not be lower than "
                f"gradle.properties version {current_gradle_version}."
            )
        require_clean_worktree(args.allow_dirty)
        repo_slug = detect_repo_slug(args.repo)
        ref = detect_ref(args.ref)

        requested_tag = f"v{requested_version}"
        known_tags, known_versions = collect_known_versions(repo_slug)
        if requested_tag in known_tags:
            raise CliError(f"Release tag {requested_tag!r} already exists.")

        latest_version = known_versions[-1] if known_versions else None
        if latest_version is not None and requested_version <= latest_version:
            raise CliError(
                f"Version {requested_version} must be greater than the latest "
                f"release {latest_version}."
            )

        release_command = build_release_command(repo_slug, ref, requested_version)
        javadoc_command = build_javadoc_command(repo_slug, ref, requested_version)
        publish_maven_command = build_publish_maven_command(repo_slug, ref)

        print(f"Repository: {repo_slug}")
        print(f"Ref: {ref}")
        print(f"Version: {requested_version}")
        print(f"Tag: {requested_tag}")
        print(f"gradle.properties version: {current_gradle_version}")
        print(f"Latest release: {latest_version or 'none'}")
        if args.sync_gradle_version:
            if requested_version == current_gradle_version:
                print("gradle.properties sync: release ref already has the requested version locally")
            else:
                print(
                    "gradle.properties sync: will update gradle.properties before dispatch "
                    f"and push the branch to origin/{ref}"
                )
        else:
            print("gradle.properties sync: disabled")
        print(f"Dispatch javadoc: {'yes' if args.dispatch_javadoc else 'no'}")
        print(
            "Dispatch publish-maven fallback: "
            + ("yes" if args.dispatch_publish_maven else "no")
        )
        print("Release workflow command:")
        print("  " + render_command(release_command))
        if args.dispatch_javadoc:
            print("Javadoc workflow command:")
            print("  " + render_command(javadoc_command))
        if args.dispatch_publish_maven:
            print("Publish Maven workflow command:")
            print("  " + render_command(publish_maven_command))

        if args.dry_run:
            if args.sync_gradle_version:
                prepare_release_ref(
                    requested_version=requested_version,
                    current_gradle_version=current_gradle_version,
                    ref=ref,
                    dry_run=True,
                    remote_ref_wait_seconds=args.remote_ref_wait_seconds,
                )
            print("Dry run only, workflows were not dispatched.")
            return 0

        if args.sync_gradle_version:
            prepare_release_ref(
                requested_version=requested_version,
                current_gradle_version=current_gradle_version,
                ref=ref,
                dry_run=False,
                remote_ref_wait_seconds=args.remote_ref_wait_seconds,
            )
        subprocess.run(release_command, cwd=REPO_ROOT, check=True)
        print("Release workflow dispatched successfully.")

        if args.dispatch_javadoc:
            subprocess.run(javadoc_command, cwd=REPO_ROOT, check=True)
            print("Javadoc workflow dispatched successfully.")
        if args.dispatch_publish_maven:
            subprocess.run(publish_maven_command, cwd=REPO_ROOT, check=True)
            print("Publish Maven workflow dispatched successfully.")
        return 0
    except CliError as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1
    except subprocess.CalledProcessError as error:
        print(
            f"Error: workflow dispatch failed with exit code {error.returncode}.",
            file=sys.stderr,
        )
        return error.returncode
    except KeyboardInterrupt:
        print("Interrupted.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
