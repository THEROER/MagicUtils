# MagicUtils

Utility toolkit for Bukkit/Paper plugins that consolidates configuration,
localisation, commands, logging, GUI helpers, and scheduling into a single
library. MagicUtils is distributed internally; depend on it via your local
Maven cache.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Module Reference](#module-reference)
   - [config](#config--annotation-driven-configuration)
   - [lang](#lang--localisation--messaging)
   - [commands](#commands--declarative-command-framework)
   - [logger](#logger--rich-console--chat-logging)
   - [gui](#gui--inventory-ui-helpers)
   - [utils](#utils--scheduling-helpers)
   - [platform](#platform--platform-abstractions)
   - [placeholders](#placeholders--custom--papi-integration)
   - [annotations](#annotations--shared-metadata)
3. [Recipes](#recipes)
4. [Troubleshooting](#troubleshooting)
5. [Contributing](#contributing)

---

## Quick Start

1. **Publish to Maven Local** (Bukkit implementation)
   ```bash
   ./gradlew :platform-bukkit:publishToMavenLocal
   ```
2. **Declare the dependency** in your plugin build script:
   ```kts
   repositories {
       mavenLocal()
   }

   dependencies {
       implementation("dev.ua.theroer:magicutils:<version>")
   }
   ```
   The default version is defined in `build.gradle` (currently `1.5.2`).
3. **Refresh after every change** by repeating `publishToMavenLocal` so
   dependent plugins pick up the new artefact.

> **Prerequisites:** Java 21 toolchain across all modules.

> **Modules:** `platform-api` (abstractions), `core` (platform-agnostic code),
> `platform-bukkit` (Paper/Bukkit implementation), `platform-neoforge`
> (minimal NeoForge adapter: config dir, logger, console audience).

---

## Module Reference

### `config` — annotation-driven configuration

**Highlights**
- Map YAML directly to POJOs using `@ConfigFile`, `@ConfigSection`, `@ConfigValue`,
  `@Comment`, `@ConfigReloadable`, and friends.
- Diff-aware saving: only modified values are written back, keeping user edits intact.
- Built-in filesystem watcher can reload files changed on disk and notify listeners.
- Placeholders in file paths (`lang/{lang}.yml`) supported out of the box.
- Plays nicely with Lombok — most built-in configs are a single class with annotations and getters.

**Key types**
- `ConfigManager`
  - `register(Class<T>, Map<String,String>)` – create/load configs with optional path placeholders.
  - `save(instance)` / `reload(instance)` / `mergeFromFile` / `mergeToFile` – explicit control over a single object.
  - `reload(Class<T>, String... sections)` – partial reload if a config is marked with `@ConfigReloadable`.
  - `onChange` – observer pattern for reacting to disk edits.
  - `shutdown()` – stop the watcher thread.
- `ConfigSerializer`, `DefaultValueProvider`, `ListProcessor`, `SaveTo` provide extensibility when simple POJOs are not enough.

**Usage notes**
- Initialise defaults inline (`private int cooldown = 5;`). Reach for `@DefaultValue` only when you need computed defaults or complex providers.
- Prefer immutable collections for complex defaults; the serializer clones values on load, preventing shared state issues.
- Always call `configManager.shutdown()` in your plugin’s `onDisable` to release watcher resources.
- Combine with `lang` by storing language codes in config and feeding them into `LanguageManager` at runtime.
- Example: `examples/config/ConfigExample.java`.

### `lang` — localisation & messaging

**Highlights**
- Language files (`lang/<code>.yml`) are fully managed: auto-created on first run, prefilled with EN/RU/UA defaults, and hot-reloadable.
- Global fallback language plus per-player overrides; missing keys fall back gracefully.
- MiniMessage everywhere, with legacy colour-code compatibility via `Messages.getRaw` and `Logger.parseSmart`.
- Placeholder expansion (map or varargs) is handled automatically across APIs.
- `InternalMessages` bridges library internals and plugin code, guaranteeing that core messages stay translated.

**Key types**
- `LanguageManager`
  - `init(defaultLang)` / `setFallbackLanguage(code)` / `loadLanguage(code)`.
  - `getMessage(...)` overloads for plain strings, `CommandSender`, or specific language codes.
  - `setPlayerLanguage`, `getPlayerLanguage`, `clearPlayerLanguage`, `getPlayerLanguages`.
  - `saveCustomMessages(language, map)` so plugins can persist overrides on the fly.
- `Messages` – static helper used throughout MagicUtils and downstream plugins.
- `LanguageConfig` – annotation-driven representation of every section (`magicutils.commands`, `magicutils.system`, etc.).
- `LanguageDefaults` – seeds `lang/<code>.yml` from in-memory templates.

**Usage notes**
- Store per-player language selections (database, config) and reapply them with `setPlayerLanguage` on login.
- Use `Messages.getRaw(sender, key, placeholders…)` for MiniMessage strings that mix localisation with runtime data.
- To expose translation keys in commands, leverage `LanguageKeyTypeParser` so tab completion is always up-to-date.
- Extend localisation by adding new sections under `messages` — no code changes required.
- Example: `examples/lang/LanguageExample.java`.

### `commands` — declarative command framework

**Highlights**
- Annotation-first design (`@CommandInfo`, `@SubCommand`, `@Permission`, `@Suggest`, `@OptionalArgument`) removes Bukkit command boilerplate.
- `CommandRegistry` & `CommandManager` can dynamically register/unregister commands at runtime (useful for reloading or feature flags).
- Rich parsers for core Bukkit types (players, worlds, offline players) plus domain-specific ones (language keys). Custom parsers plug in via `TypeParserRegistry`.
- `CommandResult` model bubbles up messages and success state; direct integration with `LanguageManager` for consistent feedback.
- `BukkitCommandWrapper` bridges registered commands with Bukkit’s dispatcher and handles permission denial automatically.

**Usage notes**
- Group subcommands by adding multiple `@SubCommand` methods on the same class. Built-in suggestion providers ensure tab completion stays relevant.
- Reuse `CommandContext` to access typed arguments (`context.getOrThrow("player", Player.class)`), command metadata, and raw input.
- Combine with the `lang` module: return `CommandResult.failure(InternalMessages.SETTINGS_LANG_NOT_FOUND.get(sender, "language", arg))` for translated errors.
- Commands can be reloaded by clearing the registry and re-registering classes; useful during development or when configs change.
- Example: `examples/commands/ExampleCommand.java`.
- Built-in `/magicutils reload` and `/magicutils settings` commands exist but are still in progress; expect breaking changes.

### `logger` — rich console & chat logging

**Highlights**
- Adventure `Component` logging with MiniMessage-first formatting, legacy colour fallback, and gradient-aware prefixes.
- Built-in level methods (`info`, `warn`, `error`, `debug`, `success`) generated by `@LogMethods` for ergonomic API usage.
- Sub-logger support (`PrefixedLogger`) for namespaced output (e.g. `database`, `api`, `scheduler`).
- Optional localisation — route everything through `LanguageManager` by flipping a single flag.
- Smart parsing: `Logger.parseSmart()` recognises hybrid strings that mix MiniMessage with legacy codes, making transition painless.
- Placeholder pipeline: custom placeholders are expanded first, then PlaceholderAPI is applied if present (fallbacks gracefully when absent).

**Key knobs in `logger.yml`**
- `chat` / `console` sections: gradients, palettes per log level, auto colour generation toggles.
- `prefix` section: default modes (FULL/SHORT/CUSTOM) and per-target overrides.
- `defaults`: default target (`BOTH`, `CHAT`, `CONSOLE`), `console-strip-formatting`, `console-use-gradient`.
- `sub-loggers`: enable/disable child loggers, customise prefixes and colours.

**Usage notes**
- Initialise once: `Logger.init(this, configManager);` and (optionally) set `Logger.setLanguageManager(languageManager);`.
- Create per-feature loggers:
  ```java
  PrefixedLogger api = Logger.prefixed("api");
  api.info("<gray>Calling</gray> <green>{url}</green>",
           TagResolver.placeholder("url", endpoint));
  ```
- Combine with the command framework for translated feedback: `Logger.warn(sender, InternalMessages.CMD_NO_PERMISSION.get(sender));`
- Flip localisation at runtime via `Logger.setAutoLocalization(true)` to respect per-player languages when broadcasting to chat.
- Register custom placeholders via `PlaceholderProcessor.registerGlobalPlaceholder` / `registerLocalPlaceholder`; see `examples/logger/PlaceholderDemo.java`.
- Example: `examples/logger/LoggerExample.java`.

### `gui` — inventory UI helpers

**Highlights**
- `MagicGui` turns raw Bukkit inventories into structured UIs with titles, pagination, redraw callbacks, and close handlers.
- `MagicItem` couples `ItemStack` metadata with click behaviour (left/right shift clicks, cancel/propagate policies, etc.).
- `MagicGuiListener` captures inventory events and routes them to the owning GUI; register once during plugin startup.
- `SlotPolicy` describes how each slot behaves (STATIC, FILLER, INTERACTIVE, BLOCKED) for easy layout definition.

**Usage notes**
- Wire GUIs to config and localisation: fetch button definitions from `ConfigManager`, text from `Messages`, and compose at runtime.
- Register all GUIs through a central factory so they can be reloaded or refreshed when config/lang changes.
- Handle pagination by updating the GUI’s page state and calling `gui.redraw(player)`.
- Example: `examples/gui/GuiExample.java`.

### `utils` — scheduling helpers

**Highlights**
- `ScheduleUtils.repeat(count, periodTicks, action, onComplete, plugin)` wraps a `BukkitRunnable` into a concise helper with automatic cancellation and error trapping.
- `ScheduleUtils.countdown(seconds, onTick, onComplete)` starts a second-based countdown and returns a cancellable handle (`CountdownTask`).
- Errors inside scheduled actions are caught and logged to prevent silent thread death.

**Usage notes**
- Provide an explicit plugin instance for `repeat`. For `countdown`, MagicUtils grabs the first loaded plugin; if you need determinism, consider overloading or forking the utility.
- The returned `CountdownTask` exposes `cancel()` and `isCancelled()` — handy for aborting timers when players leave.
- Compose with `LanguageManager` to broadcast translated countdown updates:
  ```java
  ScheduleUtils.countdown(5,
          seconds -> player.sendMessage(Messages.get(player, "match.starting", "seconds", String.valueOf(seconds))),
          () -> player.sendMessage(Messages.get(player, "match.begin")));
  ```
- Example: `examples/utils/ScheduleExample.java`.

### `platform` — platform abstractions

**Highlights**
- `Platform`, `PlatformLogger`, and `Audience` interfaces decouple core code from Bukkit-specific APIs.
- `platform-bukkit` supplies a full provider; `platform-neoforge` adds a minimal adapter (config dir, console audience, logging, main-thread execution).
- `ConfigManager` and `LanguageManager` accept either a `Platform` or a legacy plugin instance (auto-resolves to a Bukkit provider when possible).

**Usage notes**
- Use `new BukkitPlatformProvider(plugin)` (or your own `Platform` implementation) when wiring MagicUtils in non-Bukkit contexts.
- For other platforms, implement the `Platform`/`Audience`/`PlatformLogger` trio and pass it into managers.
- Typical setup in a Bukkit plugin:
  ```java
  private ConfigManager configManager;
  private LanguageManager languageManager;

  @Override
  public void onEnable() {
      Platform platform = new BukkitPlatformProvider(this);
      configManager = new ConfigManager(platform);
      languageManager = new LanguageManager(platform, configManager);
      languageManager.init("en");
      Logger.init(this, configManager);
      Messages.setLanguageManager(languageManager);
  }
  ```

### `placeholders` — custom & PAPI integration

**Highlights**
- `PlaceholderProcessor` allows global or plugin-scoped placeholders; resolvers receive the player (if any) and formatting args.
- PlaceholderAPI integration is lazy: if PAPI is present, `PapiEngine` is used; otherwise a `NoopEngine` fallback keeps messages intact.
- Logger messages automatically run through custom placeholders first, then PAPI, ensuring consistent rendering across console/chat.
- `IntegrationManager`/`Integration` helpers wrap third-party checks (e.g., PlaceholderAPI) and report availability safely.

**Usage notes**
- Register resolvers: `registerGlobalPlaceholder("server-online", () -> String.valueOf(Bukkit.getOnlinePlayers().size()))`.
- Pair with your own PAPI expansions (`examples/logger/PapiExpansionExample.java`) or purely custom placeholders (`examples/logger/PlaceholderDemo.java`).


### `annotations` — shared metadata

**Highlights**
- Command annotations (`@CommandInfo`, `@SubCommand`, `@Permission`, `@OptionalArgument`, `@Suggest`, `@ParsePriority`) control routing, permission gates, tab completion order, and argument optionality.
- Configuration annotations (`@Comment`, `@DefaultValue`, `@DefaultValueProvider`, `@SaveTo`, `@ListProcessor`) tailor YAML output, validation, and custom serialisation.
- Logger annotation `@LogMethods` generates level-specific shortcut methods
  (`info`, `warn`, `error`, `debug`, `success`) from a single interface.

**Usage notes**
- Treat annotations as declarative metadata—keep business logic in your classes while MagicUtils handles glue code and reflective wiring.
- Combine `@ParsePriority` with custom parsers to influence resolution order when multiple parsers fit the same type.
- Examples: see `examples/commands/ExampleCommand.java` and `examples/config/ConfigExample.java` for practical annotation usage.

---

## Recipes

### Register, listen, and hot-reload a configuration
```java
import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.platform.bukkit.BukkitPlatformProvider;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class ExamplePlugin extends JavaPlugin {
    private ConfigManager configManager;
    private ExampleConfig exampleConfig;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(new BukkitPlatformProvider(this));
        exampleConfig = configManager.register(ExampleConfig.class);

        configManager.onChange(ExampleConfig.class, (config, sections) ->
                Logger.info("<gray>Reloaded sections:</gray> <green>{sections}</green>",
                        TagResolver.placeholder("sections", String.join(", ", sections))));
    }

    @Override
    public void onDisable() {
        configManager.shutdown();
    }
}
```

### Send localised feedback inside a command
```java
return CommandResult.success(
        InternalMessages.CMD_EXECUTED.get(sender,
                "subcommand", context.getSubCommandName()));
```

### Build a quick admin menu with localisation
```java
MagicGui gui = MagicGui.builder()
        .title(Messages.get(player, "magicutils.gui.admin.title"))
        .size(27)
        .item(13, MagicItem.of(buttonStack)
                .onClick(event -> handleAdminAction(player)))
        .build();

gui.open(player);
```

---

## Troubleshooting

- **Gradle lock-file errors** – ensure the wrapper writes to a directory you
  control; set `GRADLE_USER_HOME` if necessary.
- **Watcher warnings on shutdown** – confirm `configManager.shutdown()` runs in
  your plugin’s `onDisable`.
- **Missing translations** – inspect `lang/<code>.yml`. MagicUtils seeds
  defaults from `LanguageDefaults`; override keys under `messages` to customise.
- **Logger output not localised** – enable `autoLocalization` in `logger.yml` or
  call `Logger.setAutoLocalization(true)` after initialisation.

---

## Contributing

MagicUtils is maintained internally. Please:

1. Keep documentation (including this README) in sync with code changes.
2. Run `./gradlew publishToMavenLocal` after modifications so dependent
   plugins update automatically.
3. Prefer adding tests or usage examples when introducing new features.

Questions or suggestions? Reach out to the maintainers or open an internal
ticket.

> Explore the `examples/` directory for standalone snippets covering each
> module (`config`, `lang`, `commands`, `logger`, `gui`, `utils`).
