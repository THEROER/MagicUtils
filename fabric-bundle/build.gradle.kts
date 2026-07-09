import dev.ua.theroer.magicutils.build.target.*
import dev.ua.theroer.magicutils.build.support.magicUtilsPrepareServerRunDir
import dev.ua.theroer.magicutils.build.support.magicUtilsRunJavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

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

// `runServer` (the compatibility smoke) runs the mod from THIS project's dev
// source set: the @Mod entrypoint + fabric.mod.json live here, so Loom loads the
// bundle mod from the compiled dev classes and resolves its declared depends
// (fabricloader + fabric-api, both on the mod-aware `modImplementation` above)
// as part of the same dev run. The MagicUtils runtime modules are `compileOnly`
// here (they ship jar-in-jar in the published bundle), so they are absent at dev
// runtime and the entrypoint would hit NoClassDefFoundError: FabricBootstrap.
//
// Put those modules on the run classpath — but by *kind*, so Loom's mod
// resolution stays correct (an earlier attempt to feed a whole fat `:dev` jar on
// `runtimeOnly` made Loom force-load that jar as the *only* root mod, dropping
// fabric-api/loader from the run — HARD_DEP_NO_CANDIDATE):
//  - Fabric-remapped modules (platform-fabric, commands/logger/placeholders-
//    fabric) carry intermediary refs, so they go on `modLocalRuntime`, which
//    Loom remaps into the named dev namespace.
//  - Platform-neutral modules (core, commands, logger, diagnostics, …) are plain
//    named jars — `runtimeOnly` is enough, no remap needed.
// This mirrors how the bundle plugin itself splits remapped vs plain modules.
//
// CRITICAL: these are added ONLY when runServer/runClient is the invoked task.
// The `modLocalRuntime` entries make Loom eagerly read each project jar's
// metadata during configuration; if added unconditionally that breaks EVERY
// build/publish/config on a target whose fabric jars aren't built yet
// ("Failed to read metadata ... NoSuchFileException"). The gate keeps normal
// builds/releases clean — the same guard the original wiring used.
val fabricRemappedRuntime = listOf(
    ":platform-fabric",
    ":commands-fabric",
    ":logger-fabric",
    ":placeholders-fabric",
)
val plainRuntime = listOf(
    ":platform-api",
    ":core",
    ":commands",
    ":commands-brigadier",
    ":logger",
    ":placeholders",
    ":diagnostics",
    ":http-client",
    ":config",
    ":config-yaml",
    ":config-toml",
    ":lang",
)
val wantsRun = gradle.startParameter.taskNames.any { it.endsWith("runServer") || it.endsWith("runClient") }
if (wantsRun) {
    val devModRuntimeConfig = if (isDeobfuscated) "localRuntime" else "modLocalRuntime"
    dependencies {
        fabricRemappedRuntime.forEach { add(devModRuntimeConfig, project(it)) }
        plainRuntime.forEach { runtimeOnly(project(it)) }
    }
}

// `./gradlew :fabric-bundle:runServer` — Loom downloads and starts a Fabric
// server with this bundle (from the dev source set) as the mod (the compatibility
// smoke launches this). Auto-accept the Minecraft EULA so the unattended smoke
// boots, and bind a free port (or -PrunServerPort=N) so it never collides with a
// real server on 25565. Loom's default run dir is `run/`.
//
// Pin the server JDK to Mojang's requirement for the target's Minecraft (1.20.x
// needs Java 17, not the build JVM): Loom otherwise launches the run on the
// build JDK (25 here) and the 1.20.x server aborts (exit 1) — the same class of
// failure the bukkit runner hit. Loom's runServer is a JavaExec, so javaLauncher
// applies directly.
val fabricRunJavaVersion = magicUtilsRunJavaVersion(magicutilsTarget.minecraft.get())
val fabricServerLauncher = extensions.getByType(JavaToolchainService::class.java).launcherFor {
    languageVersion.set(JavaLanguageVersion.of(fabricRunJavaVersion))
}
tasks.named<JavaExec>("runServer") {
    javaLauncher.set(fabricServerLauncher)
    val runDir = layout.projectDirectory.dir("run")
    doFirst {
        val port = magicUtilsPrepareServerRunDir(runDir.asFile)
        logger.lifecycle("fabric-bundle runServer: Minecraft ${magicutilsTarget.minecraft.get()} (Java $fabricRunJavaVersion) on port $port")
    }
}
