# Releasing MagicUtils

Releases are cut from `main`. The `dev` branch is for integration; PRs
land on `dev`, then `dev` is fast-forwarded into `main` before a release.

## Quickstart

```bash
# from a clean worktree on main with up-to-date local refs
./gradlew release -Pversion=1.21.4
```

`release` chains `releasePreflight` → `bumpVersion` → `dispatchRelease`:
it validates the version, bumps `gradle.properties` and commits, then
dispatches `release.yml`. The server-side chain (tag, docs/javadoc,
gh-pages publish) runs from CI. Verify afterwards with `./gradlew
smokeTest -Pversion=1.21.4` (polls the published POM).

## Release tasks

| Task | Purpose |
|------|---------|
| `releasePreflight -Pversion=X.Y.Z` | Validate version vs `gradle.properties` and existing tags. No changes. |
| `bumpVersion -Pversion=X.Y.Z` | Bump `gradle.properties` and commit. |
| `dispatchRelease -Pversion=X.Y.Z [-Pref=<branch>]` | `gh workflow run release.yml`. |
| `smokeTest -Pversion=X.Y.Z` | Poll the published POM until it appears (20-min timeout). |
| `release -Pversion=X.Y.Z` | Preflight → bump → dispatch. |

Tasks are defined in `build-logic/src/main/kotlin/MagicUtilsReleaseTasks.kt`
(pure logic in `MagicUtilsReleaseModel.kt`). They require `git` and `gh`
in PATH, same as the former script.

## Compatibility smoke + diagnostics gate

Before publishing, the standalone MagicUtils bundle plugin is launched on a
real server per Minecraft version, and the release is gated on the runtime
diagnostics verdict (the plugin decides whether it is healthy enough to ship).
This replaces the former Python `run_compatibility_smoke.py`.

```bash
./gradlew listSmokeMatrix          # show resolved smoke cases
./gradlew runCompatibilitySmoke    # run all cases; gate on diagnostics
./gradlew runCompatibilitySmoke -PsmokeCase=bukkit-paper-121x-1.21.10  # one case
```

The smoke matrix is declared in `settings.gradle` under `magicMatrix { smoke { ... } }`
(per-platform `runTask`, `successPattern`, and `entry(...)` with MC version
ranges). Each case: launch `runTask` → wait for `successPattern` → run the
diagnostics console command → read the exported report → **fail if diagnostics
report any FAIL** (or WARN when `diagnosticsFailOnWarn`). The verdict logic is
`DiagnosticReport.isPublishable(threshold)` (default threshold CRITICAL).

| Task | Purpose |
|------|---------|
| `listSmokeMatrix` | Print resolved smoke cases (`<platform>-<entry>-<mcVersion>`). |
| `runCompatibilitySmoke [-PsmokeCase=<id>]` | Launch server(s), run diagnostics, gate. |

Model/DSL: `MagicUtilsSmokeModel.kt`, `MagicUtilsSmokeDsl.kt`; orchestrator:
`MagicUtilsSmokeTasks.kt`. Consumers of the build-logic plugins get the same
gate by declaring their own `smoke { }` matrix.

## Publishing the build-logic plugins

The `build-logic` plugins are a reusable, independently versioned tool
(`pluginsGroup`/`pluginsVersion` in `build-logic/gradle.properties`, default
`dev.ua.theroer.magicutils.build` / `0.1.0` — separate from the library
version). MagicUtils itself uses them via `includeBuild("build-logic")`
(dogfooding, no version). External consumers add the Reposilite Maven repo
(`https://maven.theroer.dev/releases`) to `pluginManagement` and apply
`id("magicutils.matrix-settings") version "<ver>"`.

```bash
./gradlew -p build-logic publishToMavenLocal        # local
# CI: publish to Reposilite (URL from gradle/publishing.properties repo.url,
# credentials from PUBLISH_USER / PUBLISH_TOKEN)
./gradlew -p build-logic publish -Ppublish_repo=<url>
```

## Pipeline

