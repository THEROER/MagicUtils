package dev.ua.theroer.magicutils.build.target

import org.gradle.api.Project
import net.fabricmc.loom.api.LoomGradleExtensionAPI

/**
 * Loom-dependent half of the target conventions.
 *
 * Split out of [MagicUtilsTargetConventions][dev.ua.theroer.magicutils.build.target]
 * because it imports Loom's API: everything in the root build-logic artifact must stay
 * resolvable for consumers that never touch Fabric.
 */

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
