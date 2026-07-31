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

// Per-platform plugin artifacts. The root project carries everything that is
// platform-neutral; each subproject adds only the plugins that need one platform's
// Gradle toolchain, and declares that toolchain itself.
//
// This is what keeps a consumer's resolution honest: requesting
// `magicutils.consumer-neoforge` pulls magicutils-build-logic-neoforge (ModDevGradle)
// and never Fabric Loom, which pins a far newer Gradle than a NeoForge mod on 1.21.1
// builds with. Applying a platform plugin by id needs it on the *runtime* classpath of
// the plugin that applies it, so the split has to be by artifact, not by configuration.
include("fabric")
include("neoforge")
include("jvm")