```
./gradlew release -Pversion=X.Y.Z
   ├── releasePreflight (semver, no duplicate tag, >= current)
   ├── bumpVersion      gradle.properties + commit
   └── dispatchRelease  gh workflow run release.yml -f version=X.Y.Z

release.yml
   ├── validate     ./gradlew buildScenario (non-Fabric platforms)
   ├── resolve      version + tag string outputs
   ├── tag          git tag vX.Y.Z && git push origin vX.Y.Z
   └── dispatch-downstream
         gh workflow run docs.yml --ref vX.Y.Z -f alias=stable -f set_default=true
         gh workflow run javadoc.yml --ref vX.Y.Z

docs.yml (from workflow_dispatch)
   └── mkdocs build → mike deploy → push gh-pages

publish-maven.yml (workflow_run after docs success)
   ├── resolve-matrix  ./gradlew printPublishMatrix   (targets from targets.properties)
   ├── publish (matrix) ./gradlew <tasks> -Ptarget=<t> → PUT into Reposilite releases
   └── publish-plugins  ./gradlew -p build-logic publish → Reposilite releases

javadoc.yml (from workflow_dispatch)
   └── javadoc per module → push gh-pages under javadoc/X.Y.Z/

verify (manual)
   └── ./gradlew smokeTest -Pversion=X.Y.Z
       HEAD-polls https://maven.theroer.dev/releases/.../magicutils-core-X.Y.Z.pom
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
| `validate` | `./gradlew buildScenario` fails | Fix the failing tests on `main`, then re-run `./gradlew release` with the same version. |
| `tag` | "Tag vX.Y.Z already exists" | Either bump to a new patch number or delete the stale tag remotely (`git push origin :refs/tags/vX.Y.Z`) and re-run. |
| `dispatch-downstream` | docs/javadoc not started | Re-run the failed job from the Actions UI; gh CLI: `gh workflow run docs.yml --ref vX.Y.Z -f version=X.Y.Z -f alias=stable -f set_default=true`. |
| `publish-maven` | Maven artifact missing | Inspect `gh run list -w publish-maven.yml`. The job uses `workflow_run` from docs success — if docs failed, fix and rerun docs first. Manual fallback: `gh workflow run publish-maven.yml`. |
| smoke poll timeout | `404` persists | The publish job failed or its credentials were wrong. Check `gh run list -w publish-maven.yml` and that the `MAVEN_PUBLISH_USER`/`MAVEN_PUBLISH_TOKEN` secrets are set. Reposilite serves artifacts immediately (no CDN lag) — a lasting 404 means it wasn't uploaded. |

To roll back a release that was tagged but not yet usable:

```bash
git push origin :refs/tags/vX.Y.Z
gh release delete vX.Y.Z -R THEROER/MagicUtils --yes  # if a Release was published
```

The Maven artifact in Reposilite can be removed via its web UI / API, but
usually isn't worth removing — bump the next version instead.

## Verification commands

```bash
# Was the chain successful?
gh run list -R THEROER/MagicUtils -w release.yml --limit 1
gh run list -R THEROER/MagicUtils -w docs.yml --limit 1
gh run list -R THEROER/MagicUtils -w publish-maven.yml --limit 1
gh run list -R THEROER/MagicUtils -w javadoc.yml --limit 1

# Is the artifact visible?
curl -fI https://maven.theroer.dev/releases/dev/ua/theroer/magicutils-lang/X.Y.Z/magicutils-lang-X.Y.Z.pom

# What is the latest version per maven-metadata?
curl -s https://maven.theroer.dev/releases/dev/ua/theroer/magicutils-lang/maven-metadata.xml | grep -E '<latest>|<release>'
```

## FAQ

**Does `./gradlew release` wait for the CI chain?**
No — it dispatches `release.yml` and returns. Track progress in the
GitHub Actions tab, then confirm with `./gradlew smokeTest
-Pversion=X.Y.Z` once publish-maven has run.

**Smoke poll hit timeout but the run was successful — what gives?**
Reposilite serves uploaded artifacts immediately, so a persistent 404
means the publish job didn't actually upload (failed task or missing
`MAVEN_PUBLISH_USER`/`MAVEN_PUBLISH_TOKEN` secrets), not a propagation
delay. Check the `publish-maven.yml` run and hit the artifact URL
directly with `curl -fI`.

**Can I skip validate?**
`gh workflow run release.yml --ref main -f version=X.Y.Z -f skip_validate=true`
on the GitHub UI or CLI. `dispatchRelease` does not set this — reserve
it for genuine emergencies.

## See also

- `build-logic/src/main/kotlin/MagicUtilsReleaseTasks.kt` — the `release`
  group tasks (pure logic in `MagicUtilsReleaseModel.kt`).
- `.github/workflows/release.yml` — orchestrator.
- `.github/workflows/docs.yml`, `publish-maven.yml`, `javadoc.yml` — the
  three downstream workflows.
- `scripts/README.md` — short pointer back here.
