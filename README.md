# MagicUtils

Modular toolkit for Bukkit/Paper, Fabric, and NeoForge. It consolidates config,
lang, commands, logger, placeholders, GUI helpers, and scheduling into a single
ecosystem with platform adapters.

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
    // Optional format helpers
    implementation("dev.ua.theroer:magicutils-config-yaml:<version>")
    implementation("dev.ua.theroer:magicutils-config-toml:<version>")
}
```

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

If you use the shared bundle, place `magicutils-fabric-bundle` in the server
`mods/` folder.

### NeoForge

```kotlin
dependencies {
    implementation("dev.ua.theroer:magicutils-neoforge:<version>")
}
```

## Modules (quick map)

- Core: `magicutils-api`, `magicutils-core`
- Config: `magicutils-config`, `magicutils-config-yaml`, `magicutils-config-toml`
- Lang: `magicutils-lang`
- Commands: `magicutils-commands`
- Logger: `magicutils-logger`
- Placeholders: `magicutils-placeholders`
- Platform adapters: `magicutils-bukkit`, `magicutils-fabric`, `magicutils-neoforge`
- Velocity adapter: `magicutils-velocity`
- Fabric bundle: `magicutils-fabric-bundle`

## Quickstart

See the full setup guide in the docs:
https://theroer.github.io/MagicUtils/getting-started/quickstart/

## Notes

- The GitHub Pages Maven repository does not host `*-all` shaded artifacts.
  Use the thin jars from Maven or build shaded jars locally if needed.
