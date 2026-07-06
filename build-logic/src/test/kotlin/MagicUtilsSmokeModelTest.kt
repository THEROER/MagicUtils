import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import dev.ua.theroer.magicutils.build.smoke.*

class MagicUtilsSmokeModelTest {

    @Test
    fun `expandVersionRange returns endpoints for numeric ranges`() {
        assertEquals(listOf("1.20", "1.20.4"), expandVersionRange("1.20-1.20.4"))
        assertEquals(listOf("1.21"), expandVersionRange("1.21"))
        assertEquals(listOf("26.1", "26.2"), expandVersionRange("26.1-26.2"))
    }

    @Test
    fun `expandVersionRange leaves non-numeric ranges intact`() {
        // e.g. velocity snapshot values with a dash that is not a version range
        assertEquals(listOf("3.3.0-SNAPSHOT"), expandVersionRange("3.3.0-SNAPSHOT"))
    }

    @Test
    fun `resolvedSmokeValues prefers explicit smokeValues`() {
        val entry = SmokeMatrixEntry(
            id = "paper-121x",
            versions = listOf("1.21-1.21.11"),
            smokeValues = listOf("1.21", "1.21.7", "1.21.11"),
        )
        assertEquals(listOf("1.21", "1.21.7", "1.21.11"), entry.resolvedSmokeValues())
    }

    @Test
    fun `resolvedSmokeValues falls back to expanded versions`() {
        val entry = SmokeMatrixEntry(id = "e", versions = listOf("1.20-1.20.6"))
        assertEquals(listOf("1.20", "1.20.6"), entry.resolvedSmokeValues())
    }

    @Test
    fun `toSmokeCases builds one case per smoke value with merged properties`() {
        val spec = SmokePlatformSpec(
            name = "bukkit",
            runTask = ":bukkit-bundle:runServer --args='nogui'",
            defaultSuccessPattern = "Done (",
            versionMatrix = listOf(
                SmokeMatrixEntry(
                    id = "paper-121x",
                    versions = listOf("1.21", "1.21.11"),
                    gradleProperties = mapOf("target" to "mc12111"),
                    smokeGradleProperties = mapOf("1.21.11" to mapOf("paperVersion" to "26.2.build.40-alpha")),
                ),
            ),
        )
        val cases = spec.toSmokeCases(defaultTarget = "mc12110")
        assertEquals(2, cases.size)
        val first = cases.first { it.minecraftVersion == "1.21" }
        val last = cases.first { it.minecraftVersion == "1.21.11" }
        assertEquals("bukkit-paper-121x-1.21", first.id)
        assertTrue(first.gradleCommand.contains(":bukkit-bundle:runServer"))
        assertTrue(first.gradleCommand.contains("-Ptarget=mc12111"))
        // per-version override only applies to 1.21.11
        assertTrue(last.gradleCommand.contains("-PpaperVersion=26.2.build.40-alpha"))
        assertTrue(!first.gradleCommand.contains("paperVersion"))
        assertEquals("Done (", first.successPattern)
        assertTrue(first.diagnosticsRequired)
    }

    @Test
    fun `entry successPattern overrides platform default`() {
        val spec = SmokePlatformSpec(
            name = "neoforge",
            runTask = ":neoforge:runServer",
            defaultSuccessPattern = "PLATFORM_DEFAULT",
            versionMatrix = listOf(
                SmokeMatrixEntry(id = "nf", versions = listOf("1.21"), successPattern = "Done ("),
            ),
        )
        assertEquals("Done (", spec.toSmokeCases(defaultTarget = "mc12110").single().successPattern)
    }
}
