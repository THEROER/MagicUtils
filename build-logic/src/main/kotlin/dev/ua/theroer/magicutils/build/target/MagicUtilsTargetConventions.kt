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
 * Published MagicUtils version for [baseVersion] on this target:
 * `<base>+java<N>` (e.g. `1.25.0+java21`).
 *
 * Empirically MagicUtils' compiled bytecode depends only on the Java level, not
 * on the Minecraft version: the whole library (core/config/commands/lang, the
 * platform modules, even the Fabric mod's classes) is byte-identical between two
 * targets that share a Java level (e.g. +1.21.10 vs +1.21.11 differed in 0
 * classes) and differs wholesale only across Java levels (major 65 vs 69). So a
 * per-Minecraft coordinate published five near-duplicate copies where three real
 * variants exist — one per Java level (17 / 21 / 25). obf/deobf is not a second
 * axis: it is a function of the Java level (26.x is Java 25 + deobfuscated).
 *
 * Consumers pass the bare base version (`magicutils_version=1.25.0`); the
 * consumer plugins add `+java<N>` from the resolved target's Java level, so the
 * coordinate a consumer resolves always matches one that was published. Fabric
 * mods pin their runtime Minecraft through `fabric.mod.json` (a version range),
 * not through the Maven coordinate.
 */
fun MagicUtilsTargetExtension.publishedVersion(baseVersion: String): String =
    "$baseVersion+java${java.get()}"

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
