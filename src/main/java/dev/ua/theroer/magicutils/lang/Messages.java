package dev.ua.theroer.magicutils.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Utility class for easy message handling with MiniMessage support.
 * Provides static methods for retrieving, formatting, and sending messages
 * through a LanguageManager instance. Supports MiniMessage formatting
 * and placeholder replacement.
 * 
 * Constructor is private as this is a utility class with only static methods.
 */
public class Messages {
    /**
     * Private constructor - this is a utility class with static methods only.
     */
    private Messages() {
    }
    
    private static LanguageManager languageManager;
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    
    /**
     * Sets the language manager instance.
     * @param manager the language manager
     */
    public static void setLanguageManager(LanguageManager manager) {
        languageManager = manager;
    }
    
    /**
     * Gets the language manager instance.
     * @return the language manager or null
     */
    public static LanguageManager getLanguageManager() {
        return languageManager;
    }
    
    /**
     * Gets raw message string.
     * @param key the message key
     * @return raw message string
     */
    public static String getRaw(String key) {
        return languageManager != null ? languageManager.getMessage(key) : key;
    }
    
    /**
     * Gets raw message string with replacements.
     * @param key the message key
     * @param replacements placeholder -> value pairs
     * @return formatted message string
     */
    public static String getRaw(String key, String... replacements) {
        return languageManager != null ? languageManager.getMessage(key, replacements) : key;
    }
    
    /**
     * Gets raw message string with map replacements.
     * @param key the message key
     * @param placeholders map of placeholder -> value
     * @return formatted message string
     */
    public static String getRaw(String key, Map<String, String> placeholders) {
        return languageManager != null ? languageManager.getMessage(key, placeholders) : key;
    }
    
    /**
     * Gets message as Component with MiniMessage parsing.
     * @param key the message key
     * @return Component message
     */
    public static Component get(String key) {
        String raw = getRaw(key);
        return miniMessage.deserialize(raw);
    }
    
    /**
     * Gets message as Component with replacements.
     * @param key the message key
     * @param replacements placeholder -> value pairs
     * @return Component message
     */
    public static Component get(String key, String... replacements) {
        String raw = getRaw(key, replacements);
        return miniMessage.deserialize(raw);
    }
    
    /**
     * Gets message as Component with map replacements.
     * @param key the message key
     * @param placeholders map of placeholder -> value
     * @return Component message
     */
    public static Component get(String key, Map<String, String> placeholders) {
        String raw = getRaw(key, placeholders);
        return miniMessage.deserialize(raw);
    }
    
    /**
     * Sends message to command sender.
     * @param sender the command sender
     * @param key the message key
     */
    public static void send(CommandSender sender, String key) {
        sender.sendMessage(get(key));
    }
    
    /**
     * Sends message to command sender with replacements.
     * @param sender the command sender
     * @param key the message key
     * @param replacements placeholder -> value pairs
     */
    public static void send(CommandSender sender, String key, String... replacements) {
        sender.sendMessage(get(key, replacements));
    }
    
    /**
     * Sends message to command sender with map replacements.
     * @param sender the command sender
     * @param key the message key
     * @param placeholders map of placeholder -> value
     */
    public static void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(get(key, placeholders));
    }
    
    /**
     * Sends message to player.
     * @param player the player
     * @param key the message key
     */
    public static void send(Player player, String key) {
        player.sendMessage(get(key));
    }
    
    /**
     * Sends message to player with replacements.
     * @param player the player
     * @param key the message key
     * @param replacements placeholder -> value pairs
     */
    public static void send(Player player, String key, String... replacements) {
        player.sendMessage(get(key, replacements));
    }
    
    /**
     * Sends message to player with map replacements.
     * @param player the player
     * @param key the message key
     * @param placeholders map of placeholder -> value
     */
    public static void send(Player player, String key, Map<String, String> placeholders) {
        player.sendMessage(get(key, placeholders));
    }
    
    /**
     * Checks if message exists.
     * @param key the message key
     * @return true if message exists
     */
    public static boolean exists(String key) {
        return languageManager != null && languageManager.hasMessage(key);
    }
    
    /**
     * Gets current language code.
     * @return current language code
     */
    public static String getCurrentLanguage() {
        return languageManager != null ? languageManager.getCurrentLanguage() : "en";
    }
}