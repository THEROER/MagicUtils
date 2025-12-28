package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Type parser for ServerWorld arguments with @current support.
 */
public class WorldTypeParser implements TypeParser<ServerCommandSource, ServerWorld> {
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
        return type == ServerWorld.class;
    }

    @Override
    @Nullable
    public ServerWorld parse(@Nullable String value,
            @NotNull Class<ServerWorld> targetType,
            @NotNull ServerCommandSource sender) {
        if (value == null) {
            return null;
        }

        if ("@current".equals(value)) {
            ServerWorld world = sender.getWorld();
            if (world != null) {
                if (logger != null) {
                    logger.debug("Resolving @current to world: " + world.getRegistryKey().getValue());
                }
                return world;
            }
        }

        MinecraftServer server = sender.getServer();
        if (server == null) {
            return null;
        }
        String normalized = value.trim();
        for (ServerWorld world : server.getWorlds()) {
            Identifier id = world.getRegistryKey().getValue();
            if (id.toString().equalsIgnoreCase(normalized)
                    || id.getPath().equalsIgnoreCase(normalized)) {
                if (logger != null) {
                    logger.debug("World lookup for '" + value + "': " + id);
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
    public List<String> getSuggestions(@NotNull ServerCommandSource sender) {
        List<String> result = new ArrayList<>();
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return result;
        }
        for (ServerWorld world : server.getWorlds()) {
            Identifier id = world.getRegistryKey().getValue();
            result.add(id.getPath());
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

    private ServerPlayerEntity getPlayerSafe(ServerCommandSource sender) {
        try {
            return sender.getPlayer();
        } catch (Exception ignored) {
            return null;
        }
    }
}
