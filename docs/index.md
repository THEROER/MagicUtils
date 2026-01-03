# MagicUtils

MagicUtils is a modular toolkit for Bukkit/Paper, Fabric, and NeoForge. It
provides shared building blocks for configuration, localisation, commands,
logging, placeholders, and platform adapters.

## Highlights

- Config manager with JSON/JSONC, YAML, and TOML support.
- Annotation-first command framework with type parsers and tab completion.
- Adventure-based logger with rich formatting and per-module prefixes.
- Language manager with MiniMessage and per-player overrides.
- Optional placeholder registry (including PAPI integration on Bukkit).

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

- Read the installation guide for platform-specific details.
- Jump into the module pages for API examples.
- Use the version selector in the header to switch between releases.
