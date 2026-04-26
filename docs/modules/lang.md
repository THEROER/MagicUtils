# Lang

The lang module manages localisation files, custom messages, and per-player
language overrides.

## Setup

Manual setup:

```java
LanguageManager languageManager = new LanguageManager(platform, configManager);
languageManager.init("en");
languageManager.setFallbackLanguage("en");
languageManager.addMagicUtilsMessages();

Messages.register("myplugin", languageManager);
logger.setLanguageManager(languageManager);
```

Bootstrap helpers perform the same wiring automatically when language support is
enabled.

## File Layout And Formats

Language files live under `lang/{lang}.{ext}` inside the platform config
directory.

- YAML is supported when `magicutils-config-yaml` is installed.
- JSON / JSONC work out of the box.
- TOML works when `magicutils-config-toml` is installed.

`Messages.register(scope, manager)` keeps each plugin or mod isolated. Use
`Messages.setLanguageManager(...)` only when you need the legacy global
fallback.

## Resolving Messages

```java
MessagesView messages = Messages.view("myplugin");

Component title = messages.get("myplugin.welcome");
messages.send(playerAudience, "myplugin.goodbye");

String raw = messages.getRaw("myplugin.balance", "amount", "42");
Component rich = messages.get("myplugin.balance", Map.of("amount", "42"));
```

`Messages` uses MiniMessage for rich output and supports `{placeholder}`-style
replacements.

## Per-Player Languages

```java
languageManager.setPlayerLanguage(playerUuid, "uk");
String msg = languageManager.getMessageForAudience(audience, "myplugin.welcome");
```

`setPlayerLanguage(...)` accepts UUIDs, `Audience`, or player objects that
expose `getUniqueId()`.

## Custom Messages

```java
languageManager.putCustomMessage("en", "myplugin.welcome", "<green>Hello</green>");
```

Custom messages are persisted under the `messages` section of the active
language file.

## Async And Smart Loading

Language loading touches disk. Use the async or smart helpers on
blocking-sensitive threads:

```java
languageManager.loadLanguageAsync("en");
languageManager.setLanguageAsync("en");
languageManager.reloadAsync();

languageManager.loadLanguageSmart("en");
languageManager.setLanguageSmart("en");
languageManager.reloadSmart();
```

## Fallbacks And Missing Keys

```java
languageManager.setFallbackLanguage("en");
languageManager.setLogMissingMessages(true);
```

When the current language is missing a key, MagicUtils falls back to the
configured fallback language before logging a missing-message warning.
