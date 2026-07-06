import dev.ua.theroer.magicutils.build.target.*
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("magicutils.neoforge-bundle")
    // The standalone entrypoint (@Mod class) compiles against the NeoForge API,
    // so this module needs ModDevGradle just like commands-neoforge does. The
    // magicutils.neoforge-bundle plugin only wires the jar-in-jar bundle content.
    id("net.neoforged.moddev")
}

magicutilsPublish {
    category = MagicUtilsPublishCategory.COMMON_MATRIX
}

val target = project.extensions.getByType(MagicUtilsTargetExtension::class.java)

neoForge {
    version = target.neoforge.get()
}

// moddev puts the whole NeoForge jar on the runtime classpath; the shadowJar
// task (from magicutils.java-library) would then try to bundle it and blow past
// the 64k-entry zip limit. NeoForge is provided by the loader at runtime, so
// keep it out of the shadow archive — the same exclusion commands-neoforge uses.
val shadowRuntimeClasspath by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations["runtimeClasspath"])
    exclude(group = "net.neoforged")
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shadowRuntimeClasspath)
}

// Expand ${version} in the mod metadata (like the fabric bundle does for
// fabric.mod.json) so the published mod carries the real version.
tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }
}
