package dev.ua.theroer.magicutils.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Defines the behavior and rules for a specific slot in a GUI.
 */
public record SlotPolicy(
    boolean editable,
    boolean consumesItem,
    ItemStack placeholder,
    String id,
    Predicate<Player> permission,
    BiPredicate<Player, ItemStack> validator,
    Consumer<ItemStack> onChange
) {
    /**
     * Creates a basic editable slot policy.
     */
    public static SlotPolicy editable(boolean consumes) {
        return new SlotPolicy(true, consumes, null, null, null, null, null);
    }
    
    /**
     * Creates a read-only slot policy.
     */
    public static SlotPolicy readOnly() {
        return new SlotPolicy(false, false, null, null, null, null, null);
    }
    
    /**
     * Builder for creating slot policies.
     */
    public static class Builder {
        private boolean editable = false;
        private boolean consumesItem = true;
        private ItemStack placeholder = null;
        private String id = null;
        private Predicate<Player> permission = null;
        private BiPredicate<Player, ItemStack> validator = null;
        private Consumer<ItemStack> onChange = null;
        
        public Builder editable(boolean editable) {
            this.editable = editable;
            return this;
        }
        
        public Builder consumesItem(boolean consumes) {
            this.consumesItem = consumes;
            return this;
        }
        
        public Builder placeholder(ItemStack placeholder) {
            this.placeholder = placeholder;
            return this;
        }
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder permission(String permission) {
            this.permission = p -> p.hasPermission(permission);
            return this;
        }
        
        public Builder permission(Predicate<Player> permission) {
            this.permission = permission;
            return this;
        }
        
        public Builder validator(BiPredicate<Player, ItemStack> validator) {
            this.validator = validator;
            return this;
        }
        
        public Builder onChange(Consumer<ItemStack> onChange) {
            this.onChange = onChange;
            return this;
        }
        
        public SlotPolicy build() {
            return new SlotPolicy(editable, consumesItem, placeholder, id, permission, validator, onChange);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}