package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import dev.ua.theroer.magicutils.commands.TypeParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Type parser for OfflinePlayer arguments.
 */
public class OfflinePlayerTypeParser implements TypeParser<CommandSender, OfflinePlayer> {
    private final PrefixedLogger logger;

    /**
     * Default constructor for OfflinePlayerTypeParser.
     */
    public OfflinePlayerTypeParser() {
        this(null);
    }

    public OfflinePlayerTypeParser(PrefixedLogger logger) {
        this.logger = logger;
    }

    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == OfflinePlayer.class;
    }

    @Override
    @Nullable
    public OfflinePlayer parse(@Nullable String value, @NotNull Class<OfflinePlayer> targetType,
            @NotNull CommandSender sender) {
        if (value == null) {
            return null;
        }

        if ("@sender".equals(value) && sender instanceof Player) {
            if (logger != null) {
                logger.debug("Resolving @sender to: " + sender.getName());
            }
            return (OfflinePlayer) sender;
        }

        // Try online player first
        Player onlinePlayer = Bukkit.getPlayer(value);
        if (onlinePlayer != null) {
            if (logger != null) {
                logger.debug("OfflinePlayer lookup found online player: " + onlinePlayer.getName());
            }
            return onlinePlayer;
        }

        // Then try offline player
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(value);
        if (logger != null) {
            logger.debug("OfflinePlayer lookup for '" + value + "': " +
                    (offlinePlayer.hasPlayedBefore() ? "found" : "not found"));
        }
        return offlinePlayer;
    }

    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull CommandSender sender) {
        List<String> result = new ArrayList<>();

        // Add all online players first
        for (Player player : Bukkit.getOnlinePlayers()) {
            result.add(player.getName());
        }

        // Add offline players
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && !result.contains(player.getName())) {
                result.add(player.getName());
            }
        }

        return result;
    }

    @Override
    public boolean canParseSuggestion(@NotNull String source) {
        return "@offlineplayers".equals(source) ||
                "@allplayers".equals(source);
    }

    @Override
    public int getPriority() {
        return 45; // Slightly lower than Player parser
    }
}
