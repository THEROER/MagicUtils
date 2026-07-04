#!/usr/bin/env python3
"""DEPRECATED — release orchestration moved to Gradle (Kotlin).

The former Python release script has been replaced by tasks in the
`release` group. Use:

    ./gradlew release        -Pversion=X.Y.Z   # preflight -> bump -> dispatch
    ./gradlew releasePreflight -Pversion=X.Y.Z # validate only
    ./gradlew smokeTest      -Pversion=X.Y.Z   # verify the published POM

Implementation: build-logic/src/main/kotlin/MagicUtilsReleaseTasks.kt
(pure logic in MagicUtilsReleaseModel.kt). See RELEASING.md.

This shim exists only to point stragglers at the new command; it makes
no changes and exits non-zero so nothing silently depends on it.
"""
import sys

print(__doc__, file=sys.stderr)
sys.exit(2)
