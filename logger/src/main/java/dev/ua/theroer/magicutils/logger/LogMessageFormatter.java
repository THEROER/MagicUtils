package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.config.logger.LoggerConfig;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.utils.ColorUtils;
import dev.ua.theroer.magicutils.utils.placeholders.PlaceholderProcessor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.jetbrains.annotations.Nullable;
import java.util.Collection;

/**
 * Formats raw logger input into Adventure components applying placeholders, prefixes and colors.
 */
public final class LogMessageFormatter {
    private LogMessageFormatter() {
    }

    /**
     * Converts an arbitrary message object into a formatted component ready for delivery.
     *
     * @param logger logger core instance
     * @param message arbitrary value (String, Component, Throwable, etc.)
     * @param level log level to render
     * @param target log target describing prefix/colors
     * @param prefixOverride optional prefix mode override
     * @param directAudience explicit audience recipient (may be null)
     * @param audienceCollection collection of audiences to consider for placeholder context
     * @param placeholdersArgs placeholder arguments passed by logger
     * @return formatted component with prefixes and resolved placeholders
     */
    public static Component format(
            LoggerCore logger,
            Object message,
            LogLevel level,
            LogTarget target,
            @Nullable PrefixMode prefixOverride,
            @Nullable Audience directAudience,
            @Nullable Collection<? extends Audience> audienceCollection,
            Object... placeholdersArgs) {

        String content = stringify(logger, message);
        Object[] args = placeholdersArgs != null ? placeholdersArgs : new Object[0];
        Audience targetAudience = resolveTargetAudience(directAudience, audienceCollection, args);

        String processed = applyPipeline(logger, content, targetAudience, args);
        String finalMessage = attachPrefix(logger, processed, level, target, prefixOverride);

        ExternalPlaceholderEngine engine = logger.getExternalPlaceholderEngine();
        TagResolver externalResolver = engine.tagResolver(targetAudience);
        TagResolver resolver = externalResolver == null
                ? TagResolver.standard()
                : TagResolver.resolver(TagResolver.standard(), externalResolver);

        net.kyori.adventure.pointer.Pointered pointered = engine.adventureAudience(targetAudience);
        Component component = pointered != null
                ? logger.getMiniMessage().deserialize(finalMessage, pointered, resolver)
                : logger.getMiniMessage().deserialize(finalMessage, resolver);
        component = engine.applyComponent(targetAudience, component);
        if ((target == LogTarget.CONSOLE || target == LogTarget.BOTH) && logger.isConsoleStripFormatting()) {
            component = Component.text(PlainTextComponentSerializer.plainText().serialize(component));
        }
        return component;
    }

    private static String stringify(LoggerCore logger, Object message) {
        if (message instanceof Component component) {
            return logger.getMiniMessage().serialize(component);
        }
        if (message instanceof Throwable throwable) {
            return formatThrowable(throwable);
        }
        return String.valueOf(message);
    }

    private static Audience resolveTargetAudience(
            @Nullable Audience directAudience,
            @Nullable Collection<? extends Audience> audienceCollection,
            Object[] placeholdersArgs) {

        Audience candidate = null;
        if (placeholdersArgs.length > 0) {
            Object lastArg = placeholdersArgs[placeholdersArgs.length - 1];
            if (lastArg instanceof Audience) {
                candidate = (Audience) lastArg;
            }
        }
        if (directAudience != null) {
            candidate = directAudience;
        }
        if (candidate != null) {
            return candidate;
        }
        return PlaceholderProcessor.pickPrimaryAudience(directAudience, audienceCollection);
    }

    private static String applyPipeline(LoggerCore logger, String messageStr, @Nullable Audience audience, Object[] args) {
        String processed = PlaceholderProcessor.applyPlaceholders(logger.getPlaceholderOwner(), audience, messageStr, args);
        processed = logger.getExternalPlaceholderEngine().apply(audience, processed);
        processed = applyLocalization(logger, processed, audience);
        processed = logger.getExternalPlaceholderEngine().apply(audience, processed);
        processed = PlaceholderProcessor.applyPlaceholders(logger.getPlaceholderOwner(), audience, processed, args);
        return ColorUtils.legacyToMiniMessage(processed);
    }

    private static String attachPrefix(LoggerCore logger, String message, LogLevel level, LogTarget target, @Nullable PrefixMode prefixOverride) {
        PrefixMode mode = prefixOverride != null
                ? prefixOverride
                : (target == LogTarget.CONSOLE || target == LogTarget.BOTH)
                        ? logger.getConsolePrefixMode()
                        : logger.getChatPrefixMode();
        String prefix = buildPrefix(logger, level, mode);

        String combined = prefix.isEmpty() ? message : prefix + " " + message;
        if (!prefix.isEmpty() && shouldUseGradient(logger, target)) {
            String[] colors = logger.resolveColorsForLevel(level, target == LogTarget.CONSOLE);
            String gradientTag = ColorUtils.createGradientTag(colors);
            return "<reset>" + gradientTag + combined + "</gradient>";
        }
        return "<reset>" + combined;
    }

    private static String buildPrefix(LoggerCore logger, LogLevel level, PrefixMode mode) {
        if (mode == PrefixMode.NONE) {
            return "";
        }

        LoggerConfig cfg = logger.getConfig();
        String prefixText = switch (mode) {
            case SHORT -> cfg != null ? cfg.getShortName() : "UAP";
            case FULL -> cfg != null ? cfg.getPluginName() : "UnknownPlugin";
            case CUSTOM -> logger.getCustomPrefix();
            default -> "";
        };

        if (level != LogLevel.INFO) {
            prefixText = prefixText + " " + level.name();
        }

        return "[" + prefixText + "]";
    }

    private static boolean shouldUseGradient(LoggerCore logger, LogTarget target) {
        if (target == LogTarget.CONSOLE) {
            return logger.isConsoleUseGradient();
        }
        if (target == LogTarget.CHAT) {
            LoggerConfig cfg = logger.getConfig();
            return cfg != null && cfg.getPrefix() != null && cfg.getPrefix().isUseGradientChat();
        }
        return true;
    }

    private static String applyLocalization(LoggerCore logger, String messageStr, @Nullable Audience audience) {
        LoggerConfig cfg = logger.getConfig();
        LanguageManager lang = logger.getLanguageManager();
        if (cfg != null && cfg.isAutoLocalization() && lang != null && messageStr != null
                && messageStr.startsWith("@")) {
            String key = messageStr.substring(1);
            String localized = audience != null
                    ? lang.getMessageForAudience(audience, key)
                    : lang.getMessage(key);
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
