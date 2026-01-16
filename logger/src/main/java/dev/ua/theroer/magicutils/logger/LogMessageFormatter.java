package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.config.logger.LoggerConfig;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.placeholders.MagicPlaceholders;
import dev.ua.theroer.magicutils.placeholders.PlaceholderContext;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.utils.ColorUtils;
import dev.ua.theroer.magicutils.utils.MsgFmt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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

        ExternalPlaceholderEngine engine = logger.getExternalPlaceholderEngine();
        TagResolver externalResolver = null;
        try {
            externalResolver = engine.tagResolver(targetAudience);
        } catch (Throwable error) {
            logger.getPlatform().logger().warn("Failed to resolve external placeholder tags", error);
        }
        String processed = applyPipeline(logger, content, targetAudience, args);
        PrefixRender prefixRender = buildPrefixRender(logger, level, target, prefixOverride);
        String combined = combinePrefix(prefixRender.text(), processed);
        boolean needsMini = prefixRender.useGradient() || hasMiniMessageTags(combined)
                || hasExternalResolver(externalResolver);

        Component component;
        try {
            if (needsMini) {
                String finalMessage = attachPrefix(prefixRender, combined, logger, level, target);
                TagResolver resolver = externalResolver == null
                        ? TagResolver.standard()
                        : TagResolver.resolver(TagResolver.standard(), externalResolver);
                net.kyori.adventure.pointer.Pointered pointered = null;
                try {
                    pointered = engine.adventureAudience(targetAudience);
                } catch (Throwable error) {
                    logger.getPlatform().logger().warn("Failed to resolve external placeholder audience", error);
                }
                component = pointered != null
                        ? logger.getMiniMessage().deserialize(finalMessage, pointered, resolver)
                        : logger.getMiniMessage().deserialize(finalMessage, resolver);
            } else {
                component = Component.text(combined);
                if (component.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
                    component = component.decoration(TextDecoration.ITALIC, false);
                }
            }
        } catch (Throwable error) {
            logger.getPlatform().logger().warn("Failed to deserialize message", error);
            component = Component.text(combined);
        }

        try {
            component = engine.applyComponent(targetAudience, component);
        } catch (Throwable error) {
            logger.getPlatform().logger().warn("Failed to apply external component placeholders", error);
        }
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
        return pickPrimaryAudience(directAudience, audienceCollection);
    }

    private static String applyPipeline(LoggerCore logger, String messageStr, @Nullable Audience audience, Object[] args) {
        String processed = safeApplyLocalization(logger, messageStr, audience);
        processed = applyInlinePlaceholders(processed, args);
        PlaceholderContext context = PlaceholderContext.builder()
                .audience(audience)
                .ownerKey(logger.getPlaceholderOwner())
                .defaultNamespace(logger.getPlaceholderNamespace())
                .build();
        processed = MagicPlaceholders.render(context, processed);
        processed = safeApplyExternal(logger, audience, processed);
        return ColorUtils.legacyToMiniMessage(processed);
    }

    private static Audience pickPrimaryAudience(@Nullable Audience direct,
                                                @Nullable Collection<? extends Audience> collection) {
        if (direct != null) {
            return direct;
        }
        if (collection != null) {
            for (Audience candidate : collection) {
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String applyInlinePlaceholders(String messageStr, Object[] args) {
        if (messageStr == null || messageStr.isEmpty() || args == null || args.length == 0) {
            return messageStr;
        }
        return MsgFmt.apply(messageStr, args);
    }

    private static String safeApplyExternal(LoggerCore logger, @Nullable Audience audience, String input) {
        try {
            return logger.getExternalPlaceholderEngine().apply(audience, input);
        } catch (Throwable error) {
            logger.getPlatform().logger().warn("Failed to apply external placeholders", error);
            return input;
        }
    }

    private static String safeApplyLocalization(LoggerCore logger, String messageStr, @Nullable Audience audience) {
        try {
            return applyLocalization(logger, messageStr, audience);
        } catch (Throwable error) {
            logger.getPlatform().logger().warn("Failed to localize message", error);
            return messageStr;
        }
    }

    private static PrefixRender buildPrefixRender(LoggerCore logger, LogLevel level, LogTarget target,
                                                  @Nullable PrefixMode prefixOverride) {
        PrefixMode mode = prefixOverride != null
                ? prefixOverride
                : (target == LogTarget.CONSOLE || target == LogTarget.BOTH)
                        ? logger.getConsolePrefixMode()
                        : logger.getChatPrefixMode();
        String prefix = buildPrefix(logger, level, mode);
        boolean useGradient = !prefix.isEmpty() && shouldUseGradient(logger, target);
        return new PrefixRender(prefix, useGradient);
    }

    private static String attachPrefix(PrefixRender prefixRender,
                                       String combined,
                                       LoggerCore logger,
                                       LogLevel level,
                                       LogTarget target) {
        if (prefixRender.useGradient()) {
            String[] colors = logger.resolveColorsForLevel(level, target == LogTarget.CONSOLE);
            String gradientTag = ColorUtils.createGradientTag(colors);
            return "<reset>" + gradientTag + combined + "</gradient>";
        }
        return "<reset>" + combined;
    }

    private static String combinePrefix(String prefix, String message) {
        if (prefix == null || prefix.isEmpty()) {
            return message;
        }
        if (message == null || message.isEmpty()) {
            return prefix;
        }
        return prefix + " " + message;
    }

    private static boolean hasMiniMessageTags(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        int open = value.indexOf('<');
        return open >= 0 && value.indexOf('>', open + 1) > open;
    }

    private static boolean hasExternalResolver(TagResolver resolver) {
        return resolver != null && resolver != TagResolver.empty();
    }

    private record PrefixRender(String text, boolean useGradient) {
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
        LanguageManager lang = logger.getLanguageManager();
        if (lang != null && messageStr != null && messageStr.startsWith("@")) {
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
