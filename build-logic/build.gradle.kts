import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    kotlin("jvm") version "2.2.0"
    `java-gradle-plugin`
    `maven-publish`
}

// Version and group are independent of the MagicUtils library (which lives in
// the root gradle.properties). These plugins are their own releasable tool.
group = providers.gradleProperty("pluginsGroup").getOrElse("dev.ua.theroer.magicutils.build")
version = providers.gradleProperty("pluginsVersion").getOrElse("0.1.0")

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven { url = uri("https://maven.fabricmc.net/") }
}

publishing {
    // Publish to the same Reposilite Maven repo as the library when -Ppublish_repo
    // is set (CI); always available via mavenLocal for dogfooding/consumers.
    // This build script can't use the magicUtilsPublishRepository helper (that's
    // part of the plugins it builds), so it mirrors the same credential handling:
    // user/password from PUBLISH_USER/PUBLISH_TOKEN, applied only when supplied.
    if (project.hasProperty("publish_repo")) {
        val publishUser = (findProperty("publish_user") as? String)?.takeIf { it.isNotBlank() }
            ?: System.getenv("PUBLISH_USER")?.takeIf { it.isNotBlank() }
        val publishPassword = (findProperty("publish_password") as? String)?.takeIf { it.isNotBlank() }
            ?: System.getenv("PUBLISH_TOKEN")?.takeIf { it.isNotBlank() }
        repositories {
            maven {
                name = "magicutilsPublish"
                url = uri(project.property("publish_repo") as String)
                if (publishUser != null && publishPassword != null) {
                    credentials {
                        username = publishUser
                        password = publishPassword
                    }
                }
            }
        }
    }
}



dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib"))

   implementation("com.gradleup.shadow:shadow-gradle-plugin:${project.property("shadowVersion")}")

    // Classic (remapping) id for obfuscated targets and the new no-remap id
    // for 26.x — both resolve to the same Loom, selected per target at apply().
    implementation("fabric-loom:fabric-loom.gradle.plugin:${project.property("fabricLoomVersion")}")
    implementation("net.fabricmc.fabric-loom:net.fabricmc.fabric-loom.gradle.plugin:${project.property("fabricLoomVersion")}")

    // jpenilla run-* task plugins, applied by the consumer-* plugins only when a
    // consumer opts into `devServer {}`. run-paper carries the Paper+Folia
    // runners; run-velocity/run-waterfall carry the proxy runners. Applied via
    // their plugin *marker* artifacts (same mechanism as Loom above) so
    // `pluginManager.apply(id)` resolves them; their task types are configured
    // by name, with no compile-time API import into build-logic.
    val runPaperVersion = project.property("runPaperVersion")
    val runProxyVersion = project.property("runProxyVersion")
    implementation("xyz.jpenilla.run-paper:xyz.jpenilla.run-paper.gradle.plugin:$runPaperVersion")
    implementation("xyz.jpenilla.run-velocity:xyz.jpenilla.run-velocity.gradle.plugin:$runPaperVersion")
    implementation("xyz.jpenilla.run-waterfall:xyz.jpenilla.run-waterfall.gradle.plugin:$runProxyVersion")

    // ModDevGradle marker, applied by consumer-neoforge via pluginManager.apply(id).
    // Its API is not imported into build-logic; the neoForge extension is reached
    // reflectively so only the marker (not moddev's types) is needed here.
    implementation("net.neoforged.moddev:net.neoforged.moddev.gradle.plugin:${project.property("neoForgeModdevVersion")}")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

gradlePlugin {
    plugins {
        register("magicutilsTarget") {
            id = "magicutils.target"
            implementationClass = "dev.ua.theroer.magicutils.build.matrix.MagicUtilsTargetPlugin"
        }
        register("magicutilsMatrixSettings") {
            id = "magicutils.matrix-settings"
            implementationClass = "dev.ua.theroer.magicutils.build.matrix.MagicUtilsMatrixSettingsPlugin"
        }
        register("magicutilsMatrixRoot") {
            id = "magicutils.matrix-root"
            implementationClass = "dev.ua.theroer.magicutils.build.matrix.MagicUtilsMatrixRootPlugin"
        }
        register("magicutilsRepositories") {
            id = "magicutils.repositories"
            implementationClass = "dev.ua.theroer.magicutils.build.support.MagicUtilsRepositoriesPlugin"
        }
        register("magicutilsCommon") {
            id = "magicutils.common"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsCommonPlugin"
        }
        register("magicutilsJavaLibrary") {
            id = "magicutils.java-library"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsJavaLibraryPlugin"
        }
        register("magicutilsAnnotationProcessing") {
            id = "magicutils.annotation-processing"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsAnnotationProcessingPlugin"
        }
        register("magicutilsShadow") {
            id = "magicutils.shadow"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsShadowPlugin"
        }
        register("magicutilsShadedModule") {
            id = "magicutils.shaded-module"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsShadedModulePlugin"
        }
        register("magicutilsPublishing") {
            id = "magicutils.publishing"
            implementationClass = "dev.ua.theroer.magicutils.build.publish.MagicUtilsPublishingPlugin"
        }
        register("magicutilsFabricModule") {
            id = "magicutils.fabric-module"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsFabricModulePlugin"
        }
        register("magicutilsFabricBundle") {
            id = "magicutils.fabric-bundle"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsFabricBundlePlugin"
        }
        register("magicutilsBukkitBundle") {
            id = "magicutils.bukkit-bundle"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsBukkitBundlePlugin"
        }
        // Consumer-facing plugins for downstream plugins/mods (not MagicUtils'
        // own library modules): hide Loom/obf/classifier/toolchain selection.
        register("magicutilsConsumerCommon") {
            id = "magicutils.consumer-common"
            implementationClass = "dev.ua.theroer.magicutils.build.consumer.MagicUtilsConsumerCommonPlugin"
        }
        register("magicutilsConsumerBukkit") {
            id = "magicutils.consumer-bukkit"
            implementationClass = "dev.ua.theroer.magicutils.build.consumer.MagicUtilsConsumerBukkitPlugin"
        }
        register("magicutilsConsumerFabric") {
            id = "magicutils.consumer-fabric"
            implementationClass = "dev.ua.theroer.magicutils.build.consumer.MagicUtilsConsumerFabricPlugin"
        }
        register("magicutilsConsumerVelocity") {
            id = "magicutils.consumer-velocity"
            implementationClass = "dev.ua.theroer.magicutils.build.consumer.MagicUtilsConsumerVelocityPlugin"
        }
        register("magicutilsConsumerBungee") {
            id = "magicutils.consumer-bungee"
            implementationClass = "dev.ua.theroer.magicutils.build.consumer.MagicUtilsConsumerBungeePlugin"
        }
        register("magicutilsConsumerNeoForge") {
            id = "magicutils.consumer-neoforge"
            implementationClass = "dev.ua.theroer.magicutils.build.consumer.MagicUtilsConsumerNeoForgePlugin"
        }
        register("magicutilsNeoForgeBundle") {
            id = "magicutils.neoforge-bundle"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsNeoForgeBundlePlugin"
        }
    }
}
