import dev.ua.theroer.magicutils.build.target.*

plugins {
    id("magicutils.fabric-bundle")
}

magicutilsPublish {
    category = MagicUtilsPublishCategory.FABRIC_MATRIX
}

val magicutilsTarget = project.extensions.getByType(MagicUtilsTargetExtension::class.java)

// ---------------------------------------------------------------------------
// Standalone Fabric mod entrypoint.
//
// The `magicutils.fabric-bundle` plugin wires the MagicUtils modules only into
// the runtime jar-in-jar (`include` / `bundleShadow`) — those types are NOT on
// the compile classpath. To compile the entrypoint sources below we add the
// MagicUtils modules + fabric-api on the compile classpath here.
//
// On obfuscated targets (< 26) Loom uses `modImplementation`; on deobfuscated
// (26.x) targets it uses plain `implementation`. The bundle plugin already adds
// `minecraft` and `fabric-loader`, so we only need fabric-api + project deps.
// ---------------------------------------------------------------------------
val isDeobfuscated = magicutilsTarget.minecraft.get().split(".")[0].toInt() >= 26
val modImpl = if (isDeobfuscated) "implementation" else "modImplementation"

// Fabric API version comes from the resolved target (gradle/targets.properties
// `mcXXXX.fabric_api`). MagicUtils' own fabric modules don't depend on it, but
// the standalone bundle mod does (ModInitializer/lifecycle/command callbacks).
// Override for an unlisted target with -Pfabric_api_version=...
val fabricApiVersion = (findProperty("fabric_api_version") as String?)
    ?: magicutilsTarget.fabric_api.orNull
    ?: throw GradleException(
        "No fabric_api for target '${magicutilsTarget.name.get()}' in " +
            "gradle/targets.properties; add mcXXXX.fabric_api=... or pass -Pfabric_api_version=..."
    )

dependencies {
    // Fabric API: ModInitializer, CommandRegistrationCallback, ServerLifecycleEvents.
    add(modImpl, "net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // MagicUtils types used by the entrypoint. These live in the runtime bundle
    // (jar-in-jar) already, so compileOnly is enough for the entrypoint sources.
    compileOnly(project(":core"))            // MagicRuntime
    compileOnly(project(":commands"))        // MagicCommand, MagicSender, CommandResult, annotations
    compileOnly(project(":commands-brigadier")) // BrigadierCommandRegistry base (commandManager/registerAllCommands)
    compileOnly(project(":commands-fabric"))    // FabricBootstrap, CommandRegistry (Fabric)
    compileOnly(project(":logger"))          // LoggerCore, LogBuilderCore, MessageParser
    compileOnly(project(":logger-fabric"))   // Logger#getCore()
    compileOnly(project(":platform-api"))    // Audience, Platform
    compileOnly(project(":diagnostics"))     // DiagnosticsCommandSupport, DiagnosticsService
}

// `runServer` (the compatibility smoke) runs the mod from the dev source set,
// where the MagicUtils modules are only `compileOnly` — so they are absent at
// dev runtime (NoClassDefFoundError: FabricBootstrap). Rather than re-deriving
// the bundle's per-module variant wiring here, run the actual published bundle
// jar as a local dev mod: it already jar-in-jars every MagicUtils module, so the
// dev server loads exactly what ships. This is dev-only and never published, so
// the bundle/pom stays untouched. On deobfuscated targets (26.x) the no-remap
// Loom plugin has no `modLocalRuntime` — use plain `localRuntime`; obfuscated
// targets keep the remapping `modLocalRuntime`.
// Attach the built bundle jar as a dev mod for runServer only. It must NOT be a
// plain `files(<jar provider>)` dependency: Loom eagerly resolves the mod
// configuration and sha256s the not-yet-built jar during configuration, which
// fails every project-wide configuration (release, preflight) with "Failed to
// compute checksum". Only add it when this project's runServer is the build's
// start task, and gate on the property so normal builds/releases stay clean.
val bundleJarProvider = if (isDeobfuscated) {
    tasks.named<AbstractArchiveTask>("shadowJar").flatMap { it.archiveFile }   // 26.x: no remap
} else {
    tasks.named<AbstractArchiveTask>("remapJar").flatMap { it.archiveFile }    // <26: remapped jar
}
val devRuntimeConfig = if (isDeobfuscated) "localRuntime" else "modLocalRuntime"
val wantsRun = gradle.startParameter.taskNames.any { it.endsWith("runServer") || it.endsWith("runClient") }
if (wantsRun) {
    dependencies {
        add(devRuntimeConfig, files(bundleJarProvider))
    }
}

// `./gradlew :fabric-bundle:runServer` — Loom downloads and starts a Fabric
// server with the built bundle as a dev mod (the compatibility smoke launches
// this). Depend on the bundle jar so it's built first, and auto-accept the
// Minecraft EULA in the run dir so the unattended smoke server boots
// (dev/test only). Loom's default run dir is `run/`.
tasks.named("runServer") {
    dependsOn(bundleJarProvider)
    val runDir = layout.projectDirectory.dir("run")
    doFirst {
        val dir = runDir.asFile
        dir.mkdirs()
        dir.resolve("eula.txt").writeText("eula=true\n")
    }
}
