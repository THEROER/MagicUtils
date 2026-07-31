// Fabric half of build-logic: the plugins that need Fabric Loom.
//
// Loom is `implementation`, not `compileOnly`, because MagicUtilsFabricModulePlugin and
// MagicUtilsConsumerFabricPlugin apply it with `pluginManager.apply(target.loomPluginId)`
// — that resolves through this artifact's runtime classpath. Only projects that ask for a
// Fabric plugin id pull this artifact, so no NeoForge or proxy consumer ever resolves Loom.
dependencies {
    // Classic (remapping) id for obfuscated targets and the no-remap id for 26.x — both
    // marker coordinates resolve to the same net.fabricmc:fabric-loom artifact, which
    // declares both plugin ids; the module plugin picks one per target at apply time.
    implementation("fabric-loom:fabric-loom.gradle.plugin:${project.property("fabricLoomVersion")}")
    implementation("net.fabricmc.fabric-loom:net.fabricmc.fabric-loom.gradle.plugin:${project.property("fabricLoomVersion")}")

    // The Fabric bundle configures ShadowJar directly.
    implementation("com.gradleup.shadow:shadow-gradle-plugin:${project.property("shadowVersion")}")
}

gradlePlugin {
    plugins {
        register("magicutilsFabricModule") {
            id = "magicutils.fabric-module"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsFabricModulePlugin"
        }
        register("magicutilsFabricBundle") {
            id = "magicutils.fabric-bundle"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsFabricBundlePlugin"
        }
        register("magicutilsConsumerFabric") {
            id = "magicutils.consumer-fabric"
            implementationClass = "dev.ua.theroer.magicutils.build.consumer.MagicUtilsConsumerFabricPlugin"
        }
    }
}
