import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import dev.ua.theroer.magicutils.build.matrix.*

class MagicUtilsTaskSelectionTest {

    private fun definition() = MagicUtilsMatrixDefinition(
        targetsFile = "gradle/targets.properties",
        defaultTarget = "mc12110",
        commonProjects = linkedSetOf(":core"),
        platforms = mapOf(
            "bukkit" to MagicUtilsPlatformSpec("bukkit", linkedSetOf(":bukkit")),
            "velocity" to MagicUtilsPlatformSpec("velocity", linkedSetOf(":velocity")),
            "fabric" to MagicUtilsPlatformSpec("fabric", linkedSetOf(":fabric")),
        ),
        scenarios = mapOf(
            "workspace" to MagicUtilsScenarioSpec("workspace", setOf("bukkit", "velocity", "fabric")),
            "velocity" to MagicUtilsScenarioSpec("velocity", setOf("velocity")),
        ),
    )

    @Test
    fun `subproject-qualified build scopes to that platform, not the whole workspace`() {
        val selection = inferTaskSelection(listOf(":velocity:build"), definition())
        assertEquals(setOf("velocity"), selection.platforms)
        assertNull(selection.scenarioName)
    }

    @Test
    fun `subproject-qualified jar also scopes to its platform`() {
        val selection = inferTaskSelection(listOf(":fabric:jar"), definition())
        assertEquals(setOf("fabric"), selection.platforms)
    }

    @Test
    fun `path-less build still means the whole workspace`() {
        val selection = inferTaskSelection(listOf("build"), definition())
        assertTrue(selection.preferAllAvailable)
    }

    @Test
    fun `run task infers its platform`() {
        val selection = inferTaskSelection(listOf("runVelocity"), definition())
        assertEquals(setOf("velocity"), selection.platforms)
    }

    @Test
    fun `subproject task on a common module scopes to nothing, not the workspace`() {
        val selection = inferTaskSelection(listOf(":core:build"), definition())
        assertEquals(emptySet<String>(), selection.platforms)
        assertNull(selection.scenarioName)
    }

    @Test
    fun `two subproject builds union their platforms`() {
        val selection = inferTaskSelection(listOf(":velocity:build", ":fabric:build"), definition())
        assertEquals(setOf("velocity", "fabric"), selection.platforms)
    }
}
