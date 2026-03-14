# Module Overview

MagicUtils is split into focused artifacts so you can depend on only what you
need. The platform adapters wire the shared modules to Bukkit/Paper, Fabric,
Velocity, or NeoForge.

Bootstrap helpers are available on Bukkit, Fabric, and Velocity. NeoForge
currently uses manual wiring with the platform adapter plus command module.

## Core Modules

| Module | Artifact | Notes |
| --- | --- | --- |
| Platform API | `magicutils-api` | `Platform`, `Audience`, `TaskScheduler`, player lifecycle/message events, and shared interfaces. |
| Core | `magicutils-core` | Shared runtime container (`MagicRuntime`), reflective access helpers, plus core config/lang/logger/placeholder wiring. |
| HTTP client | `magicutils-http-client` | HTTP and WebSocket client wrappers with JSON, retries, multipart, and runtime profiles. |
| Config | `magicutils-config` | JSON/JSONC config engine, migrations, comments. |
| Config YAML | `magicutils-config-yaml` | Adds YAML support via Jackson. |
| Config TOML | `magicutils-config-toml` | Adds TOML support via Jackson. |
| Lang | `magicutils-lang` | Language manager and message helpers. |
| Commands | `magicutils-commands` | Annotation/builder command framework. |
| Logger | `magicutils-logger` | Adventure-based logger core. |
| Placeholders | `magicutils-placeholders` | Placeholder registry. |
| Processor | `magicutils-processor` | Annotation processor used by internal tooling. |

## Platform Adapters

| Platform | Artifact | Notes |
| --- | --- | --- |
| Bukkit/Paper | `magicutils-bukkit` | Includes the shared stack and exposes `BukkitBootstrap`. |
| Bukkit bundle | `magicutils-bukkit-bundle` | Shared Bukkit/Paper plugin bundle for server installs. |
| Fabric | `magicutils-fabric` | Platform API adapter for Fabric modular setups. |
| Velocity | `magicutils-velocity` | Platform adapter plus config/logger/lang/commands for Velocity. |
| NeoForge | `magicutils-neoforge` | Platform API adapter for NeoForge manual setups. |

## Brigadier Integrations

| Module | Artifact | Notes |
| --- | --- | --- |
| Brigadier base | `magicutils-commands-brigadier` | Shared Brigadier command registry base. |
| Commands (NeoForge) | `magicutils-commands-neoforge` | Brigadier integration for NeoForge. |

## Fabric Integrations

| Module | Artifact | Notes |
| --- | --- | --- |
| Logger (Fabric) | `magicutils-logger-fabric` | Fabric logger adapter. |
| Commands (Fabric) | `magicutils-commands-fabric` | Brigadier integration plus `FabricBootstrap`. |
| Placeholders (Fabric) | `magicutils-placeholders-fabric` | Fabric placeholder bridge. |
| Fabric bundle | `magicutils-fabric-bundle` | Jar-in-jar distribution for shared server installs. |

Use the per-module pages for API details, examples, and configuration options.
