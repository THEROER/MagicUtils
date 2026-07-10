import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import dev.ua.theroer.magicutils.build.release.*
import dev.ua.theroer.magicutils.build.publish.*
import dev.ua.theroer.magicutils.build.target.javaSuffixedCoordinate

class MagicUtilsReleaseModelTest {

    @Test
    fun `semver parses and rejects non-semver`() {
        assertEquals(SemanticVersion(1, 21, 4), SemanticVersion.parse("1.21.4"))
        assertThrows(GradleException::class.java) { SemanticVersion.parse("1.21") }
        assertThrows(GradleException::class.java) { SemanticVersion.parse("v1.2.3") }
        assertThrows(GradleException::class.java) { SemanticVersion.parse("01.2.3") }
    }

    @Test
    fun `semver ordering`() {
        assert(SemanticVersion(1, 2, 3) < SemanticVersion(1, 3, 0))
        assert(SemanticVersion(2, 0, 0) > SemanticVersion(1, 99, 99))
    }

    @Test
    fun `fromTag extracts version or null`() {
        assertEquals(SemanticVersion(1, 21, 4), SemanticVersion.fromTag("v1.21.4"))
        assertEquals(SemanticVersion(1, 21, 4), SemanticVersion.fromTag("refs/tags/v1.21.4^{}"))
        assertNull(SemanticVersion.fromTag("1.21.4"))
        assertNull(SemanticVersion.fromTag("release-1.2.3"))
    }

    @Test
    fun `readGradleVersion finds version line`() {
        val text = "org.gradle.caching=true\nversion=1.21.5\nfoo=bar"
        assertEquals(SemanticVersion(1, 21, 5), readGradleVersion(text))
        assertThrows(GradleException::class.java) { readGradleVersion("no=version=here") }
    }

    @Test
    fun `bumpGradleVersion replaces only the version line`() {
        val text = "version=1.21.5\nother=1.0.0\n"
        val bumped = bumpGradleVersion(text, SemanticVersion(1, 21, 6))
        assertEquals("version=1.21.6\nother=1.0.0\n", bumped)
    }

    @Test
    fun `smokeArtifactUrl percent-encodes the java-suffixed coordinate`() {
        val spec = MagicUtilsPublishingSpec(
            group = "dev.ua.theroer",
            repoUrl = "https://maven.theroer.dev/releases",
            smokeArtifact = "magicutils-core",
        )
        // The library ships only per-Java coordinates; the smoke URL must target
        // one of them (`+` percent-encoded), not a bare X.Y.Z POM that is never
        // published (which would 404 forever).
        assertEquals(
            "https://maven.theroer.dev/releases/dev/ua/theroer/magicutils-core/" +
                "1.26.0%2Bjava21/magicutils-core-1.26.0%2Bjava21.pom",
            spec.smokeArtifactUrl(javaSuffixedCoordinate("1.26.0", 21)),
        )
    }

    @Test
    fun `validateReleaseVersion enforces monotonic increase and no dup tag`() {
        val current = SemanticVersion(1, 21, 5)
        // OK: greater than current and latest, tag not taken
        validateReleaseVersion(SemanticVersion(1, 21, 6), current, SemanticVersion(1, 21, 5), emptySet())
        // lower than current
        assertThrows(GradleException::class.java) {
            validateReleaseVersion(SemanticVersion(1, 21, 4), current, null, emptySet())
        }
        // duplicate tag
        assertThrows(GradleException::class.java) {
            validateReleaseVersion(SemanticVersion(1, 21, 6), current, null, setOf("v1.21.6"))
        }
        // not greater than latest released
        assertThrows(GradleException::class.java) {
            validateReleaseVersion(SemanticVersion(1, 21, 6), current, SemanticVersion(1, 21, 6), emptySet())
        }
    }

    @Test
    fun `validateReleaseVersion allows a resume when gradle_properties is already at the version`() {
        // A prior release run bumped to 1.21.6 and tagged v1.21.6, then failed
        // during publish. Re-running must NOT reject the existing tag / version —
        // requested == current signals a resume, not a fresh release.
        val current = SemanticVersion(1, 21, 6)
        validateReleaseVersion(
            requested = SemanticVersion(1, 21, 6),
            current = current,
            latestReleased = SemanticVersion(1, 21, 6),
            existingTags = setOf("v1.21.6"),
        ) // does not throw
    }

