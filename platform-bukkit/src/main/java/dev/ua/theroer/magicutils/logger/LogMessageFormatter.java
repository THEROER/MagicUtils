package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.config.logger.LoggerConfig;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.utils.ColorUtils;
import dev.ua.theroer.magicutils.utils.placeholders.PlaceholderProcessor;
import java.util.Collection;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Formats raw logger input into Adventure components applying placeholders, prefixes and colors.
 */
public final class LogMessageFormatter {
    private LogMessageFormatter() {
    }

    /**
     * Converts an arbitrary message object into a formatted component ready for delivery.
     *
     * @param message            arbitrary value (String, Component, Throwable, etc.)
     * @param level              log level to render
     * @param target             log target describing prefix/colors
     * @param directPlayer       explicit player recipient (may be null)
     * @param playerCollection   collection of players to consider for placeholder context
     * @param placeholdersArgs   placeholder arguments passed by logger
     * @return formatted component with prefixes and resolved placeholders
     */
    public static Component format(
            Object message,
            LogLevel level,
            LogTarget target,
            @Nullable Player directPlayer,
            @Nullable Collection<? extends Player> playerCollection,
            Object... placeholdersArgs) {

        String content = stringify(message);
        Object[] args = placeholdersArgs != null ? placeholdersArgs : new Object[0];
        Player targetPlayer = resolveTargetPlayer(directPlayer, playerCollection, args);

        String processed = applyPipeline(content, targetPlayer, args);
        String finalMessage = attachPrefix(processed, level, target);

        Component component = Logger.getMiniMessage().deserialize(finalMessage, TagResolver.standard());
        if ((target == LogTarget.CONSOLE || target == LogTarget.BOTH) && Logger.isConsoleStripFormatting()) {
            component = Component.text(PlainTextComponentSerializer.plainText().serialize(component));
        }
        return component;
    }

    private static String stringify(Object message) {
        if (message instanceof Component component) {
            return Logger.getMiniMessage().serialize(component);
        }
        if (message instanceof Throwable throwable) {
            return formatThrowable(throwable);
        }
        return String.valueOf(message);
    }

    private static Player resolveTargetPlayer(
            @Nullable Player directPlayer,
            @Nullable Collection<? extends Player> playerCollection,
            Object[] placeholdersArgs) {

        Player candidate = null;
        if (placeholdersArgs.length > 0) {
            Object lastArg = placeholdersArgs[placeholdersArgs.length - 1];
            if (lastArg instanceof Player) {
                candidate = (Player) lastArg;
            }
        }
        if (directPlayer != null) {
            candidate = directPlayer;
        }
        if (candidate != null) {
            return candidate;
        }
        return PlaceholderProcessor.pickPrimaryPlayer(directPlayer, playerCollection);
    }

    private static String applyPipeline(String messageStr, @Nullable Player player, Object[] args) {
        String processed = PlaceholderProcessor.applyPlaceholders(Logger.getPluginInstance(), player, messageStr, args);
        processed = PlaceholderProcessor.applyPapi(Logger.getPluginInstance(), processed, player);
        processed = applyLocalization(processed);
        processed = PlaceholderProcessor.applyPapi(Logger.getPluginInstance(), processed, player);
        processed = PlaceholderProcessor.applyPlaceholders(Logger.getPluginInstance(), player, processed, args);
        return ColorUtils.legacyToMiniMessage(processed);
    }

    private static String attachPrefix(String message, LogLevel level, LogTarget target) {
        PrefixMode mode = (target == LogTarget.CONSOLE || target == LogTarget.BOTH)
                ? Logger.getConsolePrefixMode()
                : Logger.getChatPrefixMode();
        String prefix = buildPrefix(level, mode);

        String combined = prefix.isEmpty() ? message : prefix + " " + message;
        if (!prefix.isEmpty() && shouldUseGradient(target)) {
            String[] colors = Logger.resolveColorsForLevel(level, target == LogTarget.CONSOLE);
            String gradientTag = ColorUtils.createGradientTag(colors);
            return "<reset>" + gradientTag + combined + "</gradient>";
        }
        return "<reset>" + combined;
    }

    private static String buildPrefix(LogLevel level, PrefixMode mode) {
        if (mode == PrefixMode.NONE) {
            return "";
        }

        LoggerConfig cfg = Logger.getConfig();
        String prefixText = switch (mode) {
            case SHORT -> cfg != null ? cfg.getShortName() : "UAP";
            case FULL -> cfg != null ? cfg.getPluginName() : "UnknownPlugin";
            case CUSTOM -> Logger.getCustomPrefix();
            default -> "";
        };

        if (level != LogLevel.INFO) {
            prefixText = prefixText + " " + level.name();
        }

        return "[" + prefixText + "]";
    }

    private static boolean shouldUseGradient(LogTarget target) {
        if (target == LogTarget.CONSOLE) {
            return Logger.isConsoleUseGradient();
        }
        if (target == LogTarget.CHAT) {
            LoggerConfig cfg = Logger.getConfig();
            return cfg != null && cfg.getPrefix() != null && cfg.getPrefix().isUseGradientChat();
        }
        return true;
    }

    private static String applyLocalization(String messageStr) {
        LoggerConfig cfg = Logger.getConfig();
        LanguageManager lang = Logger.getLanguageManager();
        if (cfg != null && cfg.isAutoLocalization() && lang != null && messageStr != null
                && messageStr.startsWith("@")) {
            String localized = lang.getMessage(messageStr.substring(1));
            if (!localized.equals(messageStr)) {
                return localized;
            }
        }
        return messageStr;
    }

    private static String formatThrowable(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append("<red>").append(throwable.getClass().getSimpleName()).append(": ")
                .append(throwable.getMessage()).append("</red>\n");

        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("<gray>  at ").append(element.toString()).append("</gray>\n");
        }

        if (throwable.getCause() != null) {
            sb.append("<yellow>Caused by: </yellow>");
            sb.append(formatThrowable(throwable.getCause()));
        }

        return sb.toString();
    }
}
