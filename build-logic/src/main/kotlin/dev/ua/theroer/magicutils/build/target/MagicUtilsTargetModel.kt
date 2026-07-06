package dev.ua.theroer.magicutils.build.target

import org.gradle.api.GradleException
import java.io.File
import java.util.Properties

data class MagicUtilsTargetSpec(
    val name: String,
    val minecraft: String,
    /**
     * Minecraft version used for the *published library coordinate* — the
     * `+<mc>` version suffix, the `mc<major.minor>` classifier, and the
     * obfuscation boundary. Defaults to [minecraft]; set it explicitly (via
     * `<target>.library_minecraft`) only when the runtime Minecraft differs
     * from the MagicUtils artifact's coordinate. Example: a target running
     * Paper 1.20.6 (Java 21) still resolves the `+1.20.1` / `mc1.20` artifacts,
     * because MagicUtils publishes the whole 1.20.x branch under one coordinate.
     */
    val libraryMinecraft: String,
    val java: Int,
    val yarn: String?,
    val loader: String?,
    val fabricApi: String?,
    val paper: String?,
    val miniplaceholdersApi: String?,
    val pb4PlaceholderApi: String?,
    val neoforge: String?,
)

internal fun normalizeTargetName(rawTargetName: String): String =
    rawTargetName.trim().let {
        when {
            it.isEmpty() -> throw GradleException("Target name must not be empty.")
            it.startsWith("mc") -> it
            else -> "mc$it"
        }
    }

internal fun loadTargetProperties(targetsFile: File): Properties {
    if (!targetsFile.isFile) {
        throw GradleException("Missing targets file: ${targetsFile.absolutePath}")
    }

    return Properties().also { properties ->
        targetsFile.inputStream().use(properties::load)
    }
}

/**
 * Every target declared in [targetsFile], in file order. A target is any
 * `mcXXXX` prefix that has a `.minecraft` value (the one required key —
 * see [resolveMagicUtilsTargetSpec]). This is the single source of truth
 * the CI build/publish matrices are generated from, so no target list is
 * ever duplicated in a workflow.
 */
internal fun loadAllTargetNames(targetsFile: File): List<String> {
    val properties = loadTargetProperties(targetsFile)
    return properties.stringPropertyNames()
        .mapNotNull { key -> key.removeSuffix(".minecraft").takeIf { it != key } }
        .distinct()
        // Properties has no defined order; sort for stable, reproducible output.
        .sorted()
}

internal fun resolveMagicUtilsTargetSpec(
    targetsFile: File,
    defaultTarget: String,
    explicitTarget: String?,
): MagicUtilsTargetSpec {
    val properties = loadTargetProperties(targetsFile)
    val fallbackTarget = properties.getProperty("target", defaultTarget)
    val targetName = normalizeTargetName(explicitTarget ?: fallbackTarget)

    fun requireValue(suffix: String): String =
        properties.getProperty("$targetName.$suffix")
            ?: throw GradleException(
                "Missing '$targetName.$suffix' in ${targetsFile.absolutePath}"
            )

    val minecraft = requireValue("minecraft")

    return MagicUtilsTargetSpec(
        name = targetName,
        minecraft = minecraft,
        // Optional: falls back to the runtime Minecraft when the published
        // library coordinate matches it (the common case).
        libraryMinecraft = properties.getProperty("$targetName.library_minecraft") ?: minecraft,
        java = requireValue("java").toInt(),
        yarn = properties.getProperty("$targetName.yarn"),
        loader = properties.getProperty("$targetName.loader"),
        fabricApi = properties.getProperty("$targetName.fabric_api"),
        paper = properties.getProperty("$targetName.paper"),
        miniplaceholdersApi = properties.getProperty("$targetName.miniplaceholders_api"),
        pb4PlaceholderApi = properties.getProperty("$targetName.pb4_placeholder_api"),
        neoforge = properties.getProperty("$targetName.neoforge"),
    )
}

/**
 * Published artifact classifier for a target spec, e.g. `mc1.21`, `mc26`.
 * Derived from the library Minecraft — the same formula as the extension-side
 * [mcClassifier], kept here so matrix/release JSON tasks can resolve it for a
 * target by name without a live extension.
 */
internal fun MagicUtilsTargetSpec.mcClassifier(): String =
    "mc${libraryMinecraft.substringBeforeLast('.')}"
