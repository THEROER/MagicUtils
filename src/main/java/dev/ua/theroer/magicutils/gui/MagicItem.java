package dev.ua.theroer.magicutils.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for creating ItemStacks with MiniMessage support.
 */
public final class MagicItem {
    private final ItemStack stack;
    private final ItemMeta meta;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    
    private MagicItem(Material material) {
        this.stack = new ItemStack(material);
        this.meta = stack.getItemMeta();
    }
    
    /**
     * Create a new MagicItem builder.
     * @param material the material
     * @return new builder instance
     */
    public static MagicItem of(Material material) {
        return new MagicItem(material);
    }
    
    /**
     * Set the display name using MiniMessage format.
     * @param name the name (supports MiniMessage)
     * @return this builder
     */
    public MagicItem name(String name) {
        if (name != null) {
            meta.displayName(miniMessage.deserialize(name).decoration(TextDecoration.ITALIC, false));
        }
        return this;
    }
    
    /**
     * Set the display name using Component.
     * @param name the name component
     * @return this builder
     */
    public MagicItem name(Component name) {
        if (name != null) {
            meta.displayName(name);
        }
        return this;
    }
    
    /**
     * Set the lore using MiniMessage format.
     * @param lore the lore lines (support MiniMessage)
     * @return this builder
     */
    public MagicItem lore(List<String> lore) {
        if (lore != null && !lore.isEmpty()) {
            List<Component> loreComponents = lore.stream()
                    .map(line -> miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false))
                    .toList();
            meta.lore(loreComponents);
        }
        return this;
    }
    
    /**
     * Set the lore using MiniMessage format.
     * @param lore the lore lines (support MiniMessage)
     * @return this builder
     */
    public MagicItem lore(String... lore) {
        return lore(Arrays.asList(lore));
    }
    
    /**
     * Set the lore using Components.
     * @param lore the lore components
     * @return this builder
     */
    public MagicItem loreComponents(List<Component> lore) {
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore);
        }
        return this;
    }
    
    /**
     * Add to existing lore using MiniMessage format.
     * @param lines the lines to add
     * @return this builder
     */
    public MagicItem addLore(String... lines) {
        List<Component> currentLore = meta.lore();
        if (currentLore == null) {
            currentLore = new ArrayList<>();
        } else {
            currentLore = new ArrayList<>(currentLore);
        }
        
        for (String line : lines) {
            currentLore.add(miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        
        meta.lore(currentLore);
        return this;
    }
    
    /**
     * Set the amount.
     * @param amount the amount
     * @return this builder
     */
    public MagicItem amount(int amount) {
        stack.setAmount(amount);
        return this;
    }
    
    /**
     * Add an enchantment.
     * @param enchantment the enchantment
     * @param level the level
     * @return this builder
     */
    public MagicItem enchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        return this;
    }
    
    /**
     * Add item flags.
     * @param flags the flags to add
     * @return this builder
     */
    public MagicItem flags(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }
    
    /**
     * Hide all attributes.
     * @return this builder
     */
    public MagicItem hideAttributes() {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        return this;
    }
    
    /**
     * Hide enchantments.
     * @return this builder
     */
    public MagicItem hideEnchants() {
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }
    
    /**
     * Make the item glow (enchantment effect without showing enchantments).
     * @return this builder
     */
    public MagicItem glow() {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }
    
    /**
     * Set custom model data.
     * @param data the custom model data
     * @return this builder
     */
    public MagicItem customModelData(int data) {
        meta.setCustomModelData(data);
        return this;
    }
    
    /**
     * Set persistent data container value.
     * @param key the namespaced key
     * @param type the data type
     * @param value the value
     * @param <T> the primitive type
     * @param <Z> the complex type
     * @return this builder
     */
    public <T, Z> MagicItem pdc(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        meta.getPersistentDataContainer().set(key, type, value);
        return this;
    }
    
    /**
     * Set persistent data container string value.
     * @param key the namespaced key
     * @param value the string value
     * @return this builder
     */
    public MagicItem pdc(NamespacedKey key, String value) {
        return pdc(key, PersistentDataType.STRING, value);
    }
    
    /**
     * Set persistent data container integer value.
     * @param key the namespaced key
     * @param value the integer value
     * @return this builder
     */
    public MagicItem pdc(NamespacedKey key, int value) {
        return pdc(key, PersistentDataType.INTEGER, value);
    }
    
    /**
     * Make the item unbreakable.
     * @return this builder
     */
    public MagicItem unbreakable() {
        meta.setUnbreakable(true);
        return this;
    }
    
    /**
     * Build the ItemStack.
     * @return the built ItemStack
     */
    public ItemStack build() {
        stack.setItemMeta(meta);
        return stack;
    }
    
    /**
     * Build and clone the ItemStack.
     * @return a cloned ItemStack
     */
    public ItemStack buildClone() {
        stack.setItemMeta(meta);
        return stack.clone();
    }
}