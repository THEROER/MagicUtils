package dev.ua.theroer.magicutils.build.target

import org.gradle.api.Project
import net.fabricmc.loom.api.LoomGradleExtensionAPI

/**
 * Single source of truth for the target-derived Fabric/publishing conventions.
 *
 * The obfuscation boundary (Minecraft 26 became deobfuscated), the published
 * `mc<major.minor>` classifier, and the Loom flavour selection are the same
 * facts everywhere they're needed — the MagicUtils library's own fabric
 * modules/bundle AND every consumer plugin. They live here so no build script
 * (ours or a consumer's) re-derives them by hand.
 */

/**
 * Minecraft 26+ ships a deobfuscated jar: Loom does not remap, there are no
 * mappings, and `jar` is the artifact. Older targets are obfuscated and use the
 * classic remapping Loom path. Keyed on the *library* Minecraft (the published
 * coordinate's branch), which is what determines how MagicUtils itself was
 * built — not the runtime Minecraft, which may differ.
 */
val MagicUtilsTargetExtension.isDeobfuscated: Boolean
    get() = libraryMinecraft.get().substringBefore('.').toInt() >= 26

/**
 * Published artifact classifier for the target, e.g. `mc1.21`, `mc26`. Derived
 * from the *library* Minecraft, so a target whose runtime Minecraft differs
 * (e.g. Paper 1.20.6 on the `+1.20.1` library) still resolves the right jar.
 */
val MagicUtilsTargetExtension.mcClassifier: String
    get() = "mc${libraryMinecraft.get().substringBeforeLast('.')}"

/**
 * Published MagicUtils version for [baseVersion] on this target, Fabric-style:
 * `<base>+<library-minecraft>` (e.g. `1.22.0+26.2`). Every target carries the
 * suffix so each Minecraft version is its own Maven version — its own module
 * metadata, Java level and transitive deps — instead of one version whose
 * metadata the targets overwrite. Consumers pass the bare base version
 * (`magicutils_version=1.22.0`); the consumer plugins add the suffix from the
 * resolved target's *library* Minecraft, so no build script writes it by hand.
 * The library Minecraft equals the runtime one unless the target overrides
 * `library_minecraft` (see [MagicUtilsTargetSpec]).
 */
fun MagicUtilsTargetExtension.publishedVersion(baseVersion: String): String =
    "$baseVersion+${libraryMinecraft.get()}"

/** Loom plugin id for the target: no-remap on deobfuscated, remapping otherwise. */
val MagicUtilsTargetExtension.loomPluginId: String
    get() = if (isDeobfuscated) "net.fabricmc.fabric-loom" else "fabric-loom"

/** Gradle dependency configuration for compile-only deps (mod-aware on remap). */
val MagicUtilsTargetExtension.compileOnlyConfiguration: String
    get() = if (isDeobfuscated) "compileOnly" else "modCompileOnly"

/** Gradle dependency configuration for implementation deps (mod-aware on remap). */
val MagicUtilsTargetExtension.implementationConfiguration: String
    get() = if (isDeobfuscated) "implementation" else "modImplementation"

/** Gradle dependency configuration for runtime-only deps (mod-aware on remap). */
val MagicUtilsTargetExtension.runtimeOnlyConfiguration: String
    get() = if (isDeobfuscated) "runtimeOnly" else "modRuntimeOnly"

/** Task producing the primary (published, jar-in-jar) artifact for the target. */
val MagicUtilsTargetExtension.mainJarTaskName: String
    get() = if (isDeobfuscated) "jar" else "remapJar"

/**
 * Adds the Minecraft dependency and official Mojang mappings (obfuscated
 * targets only) to [project] for the resolved [target]. Uses the *runtime*
 * Minecraft ([MagicUtilsTargetExtension.minecraft]) — this is the game Loom
 * compiles/runs against, independent of the published library coordinate. The
 * Fabric loader is intentionally NOT added here — callers pick the configuration
 * themselves (compileOnly for modules, implementation for runnable bundles/mods).
 */
fun applyMinecraftAndMappings(project: Project, target: MagicUtilsTargetExtension) {
    project.dependencies.add("minecraft", "com.mojang:minecraft:${target.minecraft.get()}")
    if (!target.isDeobfuscated) {
        val loom = project.extensions.getByType(LoomGradleExtensionAPI::class.java)
        project.dependencies.add("mappings", loom.officialMojangMappings())
    }
}
