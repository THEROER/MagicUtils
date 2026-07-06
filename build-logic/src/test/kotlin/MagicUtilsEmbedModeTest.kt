import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import dev.ua.theroer.magicutils.build.consumer.ConsumerLoader
import dev.ua.theroer.magicutils.build.consumer.EmbedMode
import dev.ua.theroer.magicutils.build.consumer.resolveEmbedMode
import org.gradle.api.GradleException

class MagicUtilsEmbedModeTest {

    @Test
    fun `AUTO resolves to each loader's native technique`() {
        assertEquals(EmbedMode.JAR_IN_JAR, resolveEmbedMode(EmbedMode.AUTO, ConsumerLoader.FABRIC))
        assertEquals(EmbedMode.JAR_IN_JAR, resolveEmbedMode(EmbedMode.AUTO, ConsumerLoader.NEOFORGE))
        assertEquals(EmbedMode.SHADED, resolveEmbedMode(EmbedMode.AUTO, ConsumerLoader.BUKKIT))
    }

    @Test
    fun `explicit supported modes pass through unchanged`() {
        assertEquals(EmbedMode.JAR_IN_JAR, resolveEmbedMode(EmbedMode.JAR_IN_JAR, ConsumerLoader.FABRIC))
        assertEquals(EmbedMode.EXTERNAL, resolveEmbedMode(EmbedMode.EXTERNAL, ConsumerLoader.FABRIC))
        assertEquals(EmbedMode.SHADED, resolveEmbedMode(EmbedMode.SHADED, ConsumerLoader.BUKKIT))
        assertEquals(EmbedMode.EXTERNAL, resolveEmbedMode(EmbedMode.EXTERNAL, ConsumerLoader.BUKKIT))
    }

    @Test
    fun `EXTERNAL is valid on every loader`() {
        ConsumerLoader.values().forEach { loader ->
            assertEquals(EmbedMode.EXTERNAL, resolveEmbedMode(EmbedMode.EXTERNAL, loader))
        }
    }

    @Test
    fun `JAR_IN_JAR is rejected on Bukkit with a helpful message`() {
        val error = assertThrows(GradleException::class.java) {
            resolveEmbedMode(EmbedMode.JAR_IN_JAR, ConsumerLoader.BUKKIT)
        }
        assertTrue(error.message!!.contains("JAR_IN_JAR"), error.message)
        assertTrue(error.message!!.contains("Bukkit"), error.message)
        // points the consumer at the supported alternatives
        assertTrue(error.message!!.contains("SHADED"), error.message)
        assertTrue(error.message!!.contains("EXTERNAL"), error.message)
    }

    @Test
    fun `SHADED is rejected on Fabric and NeoForge`() {
        listOf(ConsumerLoader.FABRIC, ConsumerLoader.NEOFORGE).forEach { loader ->
            val error = assertThrows(GradleException::class.java) {
                resolveEmbedMode(EmbedMode.SHADED, loader)
            }
            assertTrue(error.message!!.contains("SHADED"), error.message)
            assertTrue(error.message!!.contains("JAR_IN_JAR"), error.message)
        }
    }
}
