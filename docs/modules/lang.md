# Lang

The lang module manages localisation files, custom messages, and per-player
language overrides.

## Setup

```java
LanguageManager languageManager = new LanguageManager(plugin, configManager);
languageManager.init("en");
languageManager.setFallbackLanguage("en");
languageManager.addMagicUtilsMessages();

Messages.register(plugin.getName(), languageManager);
logger.setLanguageManager(languageManager);
```

Language files are stored under `config/lang/<lang>.yml`. The lang module uses
YAML, so include `magicutils-config-yaml` if you use `LanguageManager`.

Use `Messages.setLanguageManager(...)` only if you need the legacy global
default. `Messages.register(...)` keeps each plugin isolated.

## Threading

Language loading touches disk. Use the async or smart helpers when running on
blocking-sensitive threads:

```java
languageManager.loadLanguageAsync("en");
languageManager.setLanguageAsync("en");
languageManager.reloadAsync();

languageManager.loadLanguageSmart("en");
languageManager.setLanguageSmart("en");
languageManager.reloadSmart();
```

## Resolving messages

```java
MessagesView messages = Messages.view(plugin.getName());

Component title = messages.get("myplugin.welcome");
messages.send(playerAudience, "myplugin.goodbye");

String raw = messages.getRaw("myplugin.balance", "amount", "42");
Component rich = messages.get("myplugin.balance", Map.of("amount", "42"));
```

`Messages` uses MiniMessage for rich output and supports `{placeholder}` style
replacements.

## Per-player languages

```java
languageManager.setPlayerLanguage(playerUuid, "uk");
String msg = languageManager.getMessageForAudience(audience, "myplugin.welcome");
```

`setPlayerLanguage(...)` accepts UUIDs, Audience, or player objects with
`getUniqueId()`.

## Custom messages

```java
languageManager.putCustomMessage("en", "myplugin.welcome", "<green>Hello</green>");
```

Custom messages are persisted in the same language files under `messages`.

## Missing keys

Enable logging for missing translations:

```java
languageManager.setLogMissingMessages(true);
```
