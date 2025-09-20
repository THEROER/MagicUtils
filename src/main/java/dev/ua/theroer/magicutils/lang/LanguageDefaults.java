package dev.ua.theroer.magicutils.lang;

import java.util.LinkedHashMap;
import java.util.Map;

final class LanguageDefaults {

    static final String DEFAULT_NAME = "English";
    static final String DEFAULT_CODE = "en";
    static final String DEFAULT_AUTHOR = "MagicUtils Team";
    static final String DEFAULT_VERSION = "1.0";

    private LanguageDefaults() {
    }

    static Map<String, String> englishCommands() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("no_permission", "&cYou don't have permission to execute this command!");
        map.put("execution_error", "&cAn error occurred while executing the command");
        map.put("executed", "&aCommand executed successfully");
        map.put("specify_subcommand", "&eSpecify a subcommand: &f{subcommands}");
        map.put("unknown_subcommand", "&cUnknown subcommand: &f{subcommand}");
        map.put("invalid_arguments", "&cInvalid command arguments");
        map.put("not_found", "&cCommand not found");
        map.put("internal_error", "&cAn internal error occurred while executing the command");
        return map;
    }

    static Map<String, String> englishSettings() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("language_not_initialized", "&cLanguage manager not initialized!");
        map.put("invalid_arguments",
                "&cInvalid arguments. First argument must be a language name when using 3 arguments.");
        map.put("current_language", "&aCurrent language: &f{language}");
        map.put("available_languages", "&aAvailable languages: &f{languages}");
        map.put("language_not_found", "&cLanguage '&f{language}&c' not found!");
        map.put("key_not_found", "&cKey '&f{key}&c' not found in language '&f{language}&c'");
        map.put("key_value", "&aLanguage: &f{language}\\n&aKey: &f{key}\\n&aValue: &f{value}");
        map.put("key_set", "&aSet key '&f{key}&a' to '&f{value}&a' in language '&f{language}&a'");
        return map;
    }

    static Map<String, String> englishReload() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("all_commands", "&aAll commands reloaded!");
        map.put("command", "&aCommand &f{command} &areloaded!");
        map.put("all_sections", "&aAll sections reloaded!");
        map.put("section", "&aSection &f{section} &areloaded!");
        map.put("global_settings", "&aGlobal settings reloaded!");
        map.put("global_setting", "&aGlobal setting &f{setting} &areloaded!");
        return map;
    }

    static Map<String, String> englishSystem() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("loaded_language", "Loaded language: {language}");
        map.put("failed_load_language", "Failed to load language: {language}");
        map.put("failed_save_messages", "Failed to save custom messages for language: {language}");
        map.put("created_default_config", "Created default config: {file}");
        map.put("section_not_reloadable", "Section not reloadable: {section}");
        map.put("command_registered", "Successfully registered command: {command} with aliases: {aliases}");
        map.put("command_usage", "Command usage: {usage}");
        map.put("subcommand_usages", "Subcommand usages:");
        map.put("alias_registered", "Successfully registered alias: {alias} for command: {command}");
        map.put("alias_usage", "Alias usage: {usage}");
        map.put("generated_permissions", "Generated permissions for {command}: {permissions}");
        map.put("unregistered_command", "Unregistered command: {command}");
        return map;
    }

    static Map<String, String> englishErrors() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("message_not_set", "Message must be set before sending");
        map.put("failed_get_commandmap", "Failed to get CommandMap");
        map.put("registry_not_initialized",
                "CommandRegistry not initialized! Call initialize() first.");
        map.put("commandmap_not_available", "CommandMap not available!");
        map.put("missing_commandinfo", "Command class must have @CommandInfo annotation: {class}");
        map.put("missing_configfile", "Class {class} must have @ConfigFile annotation");
        map.put("required_config_missing", "Missing required config value: {path}");
        return map;
    }

    static Map<String, String> englishTranslations() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        putPrefixed(map, "magicutils.commands", englishCommands());
        putPrefixed(map, "magicutils.settings", englishSettings());
        putPrefixed(map, "magicutils.reload", englishReload());
        putPrefixed(map, "magicutils.system", englishSystem());
        putPrefixed(map, "magicutils.errors", englishErrors());
        return map;
    }

    private static void putPrefixed(Map<String, String> target, String prefix, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            target.put(prefix + "." + entry.getKey(), entry.getValue());
        }
    }

    static Map<String, Map<String, String>> localizedSections(String languageCode) {
        Map<String, Map<String, String>> translations = new LinkedHashMap<>();
        switch (languageCode) {
            case "ru":
                translations.put("language", russianMetadata());
                translations.put("magicutils.commands", russianCommands());
                translations.put("magicutils.settings", russianSettings());
                translations.put("magicutils.reload", russianReload());
                translations.put("magicutils.system", russianSystem());
                translations.put("magicutils.errors", russianErrors());
                break;
            case "uk":
                translations.put("language", ukrainianMetadata());
                translations.put("magicutils.commands", ukrainianCommands());
                translations.put("magicutils.settings", ukrainianSettings());
                translations.put("magicutils.reload", ukrainianReload());
                translations.put("magicutils.system", ukrainianSystem());
                translations.put("magicutils.errors", ukrainianErrors());
                break;
            default:
                break;
        }
        return translations;
    }

    private static Map<String, String> russianMetadata() {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("name", "Русский");
        metadata.put("code", "ru");
        metadata.put("author", DEFAULT_AUTHOR);
        metadata.put("version", DEFAULT_VERSION);
        return metadata;
    }

    private static Map<String, String> russianCommands() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("no_permission", "&cУ вас нет прав для выполнения этой команды!");
        map.put("execution_error", "&cПроизошла ошибка при выполнении команды");
        map.put("executed", "&aКоманда выполнена успешно");
        map.put("specify_subcommand", "&eУкажите подкоманду: &f{subcommands}");
        map.put("unknown_subcommand", "&cНеизвестная подкоманда: &f{subcommand}");
        map.put("invalid_arguments", "&cНеверные аргументы команды");
        map.put("not_found", "&cКоманда не найдена");
        map.put("internal_error", "&cПроизошла внутренняя ошибка при выполнении команды");
        return map;
    }

    private static Map<String, String> russianSettings() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("language_not_initialized", "&cМенеджер языков не инициализирован!");
        map.put("invalid_arguments",
                "&cНеверные аргументы. Первый аргумент должен быть названием языка при использовании 3 аргументов.");
        map.put("current_language", "&aТекущий язык: &f{language}");
        map.put("available_languages", "&aДоступные языки: &f{languages}");
        map.put("language_not_found", "&cЯзык '&f{language}&c' не найден!");
        map.put("key_not_found", "&cКлюч '&f{key}&c' не найден в языке '&f{language}&c'");
        map.put("key_value", "&aЯзык: &f{language}\\n&aКлюч: &f{key}\\n&aЗначение: &f{value}");
        map.put("key_set", "&aКлюч '&f{key}&a' установлен в '&f{value}&a' для языка '&f{language}&a'");
        return map;
    }

    private static Map<String, String> russianReload() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("all_commands", "&aВсе команды перезагружены!");
        map.put("command", "&aКоманда &f{command} &aперезагружена!");
        map.put("all_sections", "&aВсе секции перезагружены!");
        map.put("section", "&aСекция &f{section} &aперезагружена!");
        map.put("global_settings", "&aГлобальные настройки перезагружены!");
        map.put("global_setting", "&aГлобальное настройка &f{setting} &aперезагружено!");
        return map;
    }

    private static Map<String, String> russianSystem() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("loaded_language", "Загружен язык: {language}");
        map.put("failed_load_language", "Не удалось загрузить язык: {language}");
        map.put("failed_save_messages", "Не удалось сохранить пользовательские сообщения для языка: {language}");
        map.put("created_default_config", "Создан конфиг по умолчанию: {file}");
        map.put("section_not_reloadable", "Секция не перезагружается: {section}");
        map.put("command_registered", "Успешно зарегистрирована команда: {command} с алиасами: {aliases}");
        map.put("command_usage", "Использование команды: {usage}");
        map.put("subcommand_usages", "Использование подкоманд:");
        map.put("alias_registered", "Успешно зарегистрирован алиас: {alias} для команды: {command}");
        map.put("alias_usage", "Использование алиаса: {usage}");
        map.put("generated_permissions", "Сгенерированы права для {command}: {permissions}");
        map.put("unregistered_command", "Отменена регистрация команды: {command}");
        return map;
    }

    private static Map<String, String> russianErrors() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("message_not_set", "Сообщение должно быть установлено перед отправкой");
        map.put("failed_get_commandmap", "Не удалось получить CommandMap");
        map.put("registry_not_initialized",
                "CommandRegistry не инициализирован! Сначала вызовите initialize().");
        map.put("commandmap_not_available", "CommandMap недоступен!");
        map.put("missing_commandinfo", "Класс команды должен иметь аннотацию @CommandInfo: {class}");
        map.put("missing_configfile", "Класс {class} должен иметь аннотацию @ConfigFile");
        map.put("required_config_missing", "Отсутствует обязательное значение конфигурации: {path}");
        return map;
    }

    private static Map<String, String> ukrainianMetadata() {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("name", "Українська");
        metadata.put("code", "uk");
        metadata.put("author", DEFAULT_AUTHOR);
        metadata.put("version", DEFAULT_VERSION);
        return metadata;
    }

    private static Map<String, String> ukrainianCommands() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("no_permission", "&cУ вас немає прав для виконання цієї команди!");
        map.put("execution_error", "&cПід час виконання команди сталася помилка");
        map.put("executed", "&aКоманду виконано успішно");
        map.put("specify_subcommand", "&eВкажіть підкоманду: &f{subcommands}");
        map.put("unknown_subcommand", "&cНевідома підкоманда: &f{subcommand}");
        map.put("invalid_arguments", "&cНеправильні аргументи команди");
        map.put("not_found", "&cКоманду не знайдено");
        map.put("internal_error", "&cСталася внутрішня помилка під час виконання команди");
        return map;
    }

    private static Map<String, String> ukrainianSettings() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("language_not_initialized", "&cМенеджер мов не ініціалізований!");
        map.put("invalid_arguments",
                "&cНеправильні аргументи. Перший аргумент повинен бути назвою мови при використанні 3 аргументів.");
        map.put("current_language", "&aПоточна мова: &f{language}");
        map.put("available_languages", "&aДоступні мови: &f{languages}");
        map.put("language_not_found", "&cМова '&f{language}&c' не знайдена!");
        map.put("key_not_found", "&cКлюч '&f{key}&c' не знайдений в мові '&f{language}&c'");
        map.put("key_value", "&aМова: &f{language}\\n&aКлюч: &f{key}\\n&aЗначення: &f{value}");
        map.put("key_set", "&aКлюч '&f{key}&a' встановлено в '&f{value}&a' для мови '&f{language}&a'");
        return map;
    }

    private static Map<String, String> ukrainianReload() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("all_commands", "&aВсі команди перезавантажені!");
        map.put("command", "&aКоманда &f{command} &aперезавантажена!");
        map.put("all_sections", "&aВсі секції перезавантажені!");
        map.put("section", "&aСекція &f{section} &aперезавантажена!");
        map.put("global_settings", "&aГлобальні налаштування перезавантажені!");
        map.put("global_setting", "&aГлобальне налаштування &f{setting} &aперезавантажене!");
        return map;
    }

    private static Map<String, String> ukrainianSystem() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("loaded_language", "Завантажено мову: {language}");
        map.put("failed_load_language", "Не вдалося завантажити мову: {language}");
        map.put("failed_save_messages", "Не вдалося зберегти користувацькі повідомлення для мови: {language}");
        map.put("created_default_config", "Створено конфіг за замовчуванням: {file}");
        map.put("section_not_reloadable", "Секція не перезавантажувана: {section}");
        map.put("command_registered", "Успішно зареєстровано команду: {command} з аліасами: {aliases}");
        map.put("command_usage", "Використання команди: {usage}");
        map.put("subcommand_usages", "Використання підкоманд:");
        map.put("alias_registered", "Успішно зареєстровано аліас: {alias} для команди: {command}");
        map.put("alias_usage", "Використання аліасу: {usage}");
        map.put("generated_permissions", "Згенеровані права для {command}: {permissions}");
        map.put("unregistered_command", "Скасовано реєстрацію команди: {command}");
        return map;
    }

    private static Map<String, String> ukrainianErrors() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("message_not_set", "Повідомлення повинно бути встановлено перед відправкою");
        map.put("failed_get_commandmap", "Не вдалося отримати CommandMap");
        map.put("registry_not_initialized",
                "CommandRegistry не ініціалізований! Спочатку викличте initialize().");
        map.put("commandmap_not_available", "CommandMap недоступний!");
        map.put("missing_commandinfo", "Клас команди повинен мати анотацію @CommandInfo: {class}");
        map.put("missing_configfile", "Клас {class} повинен мати анотацію @ConfigFile");
        map.put("required_config_missing", "Відсутнє обов'язкове значення конфігу: {path}");
        return map;
    }
}
