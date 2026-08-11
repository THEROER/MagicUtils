// NeoForge half of build-logic: the plugins that need ModDevGradle.
//
// `implementation` for the same reason as Loom in :fabric — MagicUtilsConsumerNeoForge
// Plugin applies moddev via `pluginManager.apply(id)`, which reads this artifact's
// runtime classpath. It also compiles against ModDev's own API (NeoForgeExtension),
// which the marker brings along. Only projects asking for a NeoForge plugin id
// resolve this artifact, so no Fabric or proxy consumer ever sees ModDevGradle.
dependencies {
    implementation("net.neoforged.moddev:net.neoforged.moddev.gradle.plugin:${project.property("neoForgeModdevVersion")}")
}

gradlePlugin {
    plugins {
        register("magicutilsNeoForgeBundle") {
            id = "magicutils.neoforge-bundle"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsNeoForgeBundlePlugin"
        }
        register("magicutilsConsumerNeoForge") {
            id = "magicutils.consumer-neoforge"
            implementationClass = "dev.ua.theroer.magicutils.build.consumer.MagicUtilsConsumerNeoForgePlugin"
        }
    }
}