    @Test
    fun `parseModrinthVersionIds pairs version_number to id and ignores nested ids`() {
        val json = """
            [
              {"id":"aaa111","version_number":"1.26.0","files":[{"id":"nested-file-id"}]},
              {"id":"bbb222","version_number":"1.25.0"}
            ]
        """.trimIndent()
        val map = parseModrinthVersionIds(json)
        assertEquals(mapOf("1.26.0" to "aaa111", "1.25.0" to "bbb222"), map)
    }

    @Test
    fun `evaluateReleaseConsistency passes when required sources agree and Modrinth may lag`() {
        val v = SemanticVersion(1, 26, 0)
        // Maven + tag + gradle.properties agree; Modrinth not yet published (manual) — still consistent.
        val report = evaluateReleaseConsistency(
            version = v,
            gradlePropertiesVersion = v,
            tagExists = true,
            mavenPublished = true,
            modrinthPublished = false,
        )
        assertTrue(report.consistent)
        assertTrue(report.problems.isEmpty())
        // Modrinth-absent still surfaces as an ABSENT status line for visibility.
        assertEquals(SourceState.ABSENT, report.statuses.single { it.source == "Modrinth" }.state)
    }

    @Test
    fun `evaluateReleaseConsistency fails when a required source disagrees`() {
        val v = SemanticVersion(1, 26, 0)
        val report = evaluateReleaseConsistency(
            version = v,
            gradlePropertiesVersion = SemanticVersion(1, 25, 0),
            tagExists = false,
            mavenPublished = false,
            modrinthPublished = null,
        )
        assertFalse(report.consistent)
        // gradle.properties, tag, Maven each contribute a problem; Modrinth (null) does not.
        assertEquals(3, report.problems.size)
        assertEquals(SourceState.SKIPPED, report.statuses.single { it.source == "Modrinth" }.state)
    }

    @Test
    fun `releasePlan lists steps in fixed order with enabled flags from the spec`() {
        val spec = MagicUtilsReleaseSpec(publishModrinth = false, push = true)
        val plan = releasePlan(spec)
        assertEquals(
            listOf(
                "releasePreflight", "releaseValidateBuild", "bumpVersion", "releaseTag",
                "releaseMavenAll", "releaseModrinth", "releaseJavadoc", "verifyReleaseConsistency",
            ),
            plan.map { it.name },
        )
        assertFalse(plan.single { it.name == "releaseModrinth" }.enabled)
        assertTrue(plan.single { it.name == "releaseMavenAll" }.enabled)
    }

    @Test
    fun `applyReleaseOverrides merges -Prelease flags over the DSL spec`() {
        val spec = MagicUtilsReleaseSpec() // all defaults (modrinth on, push off)
        val merged = applyReleaseOverrides(
            spec,
            mapOf("release.modrinth" to "false", "release.push" to "true", "release.maven" to "FALSE"),
        )
        assertFalse(merged.publishModrinth)
        assertTrue(merged.push)
        assertFalse(merged.publishMaven) // case-insensitive
        assertTrue(merged.publishJavadoc) // untouched default
    }

    @Test
    fun `javadoc urls build the latest and versioned coordinate`() {
        val repo = "https://maven.theroer.dev/releases"
        assertEquals(
            "https://maven.theroer.dev/releases/dev/ua/theroer/magicutils-javadoc/latest/magicutils-javadoc.zip",
            javadocLatestUrl(repo, "dev.ua.theroer"),
        )
        assertEquals(
            "https://maven.theroer.dev/releases/dev/ua/theroer/magicutils-javadoc/1.27.0/magicutils-javadoc.zip",
            javadocVersionUrl(repo, "dev.ua.theroer", "1.27.0"),
        )
    }

    @Test
    fun `applyReleaseOverrides ignores non-boolean values and keeps the DSL default`() {
        val spec = MagicUtilsReleaseSpec(publishModrinth = true)
        val merged = applyReleaseOverrides(spec, mapOf("release.modrinth" to "maybe"))
        assertTrue(merged.publishModrinth) // garbage ignored, default stands
    }
}
