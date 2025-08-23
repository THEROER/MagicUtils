package dev.ua.theroer.magicutils.commands.parsers.argument;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.commands.ArgumentTypeParser;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parser for World arguments with @sender support.
 */
public class WorldArgumentParser implements ArgumentTypeParser<World> {
    
    /**
     * Create a new WorldArgumentParser.
     */
    public WorldArgumentParser() {}
    
    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == World.class;
    }
    
    @Override
    @Nullable
    public World parse(@Nullable String value, @NotNull Class<World> targetType, @NotNull CommandSender sender) {
        if (value == null) {
            return null;
        }
        
        if ("@sender".equals(value) && sender instanceof Player) {
            World world = ((Player) sender).getWorld();
            Logger.debug("Resolving @sender world to: " + world.getName());
            return world;
        }
        
        World world = Bukkit.getWorld(value);
        Logger.debug("World lookup for '" + value + "': " + (world != null ? world.getName() : "null"));
        return world;
    }
    
    @Override
    public int getPriority() {
        return 50;
    }
}