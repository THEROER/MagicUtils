package dev.ua.theroer.magicutils.commands.parsers.argument;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.commands.ArgumentTypeParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Example custom parser for OfflinePlayer arguments.
 * This demonstrates how easy it is to add new argument types.
 */
public class OfflinePlayerArgumentParser implements ArgumentTypeParser<OfflinePlayer> {
    
    /**
     * Create a new OfflinePlayerArgumentParser.
     */
    public OfflinePlayerArgumentParser() {}
    
    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == OfflinePlayer.class;
    }
    
    @Override
    @Nullable
    public OfflinePlayer parse(@Nullable String value, @NotNull Class<OfflinePlayer> targetType, @NotNull CommandSender sender) {
        if (value == null) {
            return null;
        }
        
        if ("@sender".equals(value) && sender instanceof Player) {
            Logger.debug("Resolving @sender to offline player: " + sender.getName());
            return (OfflinePlayer) sender;
        }
        
        Player onlinePlayer = Bukkit.getPlayer(value);
        if (onlinePlayer != null) {
            Logger.debug("Found online player for offline lookup: " + onlinePlayer.getName());
            return onlinePlayer;
        }
        
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(value);
        
        if (offlinePlayer.hasPlayedBefore()) {
            Logger.debug("Found offline player: " + offlinePlayer.getName());
            return offlinePlayer;
        }
        
        Logger.debug("No offline player found for: " + value);
        return null;
    }
    
    @Override
    public int getPriority() {
        return 40;
    }
}