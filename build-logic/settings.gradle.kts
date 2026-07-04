pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Auto-provision the build JDK (see MAGICUTILS_BUILD_JDK) for contributors
// and CI runners that don't already have it installed.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "magicutils-build-logic"
