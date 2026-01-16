# Logger

MagicUtils logger builds on Adventure components and provides a consistent
API for console and chat output, with optional localisation and placeholders.

## Setup

Use the platform adapter where available:

```java
Platform platform = new BukkitPlatformProvider(plugin);
ConfigManager configManager = new ConfigManager(platform);
Logger logger = new Logger(platform, plugin, configManager);
```

Fabric uses the same `Logger` class from `magicutils-logger-fabric`:

```java
Platform platform = new FabricPlatformProvider(server);
ConfigManager configManager = new ConfigManager(platform);
Logger logger = new Logger(platform, configManager, "MyMod");
```

For custom platforms (or NeoForge), use `LoggerCore` directly.

## Basic usage

```java
logger.info("<green>Ready.</green>");
logger.warn("Slow query detected");
logger.error("Database unavailable");
```

The logger accepts MiniMessage markup and can route to console, chat, or both.

## Prefixed loggers

```java
PrefixedLogger db = logger.withPrefix("database", "[DB]");
db.info("Connected");
```

Each prefixed logger has its own entry under `sub-loggers` in `logger.yml`
for enable/disable.

## Log builder

```java
logger.info()
        .toConsole()
        .noPrefix()
        .send("<yellow>Reloaded</yellow>");
```

Use `target(LogTarget.CHAT/CONSOLE/BOTH)` and `to(audience)` for fine control.

## Prefix modes

Prefix rendering can be controlled via `PrefixMode`:

- `FULL` -> full plugin/mod name
- `SHORT` -> short name from config
- `CUSTOM` -> `LoggerCore.setCustomPrefix(...)`
- `NONE` -> no prefix

## Logger configuration

`LoggerConfig` is stored as `logger.{ext}`. On Fabric it is placed under
`config/<modid>/` by default (via `ConfigNamespaceProvider`).

See [Logger Config](logger-config.md) for a full key reference and examples.

Key sections:

- `prefix`: format and prefix mode.
- `defaults`: max length, console/chat defaults.
- `chat` / `console`: colors, gradients, separators.
- `help`: help command styling.
- `sub-loggers`: per-prefix toggles.

## Localisation and placeholders

Attach a `LanguageManager` to enable auto-localisation:

```java
logger.setLanguageManager(languageManager);
```

When a `LanguageManager` is attached, any message that starts with `@` is treated
as a language key:

```java
logger.info("@myplugin.ready");
```

The key is resolved via `LanguageManager` and then rendered as MiniMessage.

Logger messages also pass through MagicPlaceholders. Use `{key}` to resolve
placeholders from the logger namespace (based on the logger name), or
`{namespace:key}` for explicit namespaces.

On Bukkit, the logger automatically bridges MagicPlaceholders to
[PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) when it is
installed. On Fabric, MagicPlaceholders are bridged to
[Text Placeholder API](https://modrinth.com/mod/placeholder-api) and
[MiniPlaceholders](https://modrinth.com/mod/miniplaceholders) when those mods
are present.
