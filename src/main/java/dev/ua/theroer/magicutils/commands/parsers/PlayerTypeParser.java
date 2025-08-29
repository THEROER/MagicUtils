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
 * Type parser for Player arguments with @sender support and automatic suggestions.
 */
public class PlayerTypeParser implements TypeParser<Player> {
    
    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == Player.class;
    }
    
    @Override
    @Nullable
    public Player parse(@Nullable String value, @NotNull Class<Player> targetType, @NotNull CommandSender sender) {
        if (value == null) {
            return null;
        }
        
        if ("@sender".equals(value) && sender instanceof Player) {
            Logger.debug("Resolving @sender to: " + sender.getName());
            return (Player) sender;
        }
        
        Player player = Bukkit.getPlayer(value);
        Logger.debug("Player lookup for '" + value + "': " + (player != null ? player.getName() : "null"));
        return player;
    }
    
    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull CommandSender sender) {
        List<String> result = new ArrayList<>();
        
        // Add all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            result.add(player.getName());
        }
        
        // Add @sender if sender is a player
        if (sender instanceof Player) {
            result.add("@sender");
        }
        
        return result;
    }
    
    @Override
    public boolean canParseSuggestion(@NotNull String source) {
        return "@players".equals(source) || 
               "@player".equals(source) ||
               "@offlineplayers".equals(source) ||
               "@allplayers".equals(source);
    }
    
    @Override
    @NotNull
    public List<String> parseSuggestion(@NotNull String source, @NotNull CommandSender sender) {
        switch (source) {
            case "@players":
            case "@player":
                return getSuggestions(sender);
                
            case "@offlineplayers":
                List<String> offlineResult = new ArrayList<>();
                for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                    if (player.getName() != null) {
                        offlineResult.add(player.getName());
                    }
                }
                return offlineResult;
                
            case "@allplayers":
                List<String> allResult = new ArrayList<>();
                // Add online players
                for (Player player : Bukkit.getOnlinePlayers()) {
                    allResult.add(player.getName());
                }
                // Add offline players
                for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                    if (player.getName() != null && !allResult.contains(player.getName())) {
                        allResult.add(player.getName());
                    }
                }
                // Add @sender if applicable
                if (sender instanceof Player) {
                    allResult.add("@sender");
                }
                return allResult;
                
            default:
                return getSuggestions(sender);
        }
    }
    
    @Override
    public int getPriority() {
        return 50;
    }
}