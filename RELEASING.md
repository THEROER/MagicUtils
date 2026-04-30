# Releasing MagicUtils

Releases are cut from `main`. The `dev` branch is for integration; PRs
land on `dev`, then `dev` is fast-forwarded into `main` before a release.

## Quickstart

```bash
# from a clean worktree on main with up-to-date local refs
python3 scripts/publish_release.py 1.21.4
```

The script preflights, bumps `gradle.properties`, pushes the bump
commit, dispatches `release.yml`, watches every downstream workflow,
and smoke-tests the published artifact. Exit code 0 means the
release is fully live; non-zero means at least one stage failed
(see logs).

## Flags

| Flag | Default | Purpose |
|------|---------|---------|
| `--dry-run` | off | Validate and print actions without making changes. |
| `--allow-dirty` | off | Skip the clean-worktree check. |
| `--no-sync-gradle-version` | sync on | Assume someone already bumped `gradle.properties`. |
| `--no-wait` | wait on | Don't watch downstream workflows; return after dispatch. |
| `--no-smoke-test` | smoke on | Skip the post-publish artifact poll. |
| `--ref BRANCH` | repo default branch | Override the dispatch branch. |
| `--repo OWNER/REPO` | origin remote | Override the GitHub repo. |
| `--smoke-timeout-seconds N` | 1200 | How long to wait for the Maven artifact. |
| `--remote-ref-wait-seconds N` | 30 | How long to wait for `origin/<ref>` to catch up. |

## Pipeline

```
publish_release.py X.Y.Z
   ├── preflight (clean tree, semver, no duplicate tag)
   ├── bump gradle.properties + commit + push <ref>
   └── gh workflow run release.yml -f version=X.Y.Z

release.yml
   ├── validate     ./gradlew build (without Fabric loom modules)
   ├── resolve      version + tag string outputs
   ├── tag          git tag vX.Y.Z && git push origin vX.Y.Z
   └── dispatch-downstream
         gh workflow run docs.yml --ref vX.Y.Z -f alias=stable -f set_default=true
         gh workflow run javadoc.yml --ref vX.Y.Z

docs.yml (from workflow_dispatch)
   └── mkdocs build → mike deploy → push gh-pages

publish-maven.yml (workflow_run after docs success)
   └── ./gradlew publish (default + mc1201 + mc2611 targets)
       → stamp .last-publish marker → push gh-pages

javadoc.yml (from workflow_dispatch)
   └── javadoc per module → push gh-pages under javadoc/X.Y.Z/

publish_release.py (still running)
   ├── watch all four workflows for success
   └── HEAD-poll https://theroer.github.io/.../magicutils-lang-X.Y.Z.pom
```

The chain is fully automatic. If a previous release used the manual
`--dispatch-publish-maven` flag, you no longer need it — release.yml's
`dispatch-downstream` job replaces that fallback.

## Why workflow_dispatch instead of tag-push triggers

GitHub suppresses downstream workflows whose source push was made with
the runner's `GITHUB_TOKEN`. The tag we push from `release.yml` is one
of those, so a `workflow_run` trigger that listens for the docs build
will not fire if docs were started by the tag push.

`release.yml` instead calls `gh workflow run docs.yml` from
`dispatch-downstream`. That is a `workflow_dispatch` event, which has
no such restriction — `publish-maven.yml` then fires through
`workflow_run` as designed.

## Branch model

- `main` — stable, default branch. Release tags and `chore(release):
  bump version` commits live here.
- `dev` — integration. PRs target `dev`; once a batch is ready, open
  a PR `dev → main` and merge it. After merge, run the release script
  on `main`.

## When the pipeline fails

| Stage | Symptom | Action |
|-------|---------|--------|
| `validate` | `./gradlew build` fails | Fix the failing tests on `main`, then re-run the script with the same version. |
| `tag` | "Tag vX.Y.Z already exists" | Either bump to a new patch number or delete the stale tag remotely (`git push origin :refs/tags/vX.Y.Z`) and re-run. |
| `dispatch-downstream` | docs/javadoc not started | Re-run the failed job from the Actions UI; gh CLI: `gh workflow run docs.yml --ref vX.Y.Z -f version=X.Y.Z -f alias=stable -f set_default=true`. |
| `publish-maven` | Maven artifact missing | Inspect `gh run list -w publish-maven.yml`. The job uses `workflow_run` from docs success — if docs failed, fix and rerun docs first. Manual fallback: `gh workflow run publish-maven.yml`. |
| smoke poll timeout | `404` for 20+ minutes | GitHub Pages CDN sometimes lags. Verify the artifact exists in the repo with `git ls-tree origin/gh-pages -- maven/.../X.Y.Z/`. If yes, wait — the CDN will catch up. |

To roll back a release that was tagged but not yet usable:

```bash
git push origin :refs/tags/vX.Y.Z
gh release delete vX.Y.Z -R THEROER/MagicUtils --yes  # if a Release was published
```

The Maven artifact under `gh-pages` is harder to remove and usually
isn't worth removing — bump the next version instead.

## Verification commands

```bash
# Was the chain successful?
gh run list -R THEROER/MagicUtils -w release.yml --limit 1
gh run list -R THEROER/MagicUtils -w docs.yml --limit 1
gh run list -R THEROER/MagicUtils -w publish-maven.yml --limit 1
gh run list -R THEROER/MagicUtils -w javadoc.yml --limit 1

# Is the artifact visible?
curl -fI https://theroer.github.io/MagicUtils/maven/dev/ua/theroer/magicutils-lang/X.Y.Z/magicutils-lang-X.Y.Z.pom

# What is the latest version per maven-metadata?
curl -s https://theroer.github.io/MagicUtils/maven/dev/ua/theroer/magicutils-lang/maven-metadata.xml | grep -E '<latest>|<release>'
```

## FAQ

**Why does the script's `--wait` block my terminal for several minutes?**
By design — you get a single yes/no answer at the end. `Ctrl+C` is safe;
the dispatch already happened and the workflows finish on their own.
Re-run with `--no-wait` if you want fire-and-forget.

**Smoke poll hit timeout but the run was successful — what gives?**
GitHub Pages CDN can hold a stale `maven-metadata.xml` for 5–15
minutes after the gh-pages push. Inspect the gh-pages branch directly
to confirm the artifact landed; the CDN will eventually catch up. We
write a `.last-publish` marker on every publish to nudge Pages into
re-rendering — but it's not a hard guarantee.

**Can I skip validate?**
`gh workflow run release.yml --ref main -f version=X.Y.Z -f skip_validate=true`
on the GitHub UI or CLI. The script always runs validate. Reserve this
for genuine emergencies.

## See also

- `scripts/publish_release.py` — the script.
- `.github/workflows/release.yml` — orchestrator.
- `.github/workflows/docs.yml`, `publish-maven.yml`, `javadoc.yml` — the
  three downstream workflows.
- `scripts/README.md` — short pointer back here.
