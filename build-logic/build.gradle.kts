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
            implementationClass = "MagicUtilsTargetPlugin"
        }
        register("magicutilsMatrixSettings") {
            id = "magicutils.matrix-settings"
            implementationClass = "MagicUtilsMatrixSettingsPlugin"
        }
        register("magicutilsMatrixRoot") {
            id = "magicutils.matrix-root"
            implementationClass = "MagicUtilsMatrixRootPlugin"
        }
        register("magicutilsRepositories") {
            id = "magicutils.repositories"
            implementationClass = "MagicUtilsRepositoriesPlugin"
        }
        register("magicutilsCommon") {
            id = "magicutils.common"
            implementationClass = "MagicUtilsCommonPlugin"
        }
        register("magicutilsJavaLibrary") {
            id = "magicutils.java-library"
            implementationClass = "MagicUtilsJavaLibraryPlugin"
        }
        register("magicutilsAnnotationProcessing") {
            id = "magicutils.annotation-processing"
            implementationClass = "MagicUtilsAnnotationProcessingPlugin"
        }
        register("magicutilsShadow") {
            id = "magicutils.shadow"
            implementationClass = "MagicUtilsShadowPlugin"
        }
        register("magicutilsShadedModule") {
            id = "magicutils.shaded-module"
            implementationClass = "MagicUtilsShadedModulePlugin"
        }
        register("magicutilsPublishing") {
            id = "magicutils.publishing"
            implementationClass = "MagicUtilsPublishingPlugin"
        }
        register("magicutilsFabricModule") {
            id = "magicutils.fabric-module"
            implementationClass = "MagicUtilsFabricModulePlugin"
        }
        register("magicutilsFabricBundle") {
            id = "magicutils.fabric-bundle"
            implementationClass = "MagicUtilsFabricBundlePlugin"
        }
        register("magicutilsBukkitBundle") {
            id = "magicutils.bukkit-bundle"
            implementationClass = "MagicUtilsBukkitBundlePlugin"
        }
        register("magicutilsNeoForgeBundle") {
            id = "magicutils.neoforge-bundle"
            implementationClass = "MagicUtilsNeoForgeBundlePlugin"
        }
    }
}
