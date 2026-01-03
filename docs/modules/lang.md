# Lang

The lang module manages localisation files, custom messages, and per-player
language overrides.

## Setup

```java
LanguageManager languageManager = new LanguageManager(plugin, configManager);
languageManager.init("en");
languageManager.setFallbackLanguage("en");
languageManager.addMagicUtilsMessages();

Messages.setLanguageManager(languageManager);
logger.setLanguageManager(languageManager);
```

Language files are stored under `config/lang/<lang>.yml`. The lang module uses
YAML, so include `magicutils-config-yaml` if you use `LanguageManager`.

## Resolving messages

```java
Component title = Messages.get("myplugin.welcome");
Messages.send(playerAudience, "myplugin.goodbye");

String raw = Messages.getRaw("myplugin.balance", "amount", "42");
Component rich = Messages.get("myplugin.balance", Map.of("amount", "42"));
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
