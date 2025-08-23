package dev.ua.theroer.magicutils.commands.parsers.argument;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.commands.ArgumentTypeParser;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parser for Player arguments with @sender support.
 */
public class PlayerArgumentParser implements ArgumentTypeParser<Player> {
    
    /**
     * Create a new PlayerArgumentParser.
     */
    public PlayerArgumentParser() {}
    
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
    public int getPriority() {
        return 50;
    }
}