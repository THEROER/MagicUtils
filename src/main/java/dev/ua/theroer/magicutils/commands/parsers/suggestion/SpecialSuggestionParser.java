package dev.ua.theroer.magicutils.commands.parsers.suggestion;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import dev.ua.theroer.magicutils.commands.SuggestionParser;

/**
 * Parser for special suggestion sources like @players, @worlds, etc.
 */
public class SpecialSuggestionParser implements SuggestionParser {
    
    /**
     * Creates a new SpecialSuggestionParser.
     */
    public SpecialSuggestionParser() {
        // Default constructor
    }

    @Override
    public boolean canParse(@NotNull String source) {
        return source.startsWith("@") && (
                source.equals("@players") ||
                source.equals("@offlineplayers") ||
                source.equals("@allplayers") ||
                source.equals("@worlds") ||
                source.equals("@sender")
        );
    }

    @Override
    @NotNull
    public List<String> parse(@NotNull String source, @NotNull CommandSender sender) {
        switch (source) {
            case "@players":
                return parsePlayers();
            case "@offlineplayers":
                return parseOfflinePlayers();
            case "@allplayers":
                return parseAllPlayers();
            case "@worlds":
                return parseWorlds();
            case "@sender":
                return parseSender(sender);
            default:
                return new ArrayList<>();
        }
    }

    @Override
    public int getPriority() {
        return 100; // High priority for special arguments
    }

    /**
     * Get list of all online players
     */
    private List<String> parsePlayers() {
        List<String> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            result.add(player.getName());
        }
        return result;
    }

    /**
     * Get list of all offline players
     */
    private List<String> parseOfflinePlayers() {
        List<String> result = new ArrayList<>();
        for (@NotNull OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null) {
                result.add(player.getName());
            }
        }
        return result;
    }

    /**
     * Get list of all players (online + offline)
     */
    private List<String> parseAllPlayers() {
        List<String> result = new ArrayList<>();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            result.add(player.getName());
        }
        
        for (@NotNull OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && !result.contains(player.getName())) {
                result.add(player.getName());
            }
        }
        
        return result;
    }

    /**
     * Get list of all worlds
     */
    private List<String> parseWorlds() {
        List<String> result = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            result.add(world.getName());
        }
        return result;
    }

    /**
     * Get sender name
     */
    private List<String> parseSender(CommandSender sender) {
        List<String> result = new ArrayList<>();
        result.add(sender.getName());
        return result;
    }
}