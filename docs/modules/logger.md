# Logger

MagicUtils logger builds on Adventure components and provides a consistent API
for console and chat output, with optional localisation, internal placeholders,
and external placeholder engines.

## Setup

The recommended path is to obtain the logger from a bootstrap result or
`MagicRuntime`.

### Bukkit/Paper

```java
BukkitBootstrap.RuntimeResult magic = BukkitBootstrap.forPlugin(plugin)
        .buildRuntime();

Logger logger = magic.logger();
```

### Fabric

```java
FabricBootstrap.RuntimeResult magic = FabricBootstrap.forMod("mymod", () -> server)
        .buildRuntime();

Logger logger = magic.logger();
```

### Velocity

Velocity bootstrap returns `LoggerCore` directly:

```java
VelocityBootstrap.RuntimeResult magic = VelocityBootstrap.forPlugin(proxy, plugin, "MyPlugin", dataDirectory)
        .buildRuntime();

LoggerCore logger = magic.logger();
```

### NeoForge / Custom Platforms

NeoForge and custom platforms typically wire `LoggerCore` manually:

```java
Platform platform = new NeoForgePlatformProvider();
ConfigManager configManager = new ConfigManager(platform);
LoggerCore logger = new LoggerCore(platform, configManager, this, "MyMod");
```

## Basic Usage

```java
logger.info("<green>Ready.</green>");
logger.warn("Slow query detected");
logger.error("Database unavailable");
```

The logger accepts MiniMessage markup and can target console, chat, or both.

## Log Builder

```java
logger.info()
        .toConsole()
        .noPrefix()
        .send("<yellow>Reloaded</yellow>");
```

Use the builder when you want fine-grained control:

- `target(LogTarget.CHAT | CONSOLE | BOTH)`
- `to(audience)`
- `toConsole()`
- `noPrefix()`

## Prefixed Loggers

```java
PrefixedLogger db = logger.withPrefix("database", "[DB]");
db.info("Connected");
```

Each prefixed logger gets its own entry under `sub-loggers` in `logger.{ext}`
for enable or disable toggles.

## Prefix Modes

Prefix rendering is controlled by `PrefixMode`:

- `FULL` -> full plugin/mod name
- `SHORT` -> short name from config
- `CUSTOM` -> `setCustomPrefix(...)`
- `NONE` -> no prefix

`Logger` delegates these controls to the underlying `LoggerCore`.

## Runtime Integration

When you already have `MagicRuntime`, the logger is a shared typed component:

```java
MagicRuntime runtime = magic.runtime();
LoggerCore loggerCore = runtime.requireComponent(LoggerCore.class);
```

That makes it easy to pass the logger into reloadable services or register
named runtime resources that log through the same config and placeholder setup.

## Logger Configuration

`LoggerConfig` is stored as `logger.{ext}`.

- Bukkit: plugin config directory
- Fabric: `config/<modid>/` by default
- Velocity / custom platforms: resolved through the active `Platform`

See [Logger Config](logger-config.md) for a full key reference.

Key sections:

- `prefix`
- `defaults`
- `chat` / `console`
- `help`
- `sub-loggers`

## Localisation

Attach a `LanguageManager` to enable `@key` lookups:

```java
logger.setLanguageManager(languageManager);
logger.info("@myplugin.ready");
```

The logger resolves the key through the attached `LanguageManager` and then
renders the result as MiniMessage.

Bootstrap helpers bind the language manager automatically when language support
is enabled.

## Internal And External Placeholders

Logger messages pass through both:

1. `MagicPlaceholders` for `{key}` and `{namespace:key}` tokens.
2. Platform-specific external placeholder engines.

Examples:

```java
logger.info("Balance: {economy:balance}");
logger.info("Hello {player}");
```

Platform integrations:

- Bukkit logger installs the PlaceholderAPI bridge and a Bukkit external
  placeholder engine.
- Fabric logger installs Text Placeholder API / MiniPlaceholders / PB4 support
  when those mods are present.
- `LoggerCore` also supports a custom `ExternalPlaceholderEngine` for custom
  platforms.
