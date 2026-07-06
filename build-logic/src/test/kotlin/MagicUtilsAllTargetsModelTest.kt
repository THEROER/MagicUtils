import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

import dev.ua.theroer.magicutils.build.matrix.MagicUtilsAllTargetsSpec
import dev.ua.theroer.magicutils.build.matrix.MagicUtilsAllTargetsTaskType
import dev.ua.theroer.magicutils.build.matrix.resolveTargets

class MagicUtilsAllTargetsModelTest {

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

    private fun spec(
        targets: List<String> = emptyList(),
        scenario: String? = null,
        taskType: MagicUtilsAllTargetsTaskType = MagicUtilsAllTargetsTaskType.BUILD,
    ) = MagicUtilsAllTargetsSpec(targets, scenario, taskType)

    @Test
    fun `empty target subset resolves to every declared target`(@TempDir dir: Path) {
        assertEquals(listOf("mc1201", "mc12110", "mc262"), spec().resolveTargets(writeTargets(dir)))
    }

    @Test
    fun `explicit subset preserves requested order and dedupes`(@TempDir dir: Path) {
        val resolved = spec(targets = listOf("mc262", "mc12110", "mc262")).resolveTargets(writeTargets(dir))
        assertEquals(listOf("mc262", "mc12110"), resolved)
    }

    @Test
    fun `unknown target in subset fails fast`(@TempDir dir: Path) {
        val file = writeTargets(dir)
        assertThrows(org.gradle.api.GradleException::class.java) {
            spec(targets = listOf("mc999")).resolveTargets(file)
        }
    }

    @Test
    fun `task type tokens map to gradle tasks`() {
        assertEquals("build", MagicUtilsAllTargetsTaskType.fromToken("build").gradleTask)
        assertEquals("check", MagicUtilsAllTargetsTaskType.fromToken("CHECK").gradleTask)
        assertEquals(
            "publishToMavenLocal",
            MagicUtilsAllTargetsTaskType.fromToken("publish-to-maven-local").gradleTask,
        )
        assertEquals(
            "publishToMavenLocal",
            MagicUtilsAllTargetsTaskType.fromToken("publishToMavenLocal").gradleTask,
        )
    }

    @Test
    fun `unknown task type token fails fast`() {
        assertThrows(org.gradle.api.GradleException::class.java) {
            MagicUtilsAllTargetsTaskType.fromToken("assemble")
        }
    }
}
