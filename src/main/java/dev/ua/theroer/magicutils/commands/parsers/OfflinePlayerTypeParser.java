package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.Logger;
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
public class OfflinePlayerTypeParser implements TypeParser<OfflinePlayer> {
    
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
            Logger.debug("Resolving @sender to: " + sender.getName());
            return (OfflinePlayer) sender;
        }
        
        // Try online player first
        Player onlinePlayer = Bukkit.getPlayer(value);
        if (onlinePlayer != null) {
            Logger.debug("OfflinePlayer lookup found online player: " + onlinePlayer.getName());
            return onlinePlayer;
        }
        
        // Then try offline player
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(value);
        Logger.debug("OfflinePlayer lookup for '" + value + "': " + 
                    (offlinePlayer.hasPlayedBefore() ? "found" : "not found"));
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
        
        // Add @sender if sender is a player
        if (sender instanceof Player) {
            result.add("@sender");
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