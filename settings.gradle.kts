pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases") }
    }
}

plugins {
    // Auto-provision the build JDK (see MAGICUTILS_BUILD_JDK) when absent.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("magicutils.matrix-settings")
}

rootProject.name = "MagicUtils"

magicMatrix {
    targetsFile = "gradle/targets.properties"
    defaultTarget = "mc12110"

    // Artifact ids are `magicutils-<projectName>` except the platform-* projects.
    moduleNamePrefix("magicutils-")
    moduleName("platform-api", "magicutils-api")
    moduleName("platform-bukkit", "magicutils-bukkit")
    moduleName("platform-bungee", "magicutils-bungee")
    moduleName("platform-velocity", "magicutils-velocity")
    moduleName("platform-fabric", "magicutils-fabric")
    moduleName("platform-neoforge", "magicutils-neoforge")

    commonProjects(
        "platform-api",
        "core",
        "config",
        "lang",
        "logger",
        "commands",
        "commands-brigadier",
        "placeholders",
        "http-client",
        "config-yaml",
        "config-toml",
        "diagnostics",
        "diagnostics-testkit",
        "processor",
    )

    platform("bukkit", listOf("platform-bukkit", "bukkit-bundle"))
    platform("bungee", listOf("platform-bungee"))
    platform("velocity", listOf("platform-velocity"))
    platform("fabric", listOf("platform-fabric", "commands-fabric", "logger-fabric", "placeholders-fabric", "fabric-bundle"))
    platform("neoforge", listOf("platform-neoforge", "commands-neoforge", "neoforge-bundle"), listOf("mc1201"))

    scenario("workspace", listOf("bukkit", "bungee", "velocity", "fabric", "neoforge"), "Full multi-platform workspace")
    scenario("bukkit", listOf("bukkit"), "Bukkit and Paper modules")
    scenario("bungee", listOf("bungee"), "BungeeCord modules")
    scenario("velocity", listOf("velocity"), "Velocity modules")
    scenario("fabric", listOf("fabric"), "Fabric modules")
    scenario("neoforge", listOf("neoforge"), "NeoForge modules")

    // Compatibility smoke: launch the standalone MagicUtils bundle on a real
    // server per MC version and gate on the runtime diagnostics verdict. Both the
    // Bukkit and Fabric bundles are runnable with the `/magicutils` command +
    // diagnostics wired up. Fabric's server prints the same `Done (` line as Paper.
    smoke {
        platform("bukkit") {
            runTask = ":bukkit-bundle:runServer -Pscenario=bukkit --args=nogui"
            successPattern = "Done ("
            entry("paper-121x") {
                versions = listOf("1.21.10")
                smokeValues = listOf("1.21.10")
            }
        }
        platform("fabric") {
            // Loom's runServer downloads/boots a Fabric server with the built
            // bundle as a dev mod; fabric-bundle/build.gradle auto-accepts EULA.
            runTask = ":fabric-bundle:runServer"
            successPattern = "Done ("
            entry("fabric-121x") {
                versions = listOf("1.21.10")
                smokeValues = listOf("1.21.10")
            }
        }
    }

    // Modrinth release (opt-in). Uploads one version per declared artifact via
    // `./gradlew publishToModrinth -Pversion=X.Y.Z` (token from MODRINTH_TOKEN).
    // Left commented out here because MagicUtils itself has no Modrinth project;
    // this is the reference DSL consumers copy. Enable by setting a real
    // projectId and pointing `file` at the built bundle jar for the target.
    // modrinth {
    //     projectId = "AbCdEf12"
    //     channel = "release"
    //     artifact("bukkit") {
    //         file = "bukkit-bundle/build/libs/magicutils-bukkit-bundle-${version}.jar"
    //         loaders = listOf("bukkit", "paper")
    //         gameVersions = listOf("1.21.10")
    //     }
    //     artifact("fabric") {
    //         file = "fabric-bundle/build/libs/magicutils-fabric-bundle-${version}.jar"
    //         loaders = listOf("fabric")
    //         gameVersions = listOf("1.21.10")
    //     }
    // }
}
