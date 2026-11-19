package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.platform.Platform;

/**
 * Backs the legacy {@code LanguageManager(Object, ConfigManager)} constructor: accepts a
 * {@link Platform} directly or wraps a Bukkit plugin through {@code BukkitPlatformProvider},
 * looked up reflectively so the lang module carries no Bukkit dependency.
 */
final class LegacyPlatformResolver {

    private LegacyPlatformResolver() {
    }

    static Platform resolve(Object platformOrPlugin) {
        if (platformOrPlugin instanceof Platform) {
            return (Platform) platformOrPlugin;
        }
        if (platformOrPlugin != null) {
            try {
                Class<?> providerClass = Class
                        .forName("dev.ua.theroer.magicutils.platform.bukkit.BukkitPlatformProvider");
                for (var ctor : providerClass.getConstructors()) {
                    if (ctor.getParameterCount() == 1
                            && ctor.getParameterTypes()[0].isAssignableFrom(platformOrPlugin.getClass())) {
                        Object instance = ctor.newInstance(platformOrPlugin);
                        if (instance instanceof Platform) {
                            return (Platform) instance;
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        throw new IllegalArgumentException("Platform provider is required to initialize LanguageManager");
    }
}
