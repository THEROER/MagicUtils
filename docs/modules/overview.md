# Module overview

MagicUtils is split into focused artifacts so you can depend on only what you
need. The platform adapters wire the core modules to Bukkit/Paper, Fabric, or
NeoForge.

## Core modules

| Module | Artifact | Notes |
| --- | --- | --- |
| Platform API | `magicutils-api` | `Platform`, `Audience`, and shared interfaces. |
| Core | `magicutils-core` | Config + lang + commands + logger + placeholders. |
| HTTP client | `magicutils-http-client` | HttpClient wrapper with JSON + retries. |
| Config | `magicutils-config` | JSON/JSONC config engine, migrations, comments. |
| Config YAML | `magicutils-config-yaml` | Adds YAML support via Jackson. |
| Config TOML | `magicutils-config-toml` | Adds TOML support via Jackson. |
| Lang | `magicutils-lang` | Language manager and message helpers. |
| Commands | `magicutils-commands` | Annotation/builder command framework. |
| Logger | `magicutils-logger` | Adventure-based logger core. |
| Placeholders | `magicutils-placeholders` | Placeholder registry. |
| Processor | `magicutils-processor` | Annotation processor (internal tooling). |

## Platform adapters

| Platform | Artifact | Notes |
| --- | --- | --- |
| Bukkit/Paper | `magicutils-bukkit` | Includes core modules + Bukkit integrations. |
| Fabric | `magicutils-fabric` | Platform API adapter for Fabric. |
| Velocity | `magicutils-velocity` | Minimal platform adapter for Velocity. |
| NeoForge | `magicutils-neoforge` | Platform API adapter for NeoForge. |

## Fabric integrations

| Module | Artifact | Notes |
| --- | --- | --- |
| Logger (Fabric) | `magicutils-logger-fabric` | Fabric logger adapter. |
| Commands (Fabric) | `magicutils-commands-fabric` | Brigadier integration. |
| Placeholders (Fabric) | `magicutils-placeholders-fabric` | Fabric placeholder bridge. |
| Fabric bundle | `magicutils-fabric-bundle` | Jar-in-jar distribution for servers. |

Use the per-module pages for API details and examples.
