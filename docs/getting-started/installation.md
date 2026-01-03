# Installation

MagicUtils is published as multiple artifacts. Pick only what you need.

## Publish to Maven Local (development)

```bash
./gradlew :platform-bukkit:publishToMavenLocal
./gradlew :fabric-bundle:publishToMavenLocal
./gradlew :platform-neoforge:publishToMavenLocal
```

## Bukkit/Paper

```kotlin
repositories {
    mavenLocal()
    maven("https://theroer.github.io/MagicUtils/maven/")
}

dependencies {
    implementation("dev.ua.theroer:magicutils-bukkit:<version>")
    // Optional format helpers
    implementation("dev.ua.theroer:magicutils-config-yaml:<version>")
    implementation("dev.ua.theroer:magicutils-config-toml:<version>")
}
```

If you prefer a shaded runtime, use the `-all` artifact (example:
`magicutils-bukkit-all`).

## Fabric

You have two options:

### Embed the bundle inside your mod

```kotlin
repositories {
    mavenLocal()
    maven("https://theroer.github.io/MagicUtils/maven/")
}

dependencies {
    modImplementation(include("dev.ua.theroer:magicutils-fabric-bundle:<version>"))
    modCompileOnly("dev.ua.theroer:magicutils-fabric-bundle:<version>:dev")
    modRuntimeOnly("dev.ua.theroer:magicutils-fabric-bundle:<version>:dev")
}
```

### Depend on a shared bundle mod

```kotlin
repositories {
    mavenLocal()
    maven("https://theroer.github.io/MagicUtils/maven/")
}

dependencies {
    modImplementation("dev.ua.theroer:magicutils-fabric-bundle:<version>:dev")
}
```

If you pick the shared bundle, add the `magicutils-fabric-bundle` mod to the
server and do not embed it inside other mods.

## NeoForge

```kotlin
repositories {
    mavenLocal()
    maven("https://theroer.github.io/MagicUtils/maven/")
}

dependencies {
    implementation("dev.ua.theroer:magicutils-neoforge:<version>")
}
```

## Optional format helpers

- `magicutils-config-yaml` enables YAML support.
- `magicutils-config-toml` enables TOML support.

Without them, MagicUtils uses JSON or JSONC (Fabric default).
