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
}
