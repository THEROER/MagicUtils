# MagicUtils

Modular toolkit for Bukkit/Paper, BungeeCord, Fabric, Velocity, and NeoForge. It provides
shared building blocks for configuration, localisation, commands, logging,
placeholders, HTTP clients, and platform adapters.

Docs: https://theroer.github.io/MagicUtils/
Maven: https://theroer.github.io/MagicUtils/maven/

## Installation

Add the repository:

```kotlin
repositories {
    maven("https://theroer.github.io/MagicUtils/maven/")
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

Shared bundle install:

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

## Modules At A Glance

- Platform API: `magicutils-api`
- Core stack: `magicutils-core`
- Feature modules: `magicutils-config`, `magicutils-lang`,
  `magicutils-logger`, `magicutils-commands`, `magicutils-diagnostics`,
  `magicutils-placeholders`, `magicutils-http-client`
- Config format helpers: `magicutils-config-yaml`,
  `magicutils-config-toml`
- Platform adapters: `magicutils-bukkit`, `magicutils-bungee`,
  `magicutils-fabric`, `magicutils-velocity`, `magicutils-neoforge`
- Platform bundles: `magicutils-bukkit-bundle`,
  `magicutils-fabric-bundle`
- Fabric integrations: `magicutils-commands-fabric`,
  `magicutils-logger-fabric`, `magicutils-placeholders-fabric`
- NeoForge commands: `magicutils-commands-neoforge`
- Internal tooling: `magicutils-processor`

## Quickstart

Bootstrap helpers are the recommended platform entry points:

- Bukkit/Paper: `BukkitBootstrap.forPlugin(this)`
- BungeeCord: `BungeeBootstrap.forPlugin(this, "MyPlugin")`
- Fabric: `FabricBootstrap.forMod("mymod", () -> server)`
- Velocity: `VelocityBootstrap.forPlugin(proxy, this, "MyPlugin", dataDir)`
- NeoForge: manual wiring with `NeoForgePlatformProvider` +
  `CommandRegistry.create(...)`

If your project has a shared `common` module, let the platform layer create the
runtime and pass `MagicRuntime` into the shared services instead of calling the
bootstrap helpers from common code.

`buildRuntime()` returns a managed `MagicRuntime` container with the platform,
config manager, logger, language manager, and optional command registry.
Enable diagnostics with `.enableDiagnostics()` and fetch the service from
`runtime.requireComponent(DiagnosticsService.class)` or the bootstrap runtime
result accessor.

See the full setup guide in the docs:
https://theroer.github.io/MagicUtils/getting-started/quickstart/

## Notes

- GitHub Pages Maven does not host `*-all` shaded artifacts. Use the thin jars
  from Maven or build shaded jars locally when you need them.

## Reflection Automation

MagicUtils includes Gradle tasks to keep reflection usage explicit and
reviewable:

- `./gradlew refreshReflectionAllowlist`
  Regenerates `gradle/reflection-allowlist.txt` from current source.
- `./gradlew verifyReflectionBoundaries`
  Fails when new raw reflection markers appear outside the recorded allowlist.

`verifyReflectionBoundaries` is wired into `check`, so CI catches unexpected
new reflection usage automatically.
