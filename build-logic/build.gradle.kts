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

gradlePlugin {
    plugins {
        register("magicutilsTarget") {
            id = "magicutils.target"
            implementationClass = "MagicUtilsTargetPlugin"
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
    }
}
