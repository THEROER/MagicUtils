package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Type parser for ServerPlayerEntity arguments with @sender support and automatic suggestions.
 */
public class PlayerTypeParser implements TypeParser<ServerCommandSource, ServerPlayerEntity> {
    private final PrefixedLogger logger;

    /** Default constructor. */
    public PlayerTypeParser() {
        this(null);
    }

    /**
     * Creates the parser with optional debug logging.
     *
     * @param logger prefixed logger
     */
    public PlayerTypeParser(PrefixedLogger logger) {
        this.logger = logger;
    }

    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == ServerPlayerEntity.class;
    }

    @Override
    @Nullable
    public ServerPlayerEntity parse(@Nullable String value,
            @NotNull Class<ServerPlayerEntity> targetType,
            @NotNull ServerCommandSource sender) {
        if (value == null) {
            return null;
        }

        if ("@sender".equals(value)) {
            ServerPlayerEntity player = getPlayerSafe(sender);
            if (logger != null) {
                logger.debug("Resolving @sender to: " + (player != null ? player.getName().getString() : "null"));
            }
            return player;
        }

        MinecraftServer server = sender.getServer();
        if (server == null) {
            return null;
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(value);
        if (logger != null) {
            logger.debug("Player lookup for '" + value + "': " + (player != null ? player.getName().getString() : "null"));
        }
        return player;
    }

    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull ServerCommandSource sender) {
        List<String> result = new ArrayList<>();
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return result;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            result.add(player.getName().getString());
        }
        ServerPlayerEntity self = getPlayerSafe(sender);
        if (self != null && !result.contains(self.getName().getString())) {
            result.add(self.getName().getString());
        }
        return result;
    }

    @Override
    public boolean canParseSuggestion(@NotNull String source) {
        return "@players".equals(source)
                || "@player".equals(source)
                || "@offlineplayers".equals(source)
                || "@allplayers".equals(source);
    }

    @Override
    @NotNull
    public List<String> parseSuggestion(@NotNull String source, @NotNull ServerCommandSource sender) {
        switch (source) {
            case "@players":
            case "@player":
            case "@allplayers":
                return getSuggestions(sender);
            case "@offlineplayers":
            default:
                return new ArrayList<>();
        }
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
