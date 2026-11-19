# MagicUtils

Modular toolkit for Bukkit/Paper, BungeeCord, Fabric, Velocity, and NeoForge. It provides
shared building blocks for configuration, localisation, commands, logging,
placeholders, HTTP clients, and platform adapters.

Docs: https://magicutils.theroer.dev/
Maven: https://maven.theroer.dev/releases/

## Installation

Add the repository:

```kotlin
repositories {
    maven("https://maven.theroer.dev/releases/")
}
```

### Bukkit/Paper

```kotlin
dependencies {
    implementation("dev.ua.theroer:magicutils-bukkit:<version>")
    implementation("dev.ua.theroer:magicutils-config-yaml:<version>")
    implementation("dev.ua.theroer:magicutils-config-toml:<version>")
}
```

For a shared server install, use `magicutils-bukkit-bundle` as a standalone
plugin and depend on it from your plugin via `depend: [MagicUtils]` or
`softdepend`.

### Fabric

Embed the bundle (recommended):

```kotlin
dependencies {
    modImplementation(include("dev.ua.theroer:magicutils-fabric-bundle:<version>"))
    modCompileOnly("dev.ua.theroer:magicutils-fabric-bundle:<version>:dev")
    modRuntimeOnly("dev.ua.theroer:magicutils-fabric-bundle:<version>:dev")
}
```

Standalone install: `magicutils-fabric-bundle` is also a runnable Fabric mod on
its own — drop the jar into `mods/` and it boots the shared MagicUtils runtime
and registers the `/magicutils` (alias `/mu`) command with diagnostics
sub-commands, no host mod required. Depend on it from your mod with:

```kotlin
dependencies {
    modImplementation("dev.ua.theroer:magicutils-fabric-bundle:<version>:dev")
}
```

For a modular setup, combine `magicutils-fabric` with the Fabric integration
modules you need (`magicutils-commands-fabric`, `magicutils-logger-fabric`,
`magicutils-placeholders-fabric`).

### NeoForge

```kotlin
dependencies {
    implementation("dev.ua.theroer:magicutils-neoforge:<version>")
    implementation("dev.ua.theroer:magicutils-commands-neoforge:<version>")
}
```

### Velocity

```kotlin
dependencies {
    implementation("dev.ua.theroer:magicutils-velocity:<version>")
}
```

### BungeeCord

```kotlin
dependencies {
    implementation("dev.ua.theroer:magicutils-bungee:<version>")
}
```

## Supported Versions

Every row below is booted on a real server by the compatibility smoke
(`./gradlew runCompatibilitySmoke`) and gated on the runtime diagnostics verdict. The one
exception is NeoForge: its moddev dev run loads the mod on a different class loader than
Minecraft (see the note in `neoforge-bundle/build.gradle.kts`), so the dev smoke cannot
finish the diagnostics export; the published bundle jar itself runs cleanly on a real
NeoForge server (checked by hand on 26.3.0.113).

| Platform | Minecraft / proxy versions | Java | Build target |
| --- | --- | --- | --- |
| Paper / Spigot / Purpur | 1.20 – 1.20.4 | 17 | `mc1201` |
| Paper / Spigot / Purpur | 1.20.5 – 1.20.6 | 21 | `mc1205` |
| Paper / Spigot / Purpur / Folia | 1.21 – 1.21.11 | 21 | `mc12110` |
| Paper / Folia | 26.1 – 26.1.2 | 25 | `mc2611` |
| Paper / Folia | 26.2 | 25 | `mc262` |
| Paper / Folia | 26.3 | 25 | `mc263` |
| Fabric | 1.20.1, 1.20.6, 1.21.10, 26.2, 26.3 (one jar per branch) | 17 / 21 / 25 | `mc1201`, `mc1205`, `mc12110`, `mc262`, `mc263` |
| NeoForge | 1.21.10, 26.2, 26.3 | 21 / 25 | `mc12110`, `mc262`, `mc263` |
| Velocity 3.x (≤ 3.5.1, Adventure 4) | proxy for 1.20 – 1.21.x networks | 21 | `mc12110` |
| Velocity 4.x (4.3.1, Adventure 5) | proxy for 26.x networks | 25 | `mc262`, `mc263` |
| BungeeCord / Waterfall | proxy (Waterfall is end-of-life upstream) | 21 | `mc12110` |

Released bundles are keyed by Java level (`+java17`, `+java21`, `+java25`), and each
level ships the build of its newest target. The `+java25` bundles are therefore the
26.3 build (Adventure 5) and serve 26.2 – 26.3; Paper/Folia 26.1, which is still on
Adventure 4, is smoke-tested from the `mc2611` target but needs a build of that target
(`-Ptarget=mc2611`) rather than the released `+java25` jar.

Velocity 4 moved to Adventure 5 and Java 25, so a Velocity 3 build cannot run on it (and
vice versa); the Velocity API a build compiles against follows the target's Adventure
major (see below). Pick `velocity-bundle+java21` for Velocity 3 and
`velocity-bundle+java25` for Velocity 4.

## Adventure

MagicUtils exposes Adventure types in its public API (`Audience.send(Component)`, the
logger and message APIs), so the Adventure major matters to consumers. Each build tracks
the Adventure version its target platforms actually run:

