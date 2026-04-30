import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    kotlin("jvm") version "2.2.0"
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven { url = uri("https://maven.fabricmc.net/") }
}



dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib"))

   implementation("com.gradleup.shadow:shadow-gradle-plugin:${project.property("shadowVersion")}")

    implementation("fabric-loom:fabric-loom.gradle.plugin:${project.property("fabricLoomVersion")}")
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
