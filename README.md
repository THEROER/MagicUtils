# MagicUtils

MagicUtils is a utility toolkit for Bukkit/Paper plugins, bundling batteries for configuration management, localisation, command handling, logging, and GUI construction. The library is not published to public repositories; install it from your local Maven cache.

## Building and Installing

1. Ensure JDK 17+ is available. Clone the repository.
2. Publish MagicUtils into your local Maven cache:
   ```bash
   ./gradlew publishToMavenLocal
   ```
3. Add the dependency to your plugin build script:
   ```kts
   repositories {
       mavenLocal()
   }

   dependencies {
       implementation("dev.ua.theroer:magicutils:<version>")
   }
   ```
   The default version is declared in `build.gradle` (e.g. `1.0-SNAPSHOT`).
4. Repeat `publishToMavenLocal` whenever you modify MagicUtils to refresh the artefact.

> **Tip:** If your environment blocks the default Gradle home, export `GRADLE_USER_HOME=$PWD/.gradle` (or another writable directory) before running the wrapper.

## Modules Overview

### `config`
Annotation-driven configuration framework that maps YAML files to POJO classes.

- Use `@ConfigFile`, `@ConfigSection`, `@ConfigValue`, `@Comment`, `@ConfigReloadable` to describe the structure. Prefer setting defaults via field initialisers (`private boolean enabled = true;`); reserve `@DefaultValue` for rare cases when a literal assignment is impossible.
- Leverage Lombok (`@Getter`, `@Setter`, etc.) to avoid boilerplate; built-in config classes follow the same approach.
- `ConfigManager` handles creation, loading, saving, hot reloading (via filesystem watcher) and change notifications.
- Key API methods: `register`, `save(instance)`, `reload(instance)`, `mergeFromFile`, `mergeToFile`, `onChange`.
- Call `configManager.shutdown()` during plugin shutdown to stop the watcher thread.
- Avoid manual YAML parsing; simply add annotated fields with sensible defaults.

### `lang`
Localisation subsystem with global and per-player language support along with MiniMessage formatting.

- `LanguageManager` loads `lang/<code>.yml`, maintains fallback language, and exposes high-level getters (`getMessage(sender, key, …)`, `setPlayerLanguage`, etc.).
- `Messages` is the static façade for convenient access across your codebase (`Messages.getRaw`, `Messages.get(CommandSender, …)`, `Messages.send`).
- `InternalMessages` enumerates internal keys and falls back to English defaults provided by `LanguageDefaults` if no translation is found.
- `LanguageConfig` and `lang/messages/*` are POJOs backed by `@ConfigValue` annotations; keys resolve automatically without manual switch statements.
- Default EN/RU/UA dictionaries ship with the library and populate new language files on first load.

### `commands`
Declarative command framework.

- Extend `MagicCommand` and annotate with `@CommandInfo`, `@SubCommand`, `@OptionalArgument`, `@Suggest`.
- Create custom argument parsers (see `LanguageKeyTypeParser`) for domain-specific needs.
- Reuse `InternalMessages` for feedback to keep localisation consistent.

### `logger`
Configurable logging layer with gradients, prefixes, sub-loggers, and optional localisation.

- `LoggerConfig`, `ChatSettings`, `ConsoleSettings`, `ColorSettings`, `PrefixSettings` map `logger.yml` to strongly-typed objects.
- Supports auto-generated gradients and target-specific prefixes.
- Toggle `auto-localization` to integrate logger output with `LanguageManager`.

### `gui`
Helpers for inventory-based UI (layout builders, click handlers). Combine with `Messages` for translated strings and `config` for configurable layouts.

### `annotations`
Shared annotations used across all modules. Review their Javadoc for available options and behaviours.

### `utils`
Miscellaneous helpers (formatting, Bukkit shortcuts, etc.). Explore `dev.ua.theroer.magicutils.utils` for available utilities.

## Usage Guidelines

- Prefer the high-level APIs (`ConfigManager`, `LanguageManager`, `Messages`) over manual YAML/String handling.
- When mutating config objects at runtime, call `configManager.save(instance)` immediately—diff-aware saves prevent overwriting user edits.
- Use the naming pattern `magicutils.<section>.<key>` for localisation keys; both `LanguageManager` and `InternalMessages` rely on it.
- Persist per-player language preferences (database/config) and reapply through `setPlayerLanguage` when players join.
- Mirror the POJO + annotation approach when adding new modules; it keeps configuration self-documenting and compatible with the watcher.

## Example Workflow

1. Define a configuration class:
   ```java
   @ConfigFile("example.yml")
   @Getter
   public class ExampleConfig {
       @ConfigValue("enabled")
       private boolean enabled = true;
   }
   ```
2. Register it and use the values:
   ```java
   ExampleConfig config = configManager.register(ExampleConfig.class);
   if (config.isEnabled()) {
       // business logic
   }
   ```
3. Send a translated message:
   ```java
   Messages.send(player, InternalMessages.CMD_EXECUTED.getKey());
   ```
4. Update a player's language preference:
   ```java
   languageManager.setPlayerLanguage(player, "ru");
   ```

## Troubleshooting

- **Gradle lock-file errors:** set `GRADLE_USER_HOME` to a writable directory or run the build with proper permissions.
- **Watcher warnings on shutdown:** ensure `configManager.shutdown()` is invoked in your plugin’s `onDisable`.
- **Missing translations:** check `lang/<code>.yml`. MagicUtils seeds files with defaults via `LanguageDefaults`; override keys in the `messages` section as needed.

## Contributing

MagicUtils is maintained internally. Submit patches through your usual workflow and re-run `./gradlew publishToMavenLocal` to refresh your local dependency after changes. Tests and documentation improvements are welcome.
