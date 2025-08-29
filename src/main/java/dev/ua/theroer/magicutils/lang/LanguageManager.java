package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.config.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Universal language manager for plugins.
 * Supports loading language files from resources and custom files.
 */
public class LanguageManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, LanguageConfig> loadedLanguages = new HashMap<>();
    private String currentLanguage = "en";
    private LanguageConfig currentConfig;
    private LanguageConfig fallbackConfig;
    
    /**
     * Creates a new LanguageManager with ConfigManager.
     * @param plugin the plugin instance
     * @param configManager the config manager
     */
    public LanguageManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }
    
    /**
     * Initializes the language manager with default language.
     * @param defaultLanguage the default language code
     */
    public void init(String defaultLanguage) {
        this.currentLanguage = defaultLanguage;
        loadLanguage(currentLanguage);
        
        // Load fallback language (usually English)
        if (!currentLanguage.equals("en")) {
            loadFallbackLanguage("en");
        }
    }
    
    /**
     * Loads a language file.
     * @param languageCode the language code (e.g., "en", "uk", "es")
     * @return true if loaded successfully
     */
    public boolean loadLanguage(String languageCode) {
        try {
            // Create placeholders for the file path
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("lang", languageCode);
            
            // Register with ConfigManager which will handle file creation and loading
            LanguageConfig config = configManager.register(LanguageConfig.class, placeholders);
            
            // Initialize language-specific defaults
            initializeLanguageDefaults(config, languageCode);
            
            loadedLanguages.put(languageCode, config);
            
            if (languageCode.equals(currentLanguage)) {
                currentConfig = config;
            }
            
            plugin.getLogger().info(InternalMessages.SYS_LOADED_LANGUAGE.get("language", languageCode));
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, InternalMessages.SYS_FAILED_LOAD_LANGUAGE.get("language", languageCode), e);
            return false;
        }
    }
    
    /**
     * Initializes language-specific defaults.
     */
    private void initializeLanguageDefaults(LanguageConfig config, String languageCode) {
        // Check if this is a newly created file by checking if metadata is still default
        boolean isNewFile = config.getMetadata().getCode().equals("en") && !languageCode.equals("en");
        
        if (isNewFile) {
            // Create language-specific translations
            Map<String, Map<String, String>> translations = createTranslations(languageCode);
            
            if (!translations.isEmpty()) {
                // We need to save the config with proper translations
                // Since we can't modify private fields, we'll use a different approach
                // We'll save directly to the file after ConfigManager creates it
                saveLanguageTranslations(languageCode, translations);
                
                // Reload the config to pick up the new translations
                try {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("lang", languageCode);
                    configManager.reload(LanguageConfig.class);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to reload language config after setting translations", e);
                }
            }
        }
    }
    
    /**
     * Loads fallback language.
     * @param languageCode the fallback language code
     */
    private void loadFallbackLanguage(String languageCode) {
        if (loadLanguage(languageCode)) {
            fallbackConfig = loadedLanguages.get(languageCode);
        }
    }
    
    /**
     * Sets the current language.
     * @param languageCode the language code
     * @return true if language was loaded successfully
     */
    public boolean setLanguage(String languageCode) {
        if (!loadedLanguages.containsKey(languageCode)) {
            if (!loadLanguage(languageCode)) {
                return false;
            }
        }
        
        this.currentLanguage = languageCode;
        this.currentConfig = loadedLanguages.get(languageCode);
        return true;
    }
    
    /**
     * Gets the current language code.
     * @return current language code
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }
    
    /**
     * Gets available language codes by scanning the lang directory.
     * @return set of available language codes
     */
    public Set<String> getAvailableLanguages() {
        Set<String> languages = new HashSet<>();
        File langDir = new File(plugin.getDataFolder(), "lang");
        
        if (langDir.exists() && langDir.isDirectory()) {
            File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    languages.add(name.substring(0, name.length() - 4));
                }
            }
        }
        
        // Add loaded languages too
        languages.addAll(loadedLanguages.keySet());
        
        return languages;
    }
    
    /**
     * Gets information about available languages.
     * @return map of language code to language info
     */
    public Map<String, LanguageInfo> getLanguageInfos() {
        Map<String, LanguageInfo> infos = new HashMap<>();
        
        for (String code : getAvailableLanguages()) {
            if (!loadedLanguages.containsKey(code)) {
                // Load temporarily to get info
                loadLanguage(code);
            }
            
            LanguageConfig config = loadedLanguages.get(code);
            if (config != null) {
                infos.put(code, new LanguageInfo(
                    code,
                    config.getMetadata().getName(),
                    config.getMetadata().getAuthor(),
                    config.getMetadata().getVersion()
                ));
            }
        }
        
        return infos;
    }
    
    /**
     * Gets a message by key.
     * @param key the message key (supports dot notation)
     * @return the message or key if not found
     */
    public String getMessage(String key) {
        String message = null;
        
        // Try current language first
        if (currentConfig != null) {
            message = currentConfig.getMessage(key);
        }
        
        // Fall back to fallback language
        if (message == null && fallbackConfig != null) {
            message = fallbackConfig.getMessage(key);
        }
        
        // Return key if message not found
        return message != null ? message : key;
    }
    
    /**
     * Gets a message with placeholder replacements.
     * @param key the message key
     * @param placeholders map of placeholder -> value
     * @return the formatted message
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getMessage(key);
        
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        
        return message;
    }
    
    /**
     * Gets a message with varargs placeholder replacements.
     * @param key the message key
     * @param replacements placeholder, value pairs
     * @return the formatted message
     */
    public String getMessage(String key, String... replacements) {
        String message = getMessage(key);
        
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String placeholder = replacements[i];
            String value = replacements[i + 1];
            
            // Support both {placeholder} and placeholder format
            message = message.replace("{" + placeholder + "}", value);
            message = message.replace(placeholder, value);
        }
        
        return message;
    }
    
    /**
     * Checks if a message key exists.
     * @param key the message key
     * @return true if key exists
     */
    public boolean hasMessage(String key) {
        if (currentConfig != null && currentConfig.getMessage(key) != null) {
            return true;
        }
        
        return fallbackConfig != null && fallbackConfig.getMessage(key) != null;
    }
    
    /**
     * Reloads all loaded languages.
     */
    public void reload() {
        Set<String> languages = Set.copyOf(loadedLanguages.keySet());
        loadedLanguages.clear();
        
        for (String lang : languages) {
            loadLanguage(lang);
        }
        
        // Restore current language
        if (loadedLanguages.containsKey(currentLanguage)) {
            currentConfig = loadedLanguages.get(currentLanguage);
        }
    }
    
    /**
     * Saves custom messages to file.
     * @param languageCode the language code
     * @param customMessages custom messages to save
     */
    public void saveCustomMessages(String languageCode, Map<String, String> customMessages) {
        try {
            LanguageConfig config = loadedLanguages.get(languageCode);
            if (config == null) {
                // Load or create new language config
                loadLanguage(languageCode);
                config = loadedLanguages.get(languageCode);
            }
            
            if (config != null) {
                config.getCustomMessages().putAll(customMessages);
                configManager.save(config.getClass());
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, InternalMessages.SYS_FAILED_SAVE_MESSAGES.get("language", languageCode), e);
        }
    }
    
    /**
     * Adds internal MagicUtils messages to the language
     */
    public void addMagicUtilsMessages() {
        // This will be called by MagicUtils to register its internal messages
        for (String lang : loadedLanguages.keySet()) {
            LanguageConfig config = loadedLanguages.get(lang);
            // The LanguageConfig already has all MagicUtils messages as defaults
            configManager.save(config.getClass());
        }
    }
    
    /**
     * Creates language-specific translations.
     */
    private Map<String, Map<String, String>> createTranslations(String languageCode) {
        Map<String, Map<String, String>> translations = new HashMap<>();
        
        switch (languageCode) {
            case "ru":
                translations.put("language", createRussianMetadata());
                translations.put("magicutils.commands", createRussianCommandMessages());
                translations.put("magicutils.settings", createRussianSettingsMessages());
                translations.put("magicutils.reload", createRussianReloadMessages());
                translations.put("magicutils.system", createRussianSystemMessages());
                translations.put("magicutils.errors", createRussianErrorMessages());
                break;
            case "uk":
                translations.put("language", createUkrainianMetadata());
                translations.put("magicutils.commands", createUkrainianCommandMessages());
                translations.put("magicutils.settings", createUkrainianSettingsMessages());
                translations.put("magicutils.reload", createUkrainianReloadMessages());
                translations.put("magicutils.system", createUkrainianSystemMessages());
                translations.put("magicutils.errors", createUkrainianErrorMessages());
                break;
        }
        
        return translations;
    }
    
    private Map<String, String> createRussianMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("name", "Русский");
        metadata.put("code", "ru");
        metadata.put("author", "MagicUtils Team");
        metadata.put("version", "1.0");
        return metadata;
    }
    
    private Map<String, String> createRussianCommandMessages() {
        Map<String, String> messages = new HashMap<>();
        messages.put("no_permission", "&cУ вас нет прав для выполнения этой команды!");
        messages.put("execution_error", "&cПроизошла ошибка при выполнении команды");
        messages.put("executed", "&aКоманда выполнена успешно");
        messages.put("specify_subcommand", "&eУкажите подкоманду: &f{subcommands}");
        messages.put("unknown_subcommand", "&cНеизвестная подкоманда: &f{subcommand}");
        messages.put("invalid_arguments", "&cНеверные аргументы команды");
        messages.put("not_found", "&cКоманда не найдена");
        messages.put("internal_error", "&cПроизошла внутренняя ошибка при выполнении команды");
        return messages;
    }
    
    private Map<String, String> createRussianSettingsMessages() {
        Map<String, String> messages = new HashMap<>();
        messages.put("language_not_initialized", "&cМенеджер языков не инициализирован!");
        messages.put("invalid_arguments", "&cНеверные аргументы. Первый аргумент должен быть названием языка при использовании 3 аргументов.");
        messages.put("current_language", "&aТекущий язык: &f{language}");
        messages.put("available_languages", "&aДоступные языки: &f{languages}");
        messages.put("language_not_found", "&cЯзык '&f{language}&c' не найден!");
        messages.put("key_not_found", "&cКлюч '&f{key}&c' не найден в языке '&f{language}&c'");
        messages.put("key_value", "&aЯзык: &f{language}\n&aКлюч: &f{key}\n&aЗначение: &f{value}");
        messages.put("key_set", "&aКлюч '&f{key}&a' установлен в '&f{value}&a' для языка '&f{language}&a'");
        return messages;
    }
    
    private Map<String, String> createRussianReloadMessages() {
        Map<String, String> messages = new HashMap<>();
        messages.put("all_commands", "&aВсе команды перезагружены!");
        messages.put("command", "&aКоманда &f{command} &aперезагружена!");
        messages.put("all_sections", "&aВсе секции перезагружены!");
        messages.put("section", "&aСекция &f{section} &aперезагружена!");
        messages.put("global_settings", "&aГлобальные настройки перезагружены!");
        messages.put("global_setting", "&aГлобальная настройка &f{setting} &aперезагружена!");
        return messages;
    }
    
    private Map<String, String> createRussianSystemMessages() {
        Map<String, String> messages = new HashMap<>();
        messages.put("loaded_language", "Загружен язык: {language}");
        messages.put("failed_load_language", "Не удалось загрузить язык: {language}");
        messages.put("failed_save_messages", "Не удалось сохранить пользовательские сообщения для языка: {language}");
        messages.put("created_default_config", "Создан конфиг по умолчанию: {file}");
        messages.put("section_not_reloadable", "Секция не перезагружаемая: {section}");
        messages.put("command_registered", "Успешно зарегистрирована команда: {command} с алиасами: {aliases}");
        messages.put("command_usage", "Использование команды: {usage}");
        messages.put("subcommand_usages", "Использование подкоманд:");
        messages.put("alias_registered", "Успешно зарегистрирован алиас: {alias} для команды: {command}");
        messages.put("alias_usage", "Использование алиаса: {usage}");
        messages.put("generated_permissions", "Сгенерированы права для {command}: {permissions}");
        messages.put("unregistered_command", "Отменена регистрация команды: {command}");
        return messages;
    }
    
    private Map<String, String> createRussianErrorMessages() {
        Map<String, String> messages = new HashMap<>();
        messages.put("message_not_set", "Сообщение должно быть установлено перед отправкой");
        messages.put("failed_get_commandmap", "Не удалось получить CommandMap");
        messages.put("registry_not_initialized", "CommandRegistry не инициализирован! Сначала вызовите initialize().");
        messages.put("commandmap_not_available", "CommandMap недоступен!");
        messages.put("missing_commandinfo", "Класс команды должен иметь аннотацию @CommandInfo: {class}");
        messages.put("missing_configfile", "Класс {class} должен иметь аннотацию @ConfigFile");
        messages.put("required_config_missing", "Отсутствует обязательное значение конфига: {path}");
        return messages;
    }
    
    // Ukrainian translations
    private Map<String, String> createUkrainianMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("name", "Українська");
        metadata.put("code", "uk");
        metadata.put("author", "MagicUtils Team");
        metadata.put("version", "1.0");
        return metadata;
    }
    
    private Map<String, String> createUkrainianCommandMessages() {
        Map<String, String> messages = new HashMap<>();
        messages.put("no_permission", "&cВи не маєте прав для виконання цієї команди!");
        messages.put("execution_error", "&cСталася помилка при виконанні команди");
        messages.put("executed", "&aКоманда виконана успішно");
        messages.put("specify_subcommand", "&eВкажіть підкоманду: &f{subcommands}");
        messages.put("unknown_subcommand", "&cНевідома підкоманда: &f{subcommand}");
        messages.put("invalid_arguments", "&cНевірні аргументи команди");
        messages.put("not_found", "&cКоманда не знайдена");
        messages.put("internal_error", "&cСталася внутрішня помилка при виконанні команди");
        return messages;
    }
    
    private Map<String, String> createUkrainianSettingsMessages() {
        Map<String, String> messages = new HashMap<>();
        messages.put("language_not_initialized", "&cМенеджер мов не ініціалізований!");
        messages.put("invalid_arguments", "&cНевірні аргументи. Перший аргумент повинен бути назвою мови при використанні 3 аргументів.");
        messages.put("current_language", "&aПоточна мова: &f{language}");
        messages.put("available_languages", "&aДоступні мови: &f{languages}");
        messages.put("language_not_found", "&cМова '&f{language}&c' не знайдена!");
        messages.put("key_not_found", "&cКлюч '&f{key}&c' не знайдений в мові '&f{language}&c'");
        messages.put("key_value", "&aМова: &f{language}\n&aКлюч: &f{key}\n&aЗначення: &f{value}");
        messages.put("key_set", "&aКлюч '&f{key}&a' встановлено в '&f{value}&a' для мови '&f{language}&a'");
        return messages;
    }
    
    private Map<String, String> createUkrainianReloadMessages() {
        Map<String, String> messages = new HashMap<>();
        messages.put("all_commands", "&aВсі команди перезавантажені!");
        messages.put("command", "&aКоманда &f{command} &aперезавантажена!");
        messages.put("all_sections", "&aВсі секції перезавантажені!");
        messages.put("section", "&aСекція &f{section} &aперезавантажена!");
        messages.put("global_settings", "&aГлобальні налаштування перезавантажені!");
        messages.put("global_setting", "&aГлобальне налаштування &f{setting} &aперезавантажене!");
        return messages;
    }
    
    private Map<String, String> createUkrainianSystemMessages() {
        Map<String, String> messages = new HashMap<>();
        messages.put("loaded_language", "Завантажено мову: {language}");
        messages.put("failed_load_language", "Не вдалося завантажити мову: {language}");
        messages.put("failed_save_messages", "Не вдалося зберегти користувацькі повідомлення для мови: {language}");
        messages.put("created_default_config", "Створено конфіг за замовчуванням: {file}");
        messages.put("section_not_reloadable", "Секція не перезавантажувана: {section}");
        messages.put("command_registered", "Успішно зареєстровано команду: {command} з аліасами: {aliases}");
        messages.put("command_usage", "Використання команди: {usage}");
        messages.put("subcommand_usages", "Використання підкоманд:");
        messages.put("alias_registered", "Успішно зареєстровано аліас: {alias} для команди: {command}");
        messages.put("alias_usage", "Використання аліасу: {usage}");
        messages.put("generated_permissions", "Згенеровані права для {command}: {permissions}");
        messages.put("unregistered_command", "Скасовано реєстрацію команди: {command}");
        return messages;
    }
    
    private Map<String, String> createUkrainianErrorMessages() {
        Map<String, String> messages = new HashMap<>();
        messages.put("message_not_set", "Повідомлення повинно бути встановлено перед відправкою");
        messages.put("failed_get_commandmap", "Не вдалося отримати CommandMap");
        messages.put("registry_not_initialized", "CommandRegistry не ініціалізований! Спочатку викличте initialize().");
        messages.put("commandmap_not_available", "CommandMap недоступний!");
        messages.put("missing_commandinfo", "Клас команди повинен мати анотацію @CommandInfo: {class}");
        messages.put("missing_configfile", "Клас {class} повинен мати анотацію @ConfigFile");
        messages.put("required_config_missing", "Відсутнє обов'язкове значення конфігу: {path}");
        return messages;
    }
    
    /**
     * Saves language translations to file.
     */
    private void saveLanguageTranslations(String languageCode, Map<String, Map<String, String>> translations) {
        try {
            File langFile = new File(plugin.getDataFolder(), "lang/" + languageCode + ".yml");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(langFile);
            
            // Set all translations
            for (Map.Entry<String, Map<String, String>> section : translations.entrySet()) {
                for (Map.Entry<String, String> entry : section.getValue().entrySet()) {
                    yaml.set(section.getKey() + "." + entry.getKey(), entry.getValue());
                }
            }
            
            yaml.save(langFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save language translations for: " + languageCode, e);
        }
    }
}