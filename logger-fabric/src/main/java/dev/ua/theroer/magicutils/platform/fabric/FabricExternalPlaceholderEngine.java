package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.config.logger.LoggerConfig;
import dev.ua.theroer.magicutils.logger.ExternalPlaceholderEngine;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fabric-specific external placeholder engine adapter.
 */
public final class FabricExternalPlaceholderEngine implements ExternalPlaceholderEngine {
    private static final Pattern PB4_PERCENT_PATTERN = Pattern
            .compile("(?<!((?<!(\\\\))\\\\))[%](?<id>[^%]+)[%]");
    private static final Pattern PB4_BRACE_PATTERN = Pattern
            .compile("(?<!((?<!(\\\\))\\\\))[{](?<id>[^{}]+)[}]");

    private static final String ENGINE_MINI = "MINI_PLACEHOLDERS";
    private static final String ENGINE_PB4 = "PB4";

    private final LoggerCore logger;

    private final Method miniAudienceGlobalPlaceholders;

    private final Method pb4ParsePlaceholder;
    private final Method pb4ParseText;
    private final Method pb4ResultValid;
    private final Method pb4ResultText;
    private final Method pb4ContextOfPlayer;
    private final Method pb4ContextOfServer;

    /**
     * Creates the placeholder engine adapter.
     *
     * @param logger logger core
     */
    public FabricExternalPlaceholderEngine(LoggerCore logger) {
        this.logger = logger;
        this.miniAudienceGlobalPlaceholders = resolveMiniAudienceGlobalPlaceholders();

        Method parsePlaceholder = null;
        Method parseText = null;
        Method resultValid = null;
        Method resultText = null;
        Method contextOfPlayer = null;
        Method contextOfServer = null;

        try {
            Class<?> placeholders = ReflectiveAccess.loadClass("eu.pb4.placeholders.api.Placeholders").orElse(null);
            Class<?> placeholderContext = ReflectiveAccess.loadClass("eu.pb4.placeholders.api.PlaceholderContext").orElse(null);
            Class<?> placeholderResult = ReflectiveAccess.loadClass("eu.pb4.placeholders.api.PlaceholderResult").orElse(null);
            Class<?> identifierType = FabricIdentifierBridge.type();
            if (placeholders == null || placeholderContext == null || placeholderResult == null || identifierType == null) {
                throw new IllegalStateException("PB4 classes are unavailable");
            }

            parsePlaceholder = ReflectiveAccess.publicMethod(
                    placeholders,
                    "parsePlaceholder",
                    identifierType,
                    String.class,
                    placeholderContext
            ).orElse(null);
            parseText = ReflectiveAccess.publicMethod(placeholders, "parseText", net.minecraft.network.chat.Component.class, placeholderContext).orElse(null);
            resultValid = ReflectiveAccess.publicMethod(placeholderResult, "isValid").orElse(null);
            resultText = ReflectiveAccess.publicMethod(placeholderResult, "text").orElse(null);

            contextOfPlayer = ReflectiveAccess.publicMethod(placeholderContext, "of", ServerPlayer.class).orElse(null);
            contextOfServer = ReflectiveAccess.publicMethod(placeholderContext, "of", MinecraftServer.class).orElse(null);
        } catch (Throwable ignored) {
            // PB4 placeholder api not available
        }

        this.pb4ParsePlaceholder = parsePlaceholder;
        this.pb4ParseText = parseText;
        this.pb4ResultValid = resultValid;
        this.pb4ResultText = resultText;
        this.pb4ContextOfPlayer = contextOfPlayer;
        this.pb4ContextOfServer = contextOfServer;
    }

    @Override
    public String apply(Audience audience, String text) {
        return applyPb4(audience, text, true);
    }

    @Override
    public TagResolver tagResolver(Audience audience) {
        if (!miniAvailable()) {
            return TagResolver.empty();
        }
        LoggerConfig config = logger.getConfig();
        if (!isEngineEnabled(config, ENGINE_MINI)) {
            return TagResolver.empty();
        }
        try {
            Object resolver = ReflectiveAccess.invoke(miniAudienceGlobalPlaceholders, null).orElse(null);
            return resolver instanceof TagResolver ? (TagResolver) resolver : TagResolver.empty();
        } catch (Throwable ignored) {
            return TagResolver.empty();
        }
    }

    @Override
    public net.kyori.adventure.audience.Audience adventureAudience(Audience audience) {
        if (!miniAvailable()) {
            return null;
        }
        LoggerConfig config = logger.getConfig();
        if (!isEngineEnabled(config, ENGINE_MINI)) {
            return null;
        }
        return createMiniAudience(audience);
    }

    @Override
    public Component applyComponent(Audience audience, Component component) {
        if (!pb4Available()) {
            return component;
        }
        LoggerConfig config = logger.getConfig();
        if (!isEngineEnabled(config, ENGINE_PB4) || isPb4BeforeMini(config)) {
            return component;
        }
        Object context = resolvePb4Context(audience);
        if (context == null) {
            return component;
        }
        try {
            net.minecraft.network.chat.Component nativeText = FabricComponentSerializer.toNative(component);
            Object parsed = ReflectiveAccess.invoke(pb4ParseText, null, nativeText, context).orElse(null);
            if (parsed instanceof net.minecraft.network.chat.Component parsedText) {
                return FabricComponentSerializer.toAdventure(parsedText);
            }
        } catch (Throwable ignored) {
            return component;
        }
        return component;
    }

