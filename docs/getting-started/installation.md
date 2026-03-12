# Installation

MagicUtils is split into modules. For most projects you only need one platform
entry point (`magicutils-bukkit`, `magicutils-fabric-bundle`,
`magicutils-velocity`, or `magicutils-neoforge`) plus optional format helpers.

Use the modular artifacts only when you need a custom wiring path.

## Choose A Version

This documentation is versioned. The examples below use
`{{ magicutils_version }}` which matches the current docs version.

If you want to hardcode it in your build, replace it with the exact number
(for example `1.10.0`).

## Repositories

Add the GitHub Pages Maven repository.

```kotlin
repositories {
    maven("https://theroer.github.io/MagicUtils/maven/")
}
```

## Which Artifact Do I Need?

| Scenario | Recommended artifacts | Notes |
| --- | --- | --- |
| Bukkit/Paper plugin | `magicutils-bukkit` | Default choice for most plugins. |
| Shared Bukkit server install | `magicutils-bukkit-bundle` + plugin `compileOnly` dependency | Use when multiple plugins should share one install. |
| Fabric mod | `magicutils-fabric-bundle` | Default choice for most mods. |
| Shared Fabric server install | `magicutils-fabric-bundle:dev` without `include(...)` | Install the bundle mod on the server. |
| Modular Fabric setup | `magicutils-fabric` + Fabric integration modules | Use only when you want custom wiring. |
| Velocity plugin | `magicutils-velocity` | Includes config/logger/lang/commands support. |
| NeoForge mod | `magicutils-neoforge` + `magicutils-commands-neoforge` | Placeholder bridge is not available yet. |
| Extra config formats | `magicutils-config-yaml`, `magicutils-config-toml` | Optional helpers for YAML and TOML. |
| HTTP client | `magicutils-http-client` | Optional runtime-aware HTTP/WebSocket clients. |

## Bukkit/Paper

The Bukkit/Paper adapter bundles the core modules (config, logger, commands,
lang, placeholders). Add optional format helpers only when you need them.

You can use it in two ways:

1. Embed MagicUtils into your plugin.
2. Use a shared MagicUtils plugin (`magicutils-bukkit-bundle`) so multiple
   plugins reuse the same runtime.

Kotlin DSL:

```kotlin
dependencies {
    implementation("dev.ua.theroer:magicutils-bukkit:{{ magicutils_version }}")
    implementation("dev.ua.theroer:magicutils-config-yaml:{{ magicutils_version }}")
    implementation("dev.ua.theroer:magicutils-config-toml:{{ magicutils_version }}")
}
```

Groovy DSL:

```groovy
dependencies {
    implementation 'dev.ua.theroer:magicutils-bukkit:{{ magicutils_version }}'
    implementation 'dev.ua.theroer:magicutils-config-yaml:{{ magicutils_version }}'
    implementation 'dev.ua.theroer:magicutils-config-toml:{{ magicutils_version }}'
}
```

### Shared Bukkit Bundle

If you want a single shared MagicUtils install for multiple plugins, use the
bundle plugin and do not embed MagicUtils inside your plugins.

Dependencies (compile-only):

```kotlin
dependencies {
    compileOnly("dev.ua.theroer:magicutils-bukkit-bundle:{{ magicutils_version }}")
}
```

Install the bundle on the server:

- Drop `magicutils-bukkit-bundle-{{ magicutils_version }}.jar` into `plugins/`.
- Add `depend: [MagicUtils]` (or `softdepend`) to your `plugin.yml`.

## Fabric

You have two options:

### Embed The Bundle Inside Your Mod

This is the recommended approach for single-mod setups and avoids requiring
server owners to install MagicUtils separately.

Kotlin DSL:

```kotlin
dependencies {
    modImplementation(include("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}"))
    modCompileOnly("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}:dev")
    modRuntimeOnly("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}:dev")
}
```

Groovy DSL:

```groovy
dependencies {
    modImplementation(include('dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}'))
    modCompileOnly 'dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}:dev'
    modRuntimeOnly 'dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}:dev'
}
```

### Depend On A Shared Bundle Mod

If you want one shared MagicUtils install for multiple mods, use a standard
dependency and install the bundle mod on the server.

```kotlin
dependencies {
    modImplementation("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}:dev")
}
```

If you pick the shared bundle, add the `magicutils-fabric-bundle` mod to the
server `mods/` folder and do not embed it inside other mods.

You can download the bundle from the Maven repository:
[`magicutils-fabric-bundle-{{ magicutils_version }}.jar`](https://theroer.github.io/MagicUtils/maven/dev/ua/theroer/magicutils-fabric-bundle/{{ magicutils_version }}/magicutils-fabric-bundle-{{ magicutils_version }}.jar)

You can also add a dependency in your `fabric.mod.json`:

```json
{
  "depends": {
    "magicutils-fabric-bundle": ">=1.10.0"
  }
}
```

### Modular Fabric Dependencies

Use this only when you want to wire specific modules yourself instead of using
the bundle. `FabricBootstrap` lives in the command integration layer, so a
bootstrap-first modular setup usually pulls both the platform adapter and the
Fabric integration modules.

```kotlin
dependencies {
    modImplementation("dev.ua.theroer:magicutils-fabric:{{ magicutils_version }}:dev")
    modImplementation("dev.ua.theroer:magicutils-logger-fabric:{{ magicutils_version }}:dev")
    modImplementation("dev.ua.theroer:magicutils-commands-fabric:{{ magicutils_version }}:dev")
    modImplementation("dev.ua.theroer:magicutils-placeholders-fabric:{{ magicutils_version }}:dev")
    modImplementation("dev.ua.theroer:magicutils-config:{{ magicutils_version }}")
    modImplementation("dev.ua.theroer:magicutils-lang:{{ magicutils_version }}")
}
```

## NeoForge

NeoForge exposes platform, config, logger, and Brigadier command integrations.
There is no NeoForge placeholder bridge yet.

Kotlin DSL:

```kotlin
dependencies {
    implementation("dev.ua.theroer:magicutils-neoforge:{{ magicutils_version }}")
    implementation("dev.ua.theroer:magicutils-commands-neoforge:{{ magicutils_version }}")
}
```

Groovy DSL:

```groovy
dependencies {
    implementation 'dev.ua.theroer:magicutils-neoforge:{{ magicutils_version }}'
    implementation 'dev.ua.theroer:magicutils-commands-neoforge:{{ magicutils_version }}'
}
```

## Velocity

The Velocity adapter exposes platform, config, logger, lang, and command
integration in a single artifact.

```kotlin
dependencies {
    implementation("dev.ua.theroer:magicutils-velocity:{{ magicutils_version }}")
}
```

## Optional Format Helpers

- `magicutils-config-yaml` enables YAML support.
- `magicutils-config-toml` enables TOML support.

Without them, MagicUtils uses JSON or JSONC (Fabric default).

## Optional HTTP Client

```kotlin
dependencies {
    implementation("dev.ua.theroer:magicutils-http-client:{{ magicutils_version }}")
}
```

## Notes On Shaded Artifacts

Local builds produce `*-all` artifacts (shaded). The GitHub Pages repository
does not include these files due to the 100 MB limit, so do not depend on them.
