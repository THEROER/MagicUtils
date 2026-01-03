# Lang

The lang module manages localisation files and per-player language settings.

## Example

```java
LanguageManager languageManager = new LanguageManager(plugin, configManager);
languageManager.init("en");
languageManager.setFallbackLanguage("en");
languageManager.addMagicUtilsMessages();
Messages.setLanguageManager(languageManager);
Logger.setLanguageManager(languageManager);
```

Use `Messages.send` or return `CommandResult` instances with translated keys.
