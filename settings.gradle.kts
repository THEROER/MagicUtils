import dev.ua.theroer.magicutils.build.smoke.SmokeGate

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
        "messaging",
        "config-yaml",
        "config-toml",
        "diagnostics",
        "diagnostics-testkit",
        "processor",
    )

    platform("bukkit", listOf("platform-bukkit", "bukkit-bundle"))
    // Bungee is disabled on every 26.x target: bungeecord-api (1.20, pinned in
    // MagicUtilsPlatformApiDefaults) does not track Minecraft and still runs Adventure 4,
    // while 26.2+ platforms moved to Adventure 5 (see magicUtilsAdventureVersion).
    // Building it on a 26.x target would compile the shared modules it inlines against the
    // wrong Adventure; its artifact comes from the 1.21 target and works on proxies
    // regardless of the servers behind them.
    //
    // Velocity follows the Adventure major instead: Velocity 3.x (Adventure 4) on the
    // 1.20/1.21 targets, Velocity 4.x (Adventure 5, Java 25) on 26.2+ — see
    // magicUtilsVelocityApiVersion. Only 26.1 is excluded: it is Java 25 but still
    // Adventure 4, a combination no supported Velocity line has.
    platform("bungee", listOf("platform-bungee", "bungee-bundle"), listOf("mc26"))
    platform("velocity", listOf("platform-velocity", "velocity-bundle"), listOf("mc2611"))
    platform("fabric", listOf("platform-fabric", "commands-fabric", "logger-fabric", "placeholders-fabric", "fabric-bundle"))
    // NeoForge is disabled on both 1.20.x targets (mc1201, mc1205): MagicUtils
    // publishes no neoforge module for the 1.20.x library coordinate.
    platform("neoforge", listOf("platform-neoforge", "commands-neoforge", "neoforge-bundle"), listOf("mc1201", "mc1205"))

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
        gate = SmokeGate.STRICT
        // Bukkit/Paper: one jar per target sweeps its whole Minecraft branch — the
        // jar is pinned with `target=<mcXXXX>` and each smoke value re-launches it
        // on a specific server version via `runMinecraftVersion` (verified-plugin's
        // paperRunVersion). `versions` is the range advertised on Modrinth. The
        // 1.20 branch is split by Java: mc1201 (Java 17) covers 1.20.1-1.20.4,
        // mc1205 (Java 21) covers 1.20.5-1.20.6 — Paper 1.20.5+ needs Java 21.
        platform("bukkit") {
            runTask = ":bukkit-bundle:runServer -Pscenario=bukkit --args=nogui"
            successPattern = "Done ("
            diagnosticsCommand = "magicutils diagnostics export"
            entry("paper-120x") {
                target = "mc1201"
                versions = listOf("1.20-1.20.4")
                smokeValues = listOf("1.20", "1.20.4")
                smokeGradleProperties = mapOf(
                    "1.20" to mapOf("runMinecraftVersion" to "1.20"),
                    "1.20.4" to mapOf("runMinecraftVersion" to "1.20.4"),
                )
            }
            entry("paper-1205x") {
                target = "mc1205"
                versions = listOf("1.20.5-1.20.6")
                smokeValues = listOf("1.20.5", "1.20.6")
                smokeGradleProperties = mapOf(
                    "1.20.5" to mapOf("runMinecraftVersion" to "1.20.5"),
                    "1.20.6" to mapOf("runMinecraftVersion" to "1.20.6"),
                )
            }
            entry("paper-121x") {
                target = "mc12110"
                primary = true
                versions = listOf("1.21-1.21.11")
                smokeValues = listOf("1.21", "1.21.7", "1.21.11")
                smokeGradleProperties = mapOf(
                    "1.21" to mapOf("runMinecraftVersion" to "1.21"),
                    "1.21.7" to mapOf("runMinecraftVersion" to "1.21.7"),
                    "1.21.11" to mapOf("runMinecraftVersion" to "1.21.11"),
                )
            }
            entry("paper-261x") {
                target = "mc2611"
                // Smoke-only: 26.1 is Adventure 4, but the released +java25 bundle is
                // the newest Java-25 target's (26.3, Adventure 5) — see publishUnits.
                released = false
                versions = listOf("26.1-26.1.2")
                smokeValues = listOf("26.1.2")
                smokeGradleProperties = mapOf(
                    "26.1.2" to mapOf("runMinecraftVersion" to "26.1.2"),
                )
            }
            entry("paper-262x") {
                // The released +java25 bundle is the 26.3 build, so gate exactly that
                // jar on a real 26.2 server (same Adventure 5 line, same Java).
                target = "mc263"
                versions = listOf("26.2")
                smokeValues = listOf("26.2")
                smokeGradleProperties = mapOf(
                    "26.2" to mapOf("runMinecraftVersion" to "26.2"),
                )
            }
            entry("paper-263x") {
                target = "mc263"
                versions = listOf("26.3")
                smokeValues = listOf("26.3")
                smokeGradleProperties = mapOf(
                    "26.3" to mapOf("runMinecraftVersion" to "26.3"),
                )
            }
        }
        // Folia: a Paper fork with regionised multithreading. It is NOT a separate
        // published artifact — `folia` is one of the bukkit bundle's Modrinth
        // loaders — but a plugin must be Folia-aware to run on it, so this exercises
        // the SAME bukkit-bundle jar on a real Folia server (via runFolia) to back
        // the `folia` loader claim. Folia needs Java 21+, so only the 1.21+ / 26.x
        // targets. The Modrinth artifact generator skips this platform (no
        // folia-bundle module); it's smoke-only.
        platform("folia") {
            runTask = ":bukkit-bundle:runFolia -Pscenario=bukkit --args=nogui"
            successPattern = "Done ("
            diagnosticsCommand = "magicutils diagnostics export"
            // Folia does NOT publish a build for every patch — it releases under a
            // subset (e.g. 1.21.11 but not 1.21.10; 26.1.2 but not 26.1.1). So the
            // smokeValue is a Folia-available version, launched on the same bundle
            // jar (the jar built for +1.21.10 runs fine on the 1.21.11 Folia server).
            entry("folia-121x") {
                target = "mc12110"
                versions = listOf("1.21-1.21.11")
                smokeValues = listOf("1.21.11")
                smokeGradleProperties = mapOf(
                    "1.21.11" to mapOf("runMinecraftVersion" to "1.21.11"),
                )
            }
            entry("folia-261x") {
                target = "mc2611"
                // Smoke-only: 26.1 is Adventure 4, but the released +java25 bundle is
                // the newest Java-25 target's (26.3, Adventure 5) — see publishUnits.
                released = false
                versions = listOf("26.1-26.1.2")
                smokeValues = listOf("26.1.2")
                smokeGradleProperties = mapOf(
                    "26.1.2" to mapOf("runMinecraftVersion" to "26.1.2"),
                )
            }
            // Folia now ships 26.2 and 26.3 builds. Each runs the jar of its own
            // target, like Paper: 26.2+ is Adventure 5, which the 26.1 jar is not.
            entry("folia-262x") {
                // The released +java25 bundle is the 26.3 build, so gate exactly that
                // jar on a real 26.2 server (same Adventure 5 line, same Java).
                target = "mc263"
                versions = listOf("26.2")
                smokeValues = listOf("26.2")
                smokeGradleProperties = mapOf(
                    "26.2" to mapOf("runMinecraftVersion" to "26.2"),
                )
            }
            entry("folia-263x") {
                target = "mc263"
                versions = listOf("26.3")
                smokeValues = listOf("26.3")
                smokeGradleProperties = mapOf(
                    "26.3" to mapOf("runMinecraftVersion" to "26.3"),
                )
            }
        }
        // Fabric: Loom pins the runtime Minecraft to the build target
        // (minecraft/yarn/loader from targets.properties) — one jar cannot be
        // re-launched on an arbitrary MC version, so each entry is one target, its
        // smokeValue is that target's MC, and `versions` advertises the branch.
        platform("fabric") {
            runTask = ":fabric-bundle:runServer"
            successPattern = "Done ("
            diagnosticsCommand = "magicutils diagnostics export"
            entry("fabric-120x") {
                target = "mc1201"
                versions = listOf("1.20.1-1.20.4")
                smokeValues = listOf("1.20.1")
            }
            entry("fabric-1205x") {
                target = "mc1205"
                versions = listOf("1.20.5-1.20.6")
                smokeValues = listOf("1.20.6")
            }
            entry("fabric-121x") {
                target = "mc12110"
                primary = true
                versions = listOf("1.21-1.21.11")
                smokeValues = listOf("1.21.10")
            }
            entry("fabric-262x") {
                target = "mc262"
                versions = listOf("26.1-26.1.2", "26.2")
                smokeValues = listOf("26.2")
            }
            entry("fabric-263x") {
                target = "mc263"
                versions = listOf("26.3")
                smokeValues = listOf("26.3")
            }
        }
        // NeoForge: like Fabric, runtime MC is pinned to the target. Disabled on
        // the 1.20.x targets (no neoforge module for the 1.20.x library coordinate).
        platform("neoforge") {
            runTask = ":neoforge-bundle:runServer"
            successPattern = "Done ("
            diagnosticsCommand = "magicutils diagnostics export"
            timeoutSeconds = 600
            entry("neoforge-121x") {
                target = "mc12110"
                primary = true
                versions = listOf("1.21-1.21.11")
                smokeValues = listOf("1.21.10")
            }
            entry("neoforge-262x") {
                target = "mc262"
                versions = listOf("26.1-26.1.2", "26.2")
                smokeValues = listOf("26.2")
            }
            entry("neoforge-263x") {
                target = "mc263"
                versions = listOf("26.3")
                smokeValues = listOf("26.3")
            }
        }
        // Proxies (velocity, bungee): MC-version-agnostic — one jar covers the
        // whole span, so a single sub-range advertises everything. 26.x ships a
        // separate jar (different MagicUtils build), so gate that one too.
        platform("velocity") {
            runTask = ":velocity-bundle:runVelocity -Pscenario=velocity"
            successPattern = "Done ("
            diagnosticsCommand = "magicutils diagnostics export"
            // Two proxy lines, split by Adventure major (see the platform declaration
            // above). Each jar is MC-agnostic within its line, so `versions` only says
            // which networks it is meant for; the Modrinth artifacts fold by Java
            // level, so these become two files: velocity-bundle+java21 (Velocity 3)
            // and velocity-bundle+java25 (Velocity 4).
            //
            // Velocity 3.x (Adventure 4, Java 21): built on the 1.21 target, smoked on
            // 3.5.1 — the last 3.x release (deprecated upstream since 2026-09-30).
            entry("velocity-3x") {
                target = "mc12110"
                primary = true
                versions = listOf("1.20-1.20.6", "1.21-1.21.11")
                smokeValues = listOf("3.5.1")
                smokeGradleProperties = mapOf(
                    "3.5.1" to mapOf("runVelocityVersion" to "3.5.1"),
                )
            }
            // Velocity 4.x (Adventure 5, Java 25): built on the newest target and
            // smoked on 4.3.1, the current supported Velocity release. It fronts
            // every 26.x server generation.
            entry("velocity-4x") {
                target = "mc263"
                versions = listOf("26.1-26.1.2", "26.2", "26.3")
                smokeValues = listOf("4.3.1")
                smokeGradleProperties = mapOf(
                    "4.3.1" to mapOf("runVelocityVersion" to "4.3.1"),
                )
            }
        }
        // BungeeCord: like Velocity, the API is decoupled from Minecraft, so one
        // Waterfall proxy build covers the whole span. Waterfall does NOT print
        // `Done (` — its startup-complete marker is `Listening on /host:port`.
        platform("bungee") {
            runTask = ":bungee-bundle:runWaterfall -Pscenario=bungee"
            successPattern = "Listening on"
            diagnosticsCommand = "magicutils diagnostics export"
            // One 1.21-built artifact fronting every server generation, so 26.x is a
            // version range on this entry rather than a separate target that would
            // demand a bungee-bundle+java25 jar. Unlike Velocity there is no Adventure-5
            // BungeeCord API to split onto, and Waterfall itself is end-of-life, so the
            // advertised span is left where it was.
            entry("bungee-runtime") {
                target = "mc12110"
                primary = true
                versions = listOf("1.20-1.20.6", "1.21-1.21.11", "26.1-26.1.2", "26.2")
                smokeValues = listOf("1.21.10")
            }
        }
    }

    // Modrinth release. `./gradlew publishToModrinth -Pversion=X.Y.Z` (token from
    // MODRINTH_TOKEN) uploads one version per smoke sub-range. No `artifact(...)`
    // list is needed: when omitted, the artifacts are synthesised from the smoke
    // matrix above (the same rows printReleaseMatrix emits) — platform → loaders
    // (paper/spigot/bukkit/folia, fabric, neoforge, velocity), target → jar name,
    // and `versions` → the expanded Modrinth game_versions. Keeps the published
    // release and the smoke gate in lockstep. Declare explicit `artifact(...)`
    // entries only to override this.
    modrinth {
        projectId = "oXPn7Ug1"
        channel = "alpha"
        featured = false
    }
}
