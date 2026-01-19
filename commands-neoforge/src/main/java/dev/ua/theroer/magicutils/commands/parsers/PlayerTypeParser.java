package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import dev.ua.theroer.magicutils.logger.PrefixedLoggerCore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Type parser for ServerPlayer arguments with @sender support and automatic suggestions.
 */
public class PlayerTypeParser implements TypeParser<CommandSourceStack, ServerPlayer> {
    private final PrefixedLoggerCore logger;

    /** Default constructor. */
    public PlayerTypeParser() {
        this(null);
    }

    /**
     * Creates the parser with optional debug logging.
     *
     * @param logger prefixed logger core
     */
    public PlayerTypeParser(PrefixedLoggerCore logger) {
        this.logger = logger;
    }

    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == ServerPlayer.class;
    }

    @Override
    @Nullable
    public ServerPlayer parse(@Nullable String value,
            @NotNull Class<ServerPlayer> targetType,
            @NotNull CommandSourceStack sender) {
        if (value == null) {
            return null;
        }

        if ("@sender".equals(value)) {
            ServerPlayer player = getPlayerSafe(sender);
            if (logger != null) {
                logger.debug().send("Resolving @sender to: " + (player != null ? player.getName().getString() : "null"));
            }
            return player;
        }

        MinecraftServer server = sender.getServer();
        if (server == null) {
            return null;
        }
        ServerPlayer player = server.getPlayerList().getPlayerByName(value);
        if (logger != null) {
            logger.debug().send("Player lookup for '" + value + "': "
                    + (player != null ? player.getName().getString() : "null"));
        }
        return player;
    }

    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull CommandSourceStack sender) {
        List<String> result = new ArrayList<>();
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return result;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            result.add(player.getName().getString());
        }
        ServerPlayer self = getPlayerSafe(sender);
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
    public List<String> parseSuggestion(@NotNull String source, @NotNull CommandSourceStack sender) {
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

    private ServerPlayer getPlayerSafe(CommandSourceStack sender) {
        try {
            return sender.getPlayer();
        } catch (Exception ignored) {
            return null;
        }
    }
}
