# Installation

MagicUtils is split into modules. For most projects you only need a single
platform entry point (`magicutils-bukkit`, `magicutils-fabric-bundle`,
`magicutils-neoforge`) plus optional format helpers.

## Choose a version

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

## Bukkit/Paper

The Bukkit/Paper adapter bundles the core modules (config, logger, commands,
lang, placeholders). Add optional format helpers only when you need them.

Kotlin DSL:

```kotlin
dependencies {
    implementation("dev.ua.theroer:magicutils-bukkit:{{ magicutils_version }}")
    // Optional format helpers
    implementation("dev.ua.theroer:magicutils-config-yaml:{{ magicutils_version }}")
    implementation("dev.ua.theroer:magicutils-config-toml:{{ magicutils_version }}")
}
```

Groovy DSL:

```groovy
dependencies {
    implementation 'dev.ua.theroer:magicutils-bukkit:{{ magicutils_version }}'
    // Optional format helpers
    implementation 'dev.ua.theroer:magicutils-config-yaml:{{ magicutils_version }}'
    implementation 'dev.ua.theroer:magicutils-config-toml:{{ magicutils_version }}'
}
```

## Fabric

You have two options:

### Embed the bundle inside your mod (Jar-in-Jar)

This is the recommended approach for single-mod setups and avoids requiring
server owners to install MagicUtils separately.

Kotlin DSL:

```kotlin
dependencies {
    modImplementation(include("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}"))
    // Optional: dev mappings for local run configs
    modCompileOnly("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}:dev")
    modRuntimeOnly("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}:dev")
}
```

Groovy DSL:

```groovy
dependencies {
    modImplementation(include('dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}'))
    // Optional: dev mappings for local run configs
    modCompileOnly 'dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}:dev'
    modRuntimeOnly 'dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}:dev'
}
```

### Depend on a shared bundle mod

If you want one shared MagicUtils install for multiple mods, use a standard
dependency and install the bundle mod on the server.

```kotlin
dependencies {
    modImplementation("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}:dev")
}
```

If you pick the shared bundle, add the `magicutils-fabric-bundle` mod to the
server `mods` folder and do not embed it inside other mods.

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

### Modular Fabric dependencies (advanced)

If you want to wire only specific modules, use the platform adapter plus the
feature modules you need:

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

NeoForge currently exposes platform, config, and logger integrations. Commands
and placeholders are not wired on NeoForge yet.

Kotlin DSL:

```kotlin
dependencies {
    implementation("dev.ua.theroer:magicutils-neoforge:{{ magicutils_version }}")
}
```

Groovy DSL:

```groovy
dependencies {
    implementation 'dev.ua.theroer:magicutils-neoforge:{{ magicutils_version }}'
}
```

## Optional format helpers

- `magicutils-config-yaml` enables YAML support.
- `magicutils-config-toml` enables TOML support.

Without them, MagicUtils uses JSON or JSONC (Fabric default).

## Notes on shaded artifacts

Local builds produce `*-all` artifacts (shaded). The GitHub Pages repository
does not include these files due to the 100 MB limit, so do not depend on them.
