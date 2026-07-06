package dev.ua.theroer.magicutils.build.target

import org.gradle.api.provider.Property

abstract class MagicUtilsTargetExtension {
    abstract val name: Property<String>
    /** Whether this is the matrix default target (published without a version suffix). */
    abstract val defaultTarget: Property<Boolean>
    abstract val minecraft: Property<String>
    /**
     * Minecraft version for the published library coordinate (`+<mc>` suffix,
     * `mc<major.minor>` classifier, obfuscation boundary). Equals [minecraft]
     * unless the target overrides `library_minecraft` — see [MagicUtilsTargetSpec].
     */
    abstract val libraryMinecraft: Property<String>
    abstract val java: Property<Int>
    abstract val yarn: Property<String>
    abstract val loader: Property<String>
    abstract val fabric_api: Property<String>
    abstract val paper: Property<String>
    abstract val miniplaceholders_api: Property<String>
    abstract val pb4_placeholder_api: Property<String>
    abstract val neoforge: Property<String>
}
