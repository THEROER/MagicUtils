# MagicUtils

MagicUtils is a modular toolkit for Bukkit/Paper, Fabric, and NeoForge. It
provides shared building blocks for configuration, localisation, commands,
logging, placeholders, and platform adapters.

## Highlights

- Config manager with JSON/JSONC, YAML, and TOML support plus migrations.
- Annotation-first command framework with type parsers, options, and tab completion.
- Adventure-based logger with rich formatting, sub-loggers, and help styling.
- Language manager with MiniMessage, custom messages, and per-player overrides.
- Placeholder registry with Bukkit PlaceholderAPI bridge.

## Modules at a glance

| Layer | Artifacts | Notes |
| --- | --- | --- |
| Platform API | `magicutils-api` | `Platform`, `Audience`, and shared interfaces. |
| Core stack | `magicutils-core` | Logger + commands + config + lang + placeholders. |
| Feature modules | `magicutils-logger`, `magicutils-commands`, `magicutils-config`, `magicutils-lang`, `magicutils-placeholders` | Mix and match. |
| Format helpers | `magicutils-config-yaml`, `magicutils-config-toml` | Enable extra config formats. |
| Platform adapters | `magicutils-bukkit`, `magicutils-fabric`, `magicutils-neoforge` | Wire MagicUtils to a runtime. |
| Fabric extras | `magicutils-commands-fabric`, `magicutils-logger-fabric`, `magicutils-placeholders-fabric`, `magicutils-fabric-bundle` | Brigadier integration and jar-in-jar bundle. |

## Quick start

1. Add the GitHub Pages Maven repository.
2. Add the dependency for your platform.
3. Initialise the modules you need.

```kotlin
repositories {
    maven("https://theroer.github.io/MagicUtils/maven/")
}
```

=== "Bukkit/Paper"
    ```kotlin
    dependencies {
        implementation("dev.ua.theroer:magicutils-bukkit:{{ magicutils_version }}")
        // Optional format helpers
        implementation("dev.ua.theroer:magicutils-config-yaml:{{ magicutils_version }}")
        implementation("dev.ua.theroer:magicutils-config-toml:{{ magicutils_version }}")
    }
    ```

=== "Fabric"
    ```kotlin
    dependencies {
        modImplementation(include("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}"))
        modCompileOnly("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}:dev")
        modRuntimeOnly("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}:dev")
    }
    ```

=== "NeoForge"
    ```kotlin
    dependencies {
        implementation("dev.ua.theroer:magicutils-neoforge:{{ magicutils_version }}")
    }
    ```

## Where to go next

- Read the installation guide for platform-specific details and bundle options.
- Jump into the module pages for deeper API examples and configs.
- Use the version selector in the header to switch between releases.
