# Releasing MagicUtils

Releases are cut from `main`. The `dev` branch is for integration; PRs
land on `dev`, then `dev` is fast-forwarded into `main` before a release.

## Quickstart

```bash
# from a clean worktree on main with up-to-date local refs
./gradlew release -Pversion=1.21.4 -Prelease.push=true
```

`release` runs the whole release **locally** — it does not dispatch CI:

```
releasePreflight → releaseValidateBuild → bumpVersion → releaseTag
  → releaseMavenAll → releaseModrinth → releaseJavadoc → verifyReleaseConsistency
```

Every step is individually switchable (see [Steps](#steps)), and the tag is
created locally but **not pushed** unless `-Prelease.push=true` is passed.

Publishing needs credentials — see [Credentials](#credentials). On a slow
upstream link, publish from CI instead: see [Publishing from CI](#publishing-from-ci).

## Release tasks

| Task | Purpose |
|------|---------|
| `releasePreflight -Pversion=X.Y.Z` | Validate version vs `gradle.properties`, existing tags, and the release branch. No changes. |
| `releaseValidateBuild` | Build the non-Fabric platforms as a pre-publish gate. |
| `bumpVersion -Pversion=X.Y.Z` | Bump `gradle.properties` and commit. No-op when already at the version. |
| `releaseTag -Pversion=X.Y.Z [-Prelease.push=true]` | Create `vX.Y.Z`; push only when asked. Skips an existing tag. |
| `releaseMavenAll -Pversion=X.Y.Z` | Publish the module matrix (one child build per target) plus the build-logic plugins. |
| `releaseModrinth -Pversion=X.Y.Z` | Build the bundles per target and upload one Modrinth version per artifact. |
| `releaseJavadoc` | Build and upload the aggregated Javadoc zip. |
| `verifyReleaseConsistency -Pversion=X.Y.Z` | Cross-check gradle.properties, tag, Maven, Modrinth. |
| `release -Pversion=X.Y.Z` | All of the above, in that order. |
| `dispatchRelease -Pversion=X.Y.Z [-Pref=<branch>]` | Optional: `gh workflow run release.yml` for the CI path. Not part of `release`. |
| `smokeTest -Pversion=X.Y.Z` | Poll the published POM until it appears (20-min timeout). |

Tasks are defined in `build-logic/src/main/kotlin/.../release/MagicUtilsReleaseTasks.kt`
(pure logic in `MagicUtilsReleaseModel.kt`). They require `git` in PATH, and `gh`
only for `dispatchRelease`.

### Steps

The enabled steps come from `magicMatrix { release { } }` in `settings.gradle.kts`
and can be overridden per invocation with `-Prelease.<step>=true|false`:

| Property | Default | Step |
|----------|---------|------|
| `-Prelease.validateVersion` | on | `releasePreflight` |
| `-Prelease.validateBuild` | on | `releaseValidateBuild` (the slow one) |
| `-Prelease.bump` | on | `bumpVersion` |
| `-Prelease.tag` | on | `releaseTag` |
| `-Prelease.push` | **off** | push the tag to origin |
| `-Prelease.publishMaven` | on | `releaseMavenAll` |
| `-Prelease.publishModrinth` | on | `releaseModrinth` |
| `-Prelease.publishJavadoc` | on | `releaseJavadoc` |
| `-Prelease.verify` | on | `verifyReleaseConsistency` |
| `-Prelease.dryRun` | off | print what would be published instead of uploading |
| `-Prelease.branch=<name>` | `main` | branch a release may run from |
| `-Prelease.allowAnyBranch=true` | off | bypass the branch gate once (genuine hotfix) |

Every publish step is resumable: uploads pass `-Pskip_existing`, which HEADs each
POM and skips what is already in the (immutable) repository, and `publishToModrinth`
skips a `version_number` that already exists. Re-running after a failure only
uploads what is missing.

## Credentials

Resolved as a Gradle property first, then an environment variable:

| Purpose | Gradle property | Environment variable |
|---------|-----------------|----------------------|
| Reposilite user | `publish_user` | `PUBLISH_USER` |
| Reposilite token | `publish_password` | `PUBLISH_TOKEN` |
| Modrinth token | `modrinth_token` | `MODRINTH_TOKEN` |

Put them in `~/.gradle/gradle.properties` (outside the repository, so they are
never committed). CI reads the Reposilite pair from the `MAVEN_PUBLISH_USER` /
`MAVEN_PUBLISH_TOKEN` secrets; there is no Modrinth secret, so Modrinth is
published locally.

## Publishing from CI

The artifacts are large — the Fabric bundle is ~17 MB — and maven.theroer.dev sits
behind a Cloudflare tunnel whose request timeout is ~100 s. An upstream link slower
than roughly 200 KB/s therefore cannot upload the bundles at all: the PUT is cut
off with `524` no matter how many times it is retried. Everything below ~11 MB
still goes through.

When that applies, skip `releaseMavenAll` locally and let a runner do it:

```bash
./gradlew release -Pversion=X.Y.Z -Prelease.push=true -Prelease.publishMaven=false
gh workflow run publish-maven.yml --ref main -f version=X.Y.Z
```

`publish-maven.yml` publishes the full module matrix and the build-logic plugins
with `-Pskip_existing`, so it is safe to re-run and safe to combine with a partial
local publish. `publish-javadoc.yml` does the same for the aggregated Javadoc.
Modrinth has no such limit — its uploads are slow on a thin link but do not time
out — so `releaseModrinth` can always run locally.

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

Both commands publish **four** artifacts, all at `pluginsVersion`:

| Artifact | Carries | Extra toolchain |
| --- | --- | --- |
| `magicutils-build-logic` | matrix/target/publish/release/smoke, module + bundle plugins, `consumer-common` | none |
| `magicutils-build-logic-fabric` | `fabric-module`, `fabric-bundle`, `consumer-fabric` | Fabric Loom |
| `magicutils-build-logic-neoforge` | `neoforge-bundle`, `consumer-neoforge` | ModDevGradle |
| `magicutils-build-logic-jvm` | `consumer-bukkit`, `consumer-velocity`, `consumer-bungee` | jpenilla run-* |

The split exists so a consumer resolves only its own platform's toolchain — a NeoForge
mod on 1.21.1 must never be forced to fetch Fabric Loom, which pins a far newer Gradle.
Nothing changes for consumers: they still apply plugins by id, and each plugin's marker
points at the right artifact.

`publish`/`publishToMavenLocal` are per-project tasks, so the root build-logic project
aggregates the subprojects into them — publishing the neutral artifact alone would leave
`magicutils.consumer-fabric` and friends unresolvable.

## Pipeline

The default path is local:

```
./gradlew release -Pversion=X.Y.Z -Prelease.push=true
   ├── releasePreflight        semver, no duplicate tag, >= current, on the release branch
   ├── releaseValidateBuild    buildScenario for bukkit,bungee,velocity,neoforge
   ├── bumpVersion             gradle.properties + commit (skipped when already there)
   ├── releaseTag              git tag vX.Y.Z [+ push]
   ├── releaseMavenAll         one child build per target → PUT into Reposilite,
   │                           then -p build-logic publish (the four plugin artifacts)
   ├── releaseModrinth         build the bundles per target → one Modrinth version
   │                           per artifact, named X.Y.Z-<channel>-<platform>-java<N>
   ├── releaseJavadoc          aggregated Javadoc zip → Reposilite
   └── verifyReleaseConsistency
```

The CI path stays available for the Maven half, and is the only workable one on a
slow uplink (see [Publishing from CI](#publishing-from-ci)):

```
./gradlew dispatchRelease -Pversion=X.Y.Z
   └── release.yml
        ├── validate     ./gradlew buildScenario (non-Fabric platforms)
        ├── resolve      version + tag string outputs
        ├── tag          git tag vX.Y.Z && git push origin vX.Y.Z
        └── dispatch-downstream
              gh workflow run publish-maven.yml --ref vX.Y.Z -f version=X.Y.Z

publish-maven.yml (also runnable directly via workflow_dispatch)
   ├── resolve-matrix  ./gradlew printPublishMatrix   (targets from targets.properties)
   ├── publish (matrix) ./gradlew <tasks> -Ptarget=<t> -Pskip_existing → Reposilite
   └── publish-plugins  ./gradlew -p build-logic publish -Pskip_existing → Reposilite

verify (manual)
   └── ./gradlew smokeTest -Pversion=X.Y.Z
       HEAD-polls https://maven.theroer.dev/releases/.../magicutils-core-X.Y.Z.pom
```

Documentation is not part of this pipeline — the docs site lives in the separate
MagicUtilsWebsite (Nuxt) project. Javadoc jars are published to Reposilite
alongside the artifacts.

## Why workflow_dispatch instead of tag-push triggers

GitHub suppresses downstream workflows whose source push was made with the
runner's `GITHUB_TOKEN`. The tag pushed from `release.yml` is one of those, so a
`workflow_run` trigger would not fire. Instead `release.yml`'s
`dispatch-downstream` job calls `gh workflow run publish-maven.yml` directly —
a `workflow_dispatch` event, which has no such restriction.

## Branch model

- `main` — stable, default branch. Release tags and `chore(release):
  bump version` commits live here.
- `dev` — integration. PRs target `dev`; once a batch is ready, open
  a PR `dev → main` and merge it. After merge, run `./gradlew release`
  on `main`.

## When the pipeline fails

| Stage | Symptom | Action |
|-------|---------|--------|
| `validate` | `./gradlew buildScenario` fails | Fix the failing tests on `main`, then re-run `./gradlew release` with the same version. |
| `tag` | "Tag vX.Y.Z already exists" | Either bump to a new patch number or delete the stale tag remotely (`git push origin :refs/tags/vX.Y.Z`) and re-run. |
| any upload | `524` from maven.theroer.dev | The file is larger than the tunnel's ~100 s timeout allows at your upstream speed. Publish that half from CI — see [Publishing from CI](#publishing-from-ci). Re-running locally will not help. |
| any upload | `409 Conflict` | The path already holds a file and the repo is immutable. Expected without `-Pskip_existing`; with it, check whether a partial upload left a file behind without its POM (that combination defeats the skip). |
| local publish | JVM dies with "insufficient memory" / worker daemon exits 1 | Too many Gradle daemons: the release forks a child build per target with its own daemon. `./gradlew --stop`, then re-run with `--max-workers=1`. |
| `dispatch-downstream` | publish-maven not started | Re-run the failed job from the Actions UI; gh CLI: `gh workflow run publish-maven.yml --ref vX.Y.Z -f version=X.Y.Z`. |
| `publish-maven` | Maven artifact missing | Inspect `gh run list -w publish-maven.yml`. Manual run: `gh workflow run publish-maven.yml -f version=X.Y.Z`. Check the `MAVEN_PUBLISH_USER`/`MAVEN_PUBLISH_TOKEN` secrets are set. |
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
gh run list -R THEROER/MagicUtils -w publish-maven.yml --limit 1

# Is the artifact visible?
curl -fI https://maven.theroer.dev/releases/dev/ua/theroer/magicutils-lang/X.Y.Z/magicutils-lang-X.Y.Z.pom

# What is the latest version per maven-metadata?
curl -s https://maven.theroer.dev/releases/dev/ua/theroer/magicutils-lang/maven-metadata.xml | grep -E '<latest>|<release>'
```

## FAQ

**Does `./gradlew release` use CI?**
No. It publishes from your machine, start to finish, and only returns when
everything is uploaded. `dispatchRelease` is the opt-in CI path and is not part
of `release`; use it (or `publish-maven.yml` directly) when the uplink cannot
carry the bundles.

**A step failed halfway — is it safe to just re-run `release`?**
Yes. Every step is idempotent: `bumpVersion` no-ops when the version is already
set, `releaseTag` skips an existing tag, Maven uploads pass `-Pskip_existing`,
and Modrinth skips version numbers that already exist. Re-running finishes what
is missing rather than starting over.

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

- `build-logic/src/main/kotlin/dev/ua/theroer/magicutils/build/release/` — the
  `release` group tasks (`MagicUtilsReleaseTasks.kt`, `MagicUtilsReleasePublishTasks.kt`,
  `MagicUtilsModrinthTasks.kt`), with the pure logic in `MagicUtilsReleaseModel.kt`
  and the step list in `MagicUtilsReleaseSpec.kt`.
- `.github/workflows/release.yml` — orchestrator for the optional CI path.
- `.github/workflows/publish-maven.yml` — the Maven publish (module matrix + plugins).
- `.github/workflows/publish-javadoc.yml` — the aggregated Javadoc upload.
- `scripts/README.md` — short pointer back here.
