package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.commands.TypeParser;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Type parser for World arguments with @current support.
 */
public class WorldTypeParser implements TypeParser<World> {
    
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
        
        if ("@current".equals(value) && sender instanceof Player) {
            Logger.debug("Resolving @current to world: " + ((Player) sender).getWorld().getName());
            return ((Player) sender).getWorld();
        }
        
        World world = Bukkit.getWorld(value);
        Logger.debug("World lookup for '" + value + "': " + (world != null ? world.getName() : "null"));
        return world;
    }
    
    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull CommandSender sender) {
        List<String> result = new ArrayList<>();
        
        // Add all worlds
        for (World world : Bukkit.getWorlds()) {
            result.add(world.getName());
        }
        
        // Add @current if sender is a player
        if (sender instanceof Player) {
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
}