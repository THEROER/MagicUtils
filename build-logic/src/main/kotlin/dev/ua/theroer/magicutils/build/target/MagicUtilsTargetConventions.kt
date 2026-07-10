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
 * Bundle artifact ids end in `-bundle` (`magicutils-fabric-bundle`,
 * `magicutils-bukkit-bundle`, ...). Only the five bundle plugins produce them,
 * so this suffix is the reliable discriminator — on both the publish side (the
 * project's artifact id) and the consumer side (the requested module name) —
 * between a bundle and a plain library module, without either side importing the
 * publish-category extension.
 */
fun magicUtilsModuleIsBundle(moduleName: String): Boolean = moduleName.endsWith("-bundle")

/**
 * Published MagicUtils coordinate version for [moduleName] built at [javaLevel].
 *
 * Two kinds of module, two coordinate shapes:
 *  - **Bundles** (`*-bundle`) are fat jars whose shaded dependency set genuinely
 *    differs per Java level / Minecraft branch (the 1.20.x, 1.21.x and 26.x
 *    bundles are not byte-identical), so they keep the `<base>+java<N>` coordinate
 *    — one real variant per Java level.
 *  - **Plain library modules** (core/config/commands/lang, the platform and
 *    fabric modules) are byte-identical across Java levels once the class-file
 *    version word is normalized: the per-level diffs are pure javac codegen
 *    artifacts, not behaviour. Publishing three near-duplicate `+java17/21/25`
 *    copies was redundant, so they now publish once under the **bare** base
 *    version. A `+java17`-compiled class loads fine on any JRE >= 17, so a
 *    consumer on Java 21/25 resolving the bare coordinate runs it unchanged.
 *
 * Consumers pass the bare base version (`magicutils_version=1.27.1`); this is the
 * single place that decides whether the resolved coordinate carries `+java<N>`,
 * so the publish side and every consumer plugin agree by construction.
 */
fun magicUtilsPublishedModuleVersion(moduleName: String, baseVersion: String, javaLevel: Int): String =
    if (magicUtilsModuleIsBundle(moduleName)) javaSuffixedCoordinate(baseVersion, javaLevel) else baseVersion

/**
 * Published MagicUtils version for a [moduleName] on this target. Thin wrapper
 * over [magicUtilsPublishedModuleVersion] that supplies the target's Java level;
 * consumer plugins call this so the module-vs-bundle rule lives in one place.
 */
fun MagicUtilsTargetExtension.publishedVersion(moduleName: String, baseVersion: String): String =
    magicUtilsPublishedModuleVersion(moduleName, baseVersion, java.get())

/**
 * Pure formatter for the `<base>+java<N>` published coordinate. The `+java<N>`
 * suffix format lives here as one function so no caller (the module-version rule,
 * the Modrinth bundle file name, the release smoke URL) re-spells it by hand.
 */
fun javaSuffixedCoordinate(baseVersion: String, javaLevel: Int): String =
    "$baseVersion+java$javaLevel"

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
