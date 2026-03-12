# MagicUtils

MagicUtils is a modular toolkit for Bukkit/Paper, Fabric, Velocity, and
NeoForge. It provides shared building blocks for configuration, localisation,
commands, logging, placeholders, HTTP clients, and platform adapters.

The same stack also works well in multi-module projects where platform adapters
only bootstrap `MagicRuntime` and the real feature logic lives in shared
`common` modules.

## Highlights

- Bootstrap-first setup for Bukkit, Fabric, and Velocity via
  `BukkitBootstrap`, `FabricBootstrap`, and `VelocityBootstrap`.
- `MagicRuntime` container for managed shutdown hooks, typed components, and
  named runtime resources.
- Config manager with JSON/JSONC, YAML, and TOML support plus migrations.
- Annotation-first command framework with type parsers, options, and Brigadier
  support where the platform supports it.
- Adventure-based logger with rich formatting, sub-loggers, and help styling.
- Language manager with MiniMessage, bundled messages, and per-player
  overrides.
- HTTP client wrapper with JSON mapping, retries, multipart uploads, and
  runtime-bound profiles.
- Placeholder registry with Bukkit PlaceholderAPI and Fabric placeholder
  bridges.

## Modules At A Glance

| Layer | Artifacts | Notes |
| --- | --- | --- |
| Platform API | `magicutils-api` | `Platform`, `Audience`, `TaskScheduler`, and shared interfaces. |
| Core stack | `magicutils-core` | Shared runtime container plus core config/lang/logger/placeholder wiring. |
| Feature modules | `magicutils-logger`, `magicutils-commands`, `magicutils-config`, `magicutils-lang`, `magicutils-placeholders`, `magicutils-http-client` | Mix and match for manual setups. |
| Format helpers | `magicutils-config-yaml`, `magicutils-config-toml` | Enable extra config formats. |
| Platform adapters | `magicutils-bukkit`, `magicutils-fabric`, `magicutils-velocity`, `magicutils-neoforge` | Wire MagicUtils to each runtime. |
| Platform bundles | `magicutils-bukkit-bundle`, `magicutils-fabric-bundle` | Shared server-side installs for Bukkit/Paper and Fabric. |
| Fabric integrations | `magicutils-commands-fabric`, `magicutils-logger-fabric`, `magicutils-placeholders-fabric` | Fabric-specific command, logger, and placeholder layers. |
| Brigadier integrations | `magicutils-commands-brigadier`, `magicutils-commands-neoforge` | Shared Brigadier base and NeoForge command wiring. |

## Quick Start

1. Add the GitHub Pages Maven repository.
2. Add one platform entry point.
3. Wire the runtime through the recommended bootstrap helper.

```kotlin
repositories {
    maven("https://theroer.github.io/MagicUtils/maven/")
}
```

=== "Bukkit/Paper"
    ```kotlin
    dependencies {
        implementation("dev.ua.theroer:magicutils-bukkit:{{ magicutils_version }}")
    }
    ```

    ```java
    BukkitBootstrap.RuntimeResult magic = BukkitBootstrap.forPlugin(this)
            .enableCommands()
            .buildRuntime();
    ```

=== "Fabric"
    ```kotlin
    dependencies {
        modImplementation(include("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}"))
        modCompileOnly("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}:dev")
        modRuntimeOnly("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}:dev")
    }
    ```

    ```java
    FabricBootstrap.RuntimeResult magic = FabricBootstrap.forMod("mymod", () -> server)
            .enableCommands()
            .buildRuntime();
    ```

=== "Velocity"
    ```kotlin
    dependencies {
        implementation("dev.ua.theroer:magicutils-velocity:{{ magicutils_version }}")
    }
    ```

    ```java
    VelocityBootstrap.RuntimeResult magic = VelocityBootstrap.forPlugin(proxy, this, "MyPlugin", dataDirectory)
            .enableCommands()
            .buildRuntime();
    ```

=== "NeoForge"
    ```kotlin
    dependencies {
        implementation("dev.ua.theroer:magicutils-neoforge:{{ magicutils_version }}")
        implementation("dev.ua.theroer:magicutils-commands-neoforge:{{ magicutils_version }}")
    }
    ```

    ```java
    Platform platform = new NeoForgePlatformProvider();
    ConfigManager configManager = new ConfigManager(platform);
    LoggerCore logger = new LoggerCore(platform, configManager, this, "MyMod");
    CommandRegistry commands = CommandRegistry.create("mymod", "mymod", logger);
    ```

`buildRuntime()` returns a managed `MagicRuntime` wrapper, so you can keep one
runtime handle and close it cleanly on shutdown.

## Where To Go Next

- Read the Core / Common Logic page when your code lives mostly in
  `magicutils-core`.
- Read the installation guide for bundle options and modular setups.
- Jump to Quickstart for end-to-end bootstrap examples per platform.
- Read the Runtime guide for `MagicRuntime` patterns and lifecycle management.
- Use the Migration guide when updating older code samples or plugins.
- Use the module pages for deeper API examples and config details.
- Use the version selector in the header to switch between releases.
