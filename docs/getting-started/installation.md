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

The `magicutils-bukkit` artifact already includes core modules (config, logger,
commands, lang, placeholders).

## Fabric

You have two options:

### Embed the bundle inside your mod (Jar-in-Jar)

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

```kotlin
dependencies {
    modImplementation("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}:dev")
}
```

If you pick the shared bundle, add the `magicutils-fabric-bundle` mod to the
server `mods` folder and do not embed it inside other mods.

You can also add a dependency in your `fabric.mod.json`:

```json
{
  "depends": {
    "magicutils-fabric-bundle": ">=1.10.0"
  }
}
```

## NeoForge

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