    private Method resolveMiniAudienceGlobalPlaceholders() {
        try {
            Class<?> miniPlaceholders = ReflectiveAccess.loadClass("io.github.miniplaceholders.api.MiniPlaceholders")
                    .orElse(null);
            return miniPlaceholders != null
                    ? ReflectiveAccess.publicMethod(miniPlaceholders, "audienceGlobalPlaceholders").orElse(null)
                    : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean miniAvailable() {
        return miniAudienceGlobalPlaceholders != null;
    }

    private boolean pb4Available() {
        return pb4ParsePlaceholder != null && pb4ParseText != null && pb4ResultValid != null && pb4ResultText != null;
    }

    private boolean isEngineEnabled(LoggerConfig config, String engine) {
        List<String> order = resolveEngineOrder(config);
        return order.contains(engine);
    }

    private boolean isPb4BeforeMini(LoggerConfig config) {
        List<String> order = resolveEngineOrder(config);
        int pb4 = order.indexOf(ENGINE_PB4);
        if (pb4 == -1) {
            return false;
        }
        int mini = order.indexOf(ENGINE_MINI);
        return mini == -1 || pb4 < mini;
    }

    private List<String> resolveEngineOrder(LoggerConfig config) {
        if (config == null || config.getDefaults() == null) {
            return List.of(ENGINE_MINI, ENGINE_PB4);
        }
        List<String> raw = config.getDefaults().getPlaceholderEngineOrder();
        if (raw == null || raw.isEmpty()) {
            return List.of(ENGINE_MINI, ENGINE_PB4);
        }
        List<String> normalized = new ArrayList<>(raw.size());
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            normalized.add(normalizeEngine(entry));
        }
        return normalized.isEmpty() ? List.of(ENGINE_MINI, ENGINE_PB4) : normalized;
    }

    private String normalizeEngine(String engine) {
        String normalized = engine.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("MINI") || normalized.equals("MINIPLACEHOLDERS")) {
            return ENGINE_MINI;
        }
        if (normalized.equals("PLACEHOLDER_API") || normalized.equals("PLACEHOLDERAPI")) {
            return "PAPI";
        }
        return normalized;
    }

    private Object resolvePb4Context(Audience audience) {
        if (!pb4Available()) {
            return null;
        }
        try {
            ServerPlayer player = extractPlayer(audience);
            if (player != null) {
                return ReflectiveAccess.invoke(pb4ContextOfPlayer, null, player).orElse(null);
            }
            CommandSourceStack source = extractSource(audience);
            if (source != null && source.getServer() != null) {
                return ReflectiveAccess.invoke(pb4ContextOfServer, null, source.getServer()).orElse(null);
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }

    String applyPb4(Audience audience, String text, boolean respectOrder) {
        if (!pb4Available() || text == null || text.isEmpty()) {
            return text;
        }
        LoggerConfig config = logger.getConfig();
        if (!isEngineEnabled(config, ENGINE_PB4)) {
            return text;
        }
        if (respectOrder && !isPb4BeforeMini(config)) {
            return text;
        }
        Object context = resolvePb4Context(audience);
        if (context == null) {
            return text;
        }
        String resolved = replacePb4(text, context, PB4_PERCENT_PATTERN);
        resolved = replacePb4(resolved, context, PB4_BRACE_PATTERN);
        return resolved;
    }

    private ServerPlayer extractPlayer(Audience audience) {
        if (audience instanceof FabricAudience fabricAudience) {
            return fabricAudience.getPlayer();
        }
        if (audience instanceof FabricCommandAudience commandAudience) {
            return commandAudience.getPlayer();
        }
        return null;
    }

    private CommandSourceStack extractSource(Audience audience) {
        if (audience instanceof FabricCommandAudience commandAudience) {
            return commandAudience.getSource();
        }
        return null;
    }

    private String replacePb4(String input, Object context, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer(input.length() + 16);
        while (matcher.find()) {
            String raw = matcher.group("id");
            String replacement = resolvePb4Placeholder(raw, context);
            if (replacement == null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String resolvePb4Placeholder(String raw, Object context) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split(" ", 2);
        Object identifier = FabricIdentifierBridge.parse(parts[0]);
        if (identifier == null) {
            return null;
        }
        String argument = parts.length > 1 ? parts[1] : null;
        try {
            Object result = ReflectiveAccess.invoke(pb4ParsePlaceholder, null, identifier, argument, context).orElse(null);
            if (result == null) {
                return null;
            }
            Object validValue = ReflectiveAccess.invoke(pb4ResultValid, result).orElse(Boolean.FALSE);
            boolean valid = validValue instanceof Boolean flag && flag;
            if (!valid) {
                return null;
            }
            Object text = ReflectiveAccess.invoke(pb4ResultText, result).orElse(null);
            if (!(text instanceof net.minecraft.network.chat.Component textValue)) {
                return null;
            }
            Component component = FabricComponentSerializer.toAdventure(textValue);
            return logger.getMiniMessage().serialize(component);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private net.kyori.adventure.audience.Audience createMiniAudience(Audience audience) {
        ServerPlayer player = extractPlayer(audience);
        if (player == null) {
            return net.kyori.adventure.audience.Audience.empty();
        }
        String name = Objects.toString(player.getName().getString(), "");
        Component displayName = FabricComponentSerializer.toAdventure(player.getDisplayName());

        Pointers pointers = Pointers.builder()
                .withStatic(Identity.UUID, player.getUUID())
                .withStatic(Identity.NAME, name)
                .withStatic(Identity.DISPLAY_NAME, displayName)
                .build();

        return new PlaceholderAudience(pointers);
    }

    private static final class PlaceholderAudience implements net.kyori.adventure.audience.Audience {
        private final Pointers pointers;

        private PlaceholderAudience(Pointers pointers) {
            this.pointers = pointers != null ? pointers : Pointers.empty();
        }

        @Override
        public Pointers pointers() {
            return pointers;
        }
    }
}
