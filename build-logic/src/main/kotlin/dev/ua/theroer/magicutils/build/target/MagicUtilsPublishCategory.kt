package dev.ua.theroer.magicutils.build.target

import dev.ua.theroer.magicutils.build.module.*

import org.gradle.api.Project
import org.gradle.api.provider.Property

enum class MagicUtilsPublishCategory {
    /**
     * Module is published once on the default target only (typically the
     * latest supported Minecraft version). Used for modules that don't
     * carry MC-version-specific bytecode.
     */
    DEFAULT_ONLY,

    /**
     * Module is published once per "common matrix" target (e.g. mc1201,
     * mc2611, plus the default). All non-Fabric publishable modules use
     * this category.
     */
    COMMON_MATRIX,

    /**
     * Module is published only on Fabric-supported targets (currently
     * mc1201; later targets such as mc2611 disable the Fabric platform).
     */
    FABRIC_MATRIX,

    /**
     * Module participates in the build but is intentionally not
     * published. Used for internal/test modules.
     */
    NONE,
}

abstract class MagicUtilsPublishExtension {
    abstract val category: Property<MagicUtilsPublishCategory>
}

internal const val MAGICUTILS_PUBLISH_EXTENSION_NAME: String = "magicutilsPublish"

/**
 * Returns the configured publish category for [project], or [DEFAULT_ONLY]
 * when no category was set explicitly. The extension is registered by
 * [MagicUtilsCommonPlugin] which is applied transitively by every
 * publishable plugin.
 */
internal fun Project.magicUtilsPublishCategory(): MagicUtilsPublishCategory {
    val extension = extensions.findByType(MagicUtilsPublishExtension::class.java)
        ?: return MagicUtilsPublishCategory.DEFAULT_ONLY
    return extension.category.getOrElse(MagicUtilsPublishCategory.DEFAULT_ONLY)
}