| Target | Adventure | Why |
| --- | --- | --- |
| 1.20.x – 1.21.x | 4.x | Paper 1.21 and adventure-platform-fabric 6.x are on Adventure 4 |
| 26.1 | 4.x | deobfuscated already, but paper-api 26.1.1 still imports adventure-bom 4.26.1 |
| 26.2+ | 5.2.0 | paper-api 26.2/26.3 and velocity-api 4.x import adventure-bom 5.2.0; adventure-platform-fabric 7.x bundles Adventure 5 |

Only the major is pinned per target; within a major the platform's own requirement wins
(paper-api 1.21.11 pulls Adventure 4.26.1, so that is what a 1.21 build compiles against).

Two Adventure majors cannot coexist on Fabric's shared class loader — `ComponentFlattener`
stopped extending `Buildable` in 5.x (so `toBuilder()` changed descriptor) and `Services`
gained `service(ServiceLoader, Class)`. A mod carrying Adventure 4 next to a server on
Adventure 5 crashes in `AdventureCommon.<clinit>` with `NoSuchMethodError` and takes every
mod on that runtime down with it. Aligning per target is what prevents that, so **code
built against a 26.2+ MagicUtils artifact compiles against Adventure 5**; the package stays
`net.kyori`, but Adventure's own 4→5 changes apply.

The Fabric and NeoForge bundles inline Adventure rather than requiring
adventure-platform-fabric. They deliberately do **not** ship `com.google.gson` — Minecraft
provides it, and a second copy breaks version detection.

## Modules At A Glance

- Platform API: `magicutils-api`
- Core stack: `magicutils-core`
- Feature modules: `magicutils-config`, `magicutils-lang`,
  `magicutils-logger`, `magicutils-commands`, `magicutils-commands-brigadier`,
  `magicutils-diagnostics`, `magicutils-diagnostics-testkit`,
  `magicutils-placeholders`, `magicutils-http-client`, `magicutils-messaging`
- Config format helpers: `magicutils-config-yaml`,
  `magicutils-config-toml`
- Platform adapters: `magicutils-bukkit`, `magicutils-bungee`,
  `magicutils-fabric`, `magicutils-velocity`, `magicutils-neoforge`
- Platform bundles: `magicutils-bukkit-bundle`, `magicutils-bungee-bundle`,
  `magicutils-velocity-bundle`, `magicutils-fabric-bundle`,
  `magicutils-neoforge-bundle`
- Fabric integrations: `magicutils-commands-fabric`,
  `magicutils-logger-fabric`, `magicutils-placeholders-fabric`
- NeoForge commands: `magicutils-commands-neoforge`
- Internal tooling: `magicutils-processor`

## Quickstart

Bootstrap helpers are the recommended platform entry points. The name and data
directory are derived from the plugin/mod metadata, so the entry points are
consistent across platforms:

- Bukkit/Paper: `BukkitBootstrap.forPlugin(this)`
- BungeeCord: `BungeeBootstrap.forPlugin(this)`
- Velocity: `VelocityBootstrap.forPlugin(proxy, this)`
- Fabric: `FabricBootstrap.forMod("mymod", () -> server)`
- NeoForge: `NeoForgeBootstrap.forMod("mymod", () -> server)`

Every builder exposes `.withRecommendedDefaults()` (the default, every language
and messages feature on) and `.minimal()` (opt out of the automatic language and
messages wiring when you manage localization yourself), so you configure intent
with one call instead of a wall of boolean toggles.

If your project has a shared `common` module, let the platform layer create the
runtime and pass `MagicRuntime` into the shared services instead of calling the
bootstrap helpers from common code.

`buildRuntime()` returns a managed `MagicRuntime` container with the platform,
config manager, logger, language manager, and optional command registry. On
every platform, `bootstrap.logger()` returns a typed `Logger` facade with
`info/warn/error/...` methods, player-typed overloads, and a fluent
`Logger#log()` builder. Enable diagnostics with `.enableDiagnostics()` and fetch
the service from `runtime.requireComponent(DiagnosticsService.class)` or the
bootstrap runtime result accessor.

See the full setup guide in the docs:
https://magicutils.theroer.dev/getting-started/quickstart/

## Notes

- Artifacts are published to the self-hosted Reposilite at
  `https://maven.theroer.dev/releases`. Both the thin jars and the `*-all`
  shaded jars (where produced) are available there.

## Reflection Automation

MagicUtils includes Gradle tasks to keep reflection usage explicit and
reviewable:

- `./gradlew refreshReflectionAllowlist`
  Regenerates `gradle/reflection-allowlist.txt` from current source.
- `./gradlew verifyReflectionBoundaries`
  Fails when new raw reflection markers appear outside the recorded allowlist.

`verifyReflectionBoundaries` is wired into `check`, so CI catches unexpected
new reflection usage automatically.

## Release Helper

Maintainers cut a tagged release with the Gradle release tasks (the former
`scripts/publish_release.py` is deprecated):

```bash
./gradlew releasePreflight -Pversion=X.Y.Z   # validate version + tags, no changes
./gradlew release -Pversion=X.Y.Z            # full local release (tag not pushed by default)
```

`release` runs locally: it validates the version and build, bumps
`gradle.properties`, tags `vX.Y.Z`, and publishes the Maven artifacts, Modrinth
versions, and Javadoc. `dispatchRelease` is the opt-in CI path via
`release.yml`. See `RELEASING.md` for the full flow.
