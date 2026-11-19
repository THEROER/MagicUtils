Release helpers for MagicUtils are now Gradle tasks (Kotlin), not scripts.

The former `publish_release.py` has been replaced by tasks in the `release`
group, implemented in
`build-logic/src/main/kotlin/dev/ua/theroer/magicutils/build/release/MagicUtilsReleaseTasks.kt`
(pure logic in `MagicUtilsReleaseModel.kt`):

- `./gradlew releasePreflight -Pversion=X.Y.Z` — validate the version against
  `gradle.properties` and existing tags (no changes).
- `./gradlew bumpVersion -Pversion=X.Y.Z` — bump `gradle.properties` + commit.
- `./gradlew dispatchRelease -Pversion=X.Y.Z [-Pref=<branch>]` — `gh workflow
  run release.yml` (optional CI path).
- `./gradlew smokeTest -Pversion=X.Y.Z` — poll the published POM.
- `./gradlew release -Pversion=X.Y.Z` — full local release: preflight → build
  → bump → tag → Maven → Modrinth → Javadoc → verify.

The optional CI chain (tagging, Javadoc dispatch, Reposilite Maven publish)
lives in `.github/workflows/release.yml`, `publish-maven.yml` and
`publish-javadoc.yml`.

For the full release process — pipeline diagram, troubleshooting, and
verification commands — see [`../RELEASING.md`](../RELEASING.md).
