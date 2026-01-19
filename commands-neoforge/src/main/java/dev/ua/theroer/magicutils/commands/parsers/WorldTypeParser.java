package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import dev.ua.theroer.magicutils.logger.PrefixedLoggerCore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
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
                if (logger != null) {
                    logger.debug().send("Resolving @current to world: " + world.dimension().location());
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
            ResourceLocation id = world.dimension().location();
            if (id.toString().equalsIgnoreCase(normalized)
                    || id.getPath().equalsIgnoreCase(normalized)) {
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
            ResourceLocation id = world.dimension().location();
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

    private ServerPlayer getPlayerSafe(CommandSourceStack sender) {
        try {
            return sender.getPlayer();
        } catch (Exception ignored) {
            return null;
        }
    }
}
