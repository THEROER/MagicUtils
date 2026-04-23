package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import dev.ua.theroer.magicutils.logger.PrefixedLoggerCore;
import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import java.lang.reflect.Method;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Type parser for ServerLevel arguments with @current support.
 */
public class WorldTypeParser implements TypeParser<CommandSourceStack, ServerLevel> {
    private static final Method IDENTIFIER_GET_PATH = resolveIdentifierPathMethod();
    private static final Method RESOURCE_KEY_IDENTIFIER = resolveResourceKeyIdentifierMethod();
    private final PrefixedLoggerCore logger;

    /** Default constructor. */
    public WorldTypeParser() {
        this(null);
    }

    /**
     * Creates the parser with optional debug logging.
     *
     * @param logger prefixed logger core
     */
    public WorldTypeParser(PrefixedLoggerCore logger) {
        this.logger = logger;
    }

    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == ServerLevel.class;
    }

    @Override
    @Nullable
    public ServerLevel parse(@Nullable String value,
            @NotNull Class<ServerLevel> targetType,
            @NotNull CommandSourceStack sender) {
        if (value == null) {
            return null;
        }

        if ("@current".equals(value)) {
            ServerLevel world = sender.getLevel();
            if (world != null) {
                Object worldId = worldIdentifier(world);
                if (logger != null) {
                    logger.debug().send("Resolving @current to world: " + worldId);
                }
                return world;
            }
        }

        MinecraftServer server = sender.getServer();
        if (server == null) {
            return null;
        }
        String normalized = value.trim();
        for (ServerLevel world : server.getAllLevels()) {
            Object id = worldIdentifier(world);
            String idPath = identifierPath(id);
            if (id != null && (id.toString().equalsIgnoreCase(normalized)
                    || (idPath != null && idPath.equalsIgnoreCase(normalized)))) {
                if (logger != null) {
                    logger.debug().send("World lookup for '" + value + "': " + id);
                }
                return world;
            }
        }
        if (logger != null) {
            logger.debug().send("World lookup for '" + value + "': null");
        }
        return null;
    }

    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull CommandSourceStack sender) {
        List<String> result = new ArrayList<>();
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return result;
        }
        for (ServerLevel world : server.getAllLevels()) {
            String idPath = identifierPath(worldIdentifier(world));
            if (idPath != null && !idPath.isBlank()) {
                result.add(idPath);
            }
        }
        if (getPlayerSafe(sender) != null) {
            result.add("@current");
        }
        return result;
    }

    @Override
    public boolean canParseSuggestion(@NotNull String source) {
        return "@worlds".equals(source) || "@world".equals(source);
    }

    @Override
    public int getPriority() {
        return 50;
    }

    private ServerPlayer getPlayerSafe(CommandSourceStack sender) {
        try {
            return sender.getPlayer();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static @Nullable String identifierPath(@Nullable Object identifier) {
        if (identifier == null || IDENTIFIER_GET_PATH == null) {
            return null;
        }
        return ReflectiveAccess.invoke(IDENTIFIER_GET_PATH, identifier)
                .flatMap(value -> ReflectiveAccess.cast(value, String.class))
                .orElse(null);
    }

    private static @Nullable Method resolveIdentifierPathMethod() {
        Class<?> identifierClass = ReflectiveAccess.loadFirstAvailable(
                "net.minecraft.resources.ResourceLocation",
                "net.minecraft.resources.Identifier"
        ).orElse(null);
        if (identifierClass == null) {
            return null;
        }
        return ReflectiveAccess.publicMethod(identifierClass, "getPath").orElse(null);
    }

    private static @Nullable Object worldIdentifier(@NotNull ServerLevel world) {
        return ReflectiveAccess.invoke(RESOURCE_KEY_IDENTIFIER, world.dimension()).orElse(null);
    }

    private static @Nullable Method resolveResourceKeyIdentifierMethod() {
        Class<?> resourceKeyClass = ReflectiveAccess.loadClass("net.minecraft.resources.ResourceKey").orElse(null);
        if (resourceKeyClass == null) {
            return null;
        }
        Method method = ReflectiveAccess.publicMethod(resourceKeyClass, "location").orElse(null);
        if (method != null) {
            return method;
        }
        return ReflectiveAccess.publicMethod(resourceKeyClass, "identifier").orElse(null);
    }
}
