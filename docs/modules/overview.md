# Module overview

MagicUtils is split into focused artifacts. You can depend on only what you
need, or use `core` for the full stack.

| Module | Artifact | Notes |
| --- | --- | --- |
| Platform API | `magicutils-api` | Abstractions used by all modules. |
| Core | `magicutils-core` | Config + lang + commands + logger + placeholders. |
| Config | `magicutils-config` | JSON/JSONC config engine. |
| Config YAML | `magicutils-config-yaml` | Adds YAML support via Jackson. |
| Config TOML | `magicutils-config-toml` | Adds TOML support via Jackson. |
| Lang | `magicutils-lang` | Language manager and message helpers. |
| Commands | `magicutils-commands` | Annotation-based command framework. |
| Logger | `magicutils-logger` | Adventure-based logging core. |
| Placeholders | `magicutils-placeholders` | Placeholder registry (PAPI on Bukkit). |
| Bukkit | `magicutils-bukkit` | Bukkit/Paper adapter. |
| Fabric | `magicutils-fabric` | Fabric adapter. |
| NeoForge | `magicutils-neoforge` | NeoForge adapter. |
| Fabric bundle | `magicutils-fabric-bundle` | Jar-in-jar distribution. |

Use the per-module pages for examples and API notes.
