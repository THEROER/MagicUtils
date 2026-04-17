package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Type parser for ServerLevel arguments with @current support.
 */
public class WorldTypeParser implements TypeParser<CommandSourceStack, ServerLevel> {
    private final PrefixedLogger logger;

    /** Default constructor. */
    public WorldTypeParser() {
        this(null);
    }

    /**
     * Creates the parser with optional debug logging.
     *
     * @param logger prefixed logger
     */
    public WorldTypeParser(PrefixedLogger logger) {
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
                String identifier = dimensionId(world);
                if (logger != null) {
                    logger.debug("Resolving @current to world: " + identifier);
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
            String identifier = dimensionId(world);
            String path = identifierPath(identifier);
            if (identifier.equalsIgnoreCase(normalized)
                    || path.equalsIgnoreCase(normalized)) {
                if (logger != null) {
                    logger.debug("World lookup for '" + value + "': " + identifier);
                }
                return world;
            }
        }
        if (logger != null) {
            logger.debug("World lookup for '" + value + "': null");
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
            result.add(identifierPath(dimensionId(world)));
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

    private static String dimensionId(ServerLevel world) {
        if (world == null) {
            return "";
        }
        Object dimension = world.dimension();
        if (dimension == null) {
            return "";
        }
        Method accessor = ReflectiveAccess.publicMethod(dimension.getClass(), "identifier")
                .or(() -> ReflectiveAccess.publicMethod(dimension.getClass(), "location"))
                .orElse(null);
        Object identifier = accessor != null ? ReflectiveAccess.invoke(accessor, dimension).orElse(null) : null;
        return identifier != null ? identifier.toString() : "";
    }

    private static String identifierPath(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "";
        }
        int separator = identifier.indexOf(':');
        return separator >= 0 ? identifier.substring(separator + 1) : identifier;
    }
}
