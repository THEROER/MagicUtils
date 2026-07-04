import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MagicUtilsMatrixModelTest {

    private fun writeTargets(dir: Path): File {
        val file = dir.resolve("targets.properties").toFile()
        file.writeText(
            """
            target=mc12110

            mc1201.minecraft=1.20.1
            mc1201.java=17

            mc12110.minecraft=1.21.10
            mc12110.java=21

            mc262.minecraft=26.2
            mc262.java=25
            """.trimIndent()
        )
        return file
    }

    private fun definition() = MagicUtilsMatrixDefinition(
        targetsFile = "gradle/targets.properties",
        defaultTarget = "mc12110",
        commonProjects = linkedSetOf(":core"),
        platforms = mapOf(
            "bukkit" to MagicUtilsPlatformSpec("bukkit", linkedSetOf(":platform-bukkit")),
            "fabric" to MagicUtilsPlatformSpec(
                "fabric",
                linkedSetOf(":platform-fabric"),
                disabledTargetPrefixes = setOf("mc26"),
            ),
            "neoforge" to MagicUtilsPlatformSpec(
                "neoforge",
                linkedSetOf(":platform-neoforge"),
                disabledTargetPrefixes = setOf("mc1201"),
            ),
        ),
        scenarios = mapOf(
            "workspace" to MagicUtilsScenarioSpec("workspace", setOf("bukkit", "fabric", "neoforge")),
        ),
    )

    @Test
    fun `loadAllTargetNames returns every declared target sorted`(@TempDir dir: Path) {
        val targets = loadAllTargetNames(writeTargets(dir))
        assertEquals(listOf("mc1201", "mc12110", "mc262"), targets)
    }

    @Test
    fun `availablePlatformsFor honours disabled prefixes`() {
        val def = definition()
        // fabric disabled on mc26; neoforge disabled on mc1201.
        assertEquals(setOf("bukkit", "neoforge"), def.availablePlatformsFor("mc262"))
        assertEquals(setOf("bukkit", "fabric"), def.availablePlatformsFor("mc1201"))
        assertEquals(setOf("bukkit", "fabric", "neoforge"), def.availablePlatformsFor("mc12110"))
    }

    @Test
    fun `publishUnits marks default target without suffix and all-categories`() {
        val units = definition().publishUnits(listOf("mc1201", "mc12110", "mc262"))
        val default = units.single { it.target == "mc12110" }
        assertEquals(listOf("publishDefaultMatrix"), default.publishTasks)
        assertFalse(default.suffix)
    }

    @Test
    fun `publishUnits adds fabric only when platform enabled`() {
        val units = definition().publishUnits(listOf("mc1201", "mc262"))
        val mc1201 = units.single { it.target == "mc1201" }
        val mc262 = units.single { it.target == "mc262" }
        assertTrue("publishFabricMatrix" in mc1201.publishTasks)
        assertFalse("publishFabricMatrix" in mc262.publishTasks) // fabric disabled on mc26
        assertTrue(mc1201.suffix)
        assertTrue(mc262.suffix)
    }

    @Test
    fun `toMatrixJson emits valid boolean suffix and joined tasks`() {
        val json = definition().publishUnits(listOf("mc12110", "mc1201")).toMatrixJson()
        assertTrue(json.startsWith("[") && json.endsWith("]"))
        assertTrue(json.contains(""""target":"mc12110","tasks":"publishDefaultMatrix","suffix":false"""))
        assertTrue(json.contains(""""tasks":"publishCommonMatrix publishFabricMatrix","suffix":true"""))
    }
}
