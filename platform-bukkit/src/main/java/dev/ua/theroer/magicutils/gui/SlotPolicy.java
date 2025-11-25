package dev.ua.theroer.magicutils.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Defines the behavior and rules for a specific slot in a GUI.
 * 
 * @param editable     whether the slot can be edited by players
 * @param consumesItem whether items placed in this slot are consumed
 * @param placeholder  the placeholder item to display when slot is empty
 * @param id           unique identifier for this slot
 * @param permission   predicate to check if player has permission to interact
 * @param validator    predicate to validate items placed in this slot
 * @param onChange     callback when slot contents change
 */
public record SlotPolicy(
        boolean editable,
        boolean consumesItem,
        ItemStack placeholder,
        String id,
        Predicate<Player> permission,
        BiPredicate<Player, ItemStack> validator,
        Consumer<ItemStack> onChange) {
    /**
     * Creates a basic editable slot policy.
     * 
     * @param consumes whether the slot consumes items placed in it
     * @return a new editable SlotPolicy
     */
    public static SlotPolicy editable(boolean consumes) {
        return new SlotPolicy(true, consumes, null, null, null, null, null);
    }

    /**
     * Creates a read-only slot policy.
     * 
     * @return a new read-only SlotPolicy
     */
    public static SlotPolicy readOnly() {
        return new SlotPolicy(false, false, null, null, null, null, null);
    }

    /**
     * Builder for creating slot policies.
     */
    public static class Builder {
        /**
         * Creates a new Builder instance.
         */
        public Builder() {
        }

        private boolean editable = false;
        private boolean consumesItem = true;
        private ItemStack placeholder = null;
        private String id = null;
        private Predicate<Player> permission = null;
        private BiPredicate<Player, ItemStack> validator = null;
        private Consumer<ItemStack> onChange = null;

        /**
         * Sets whether the slot is editable.
         * 
         * @param editable whether the slot can be edited
         * @return this builder for chaining
         */
        public Builder editable(boolean editable) {
            this.editable = editable;
            return this;
        }

        /**
         * Sets whether the slot consumes items.
         * 
         * @param consumes whether items are consumed
         * @return this builder for chaining
         */
        public Builder consumesItem(boolean consumes) {
            this.consumesItem = consumes;
            return this;
        }

        /**
         * Sets the placeholder item.
         * 
         * @param placeholder the placeholder item
         * @return this builder for chaining
         */
        public Builder placeholder(ItemStack placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        /**
         * Sets the slot ID.
         * 
         * @param id the unique identifier
         * @return this builder for chaining
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the required permission.
         * 
         * @param permission the permission string
         * @return this builder for chaining
         */
        public Builder permission(String permission) {
            this.permission = p -> p.hasPermission(permission);
            return this;
        }

        /**
         * Sets the permission predicate.
         * 
         * @param permission the permission check predicate
         * @return this builder for chaining
         */
        public Builder permission(Predicate<Player> permission) {
            this.permission = permission;
            return this;
        }

        /**
         * Sets the item validator.
         * 
         * @param validator the validation predicate
         * @return this builder for chaining
         */
        public Builder validator(BiPredicate<Player, ItemStack> validator) {
            this.validator = validator;
            return this;
        }

        /**
         * Sets the change callback.
         * 
         * @param onChange the callback for changes
         * @return this builder for chaining
         */
        public Builder onChange(Consumer<ItemStack> onChange) {
            this.onChange = onChange;
            return this;
        }

        /**
         * Builds the SlotPolicy.
         * 
         * @return the configured SlotPolicy
         */
        public SlotPolicy build() {
            return new SlotPolicy(editable, consumesItem, placeholder, id, permission, validator, onChange);
        }
    }

    /**
     * Creates a new Builder.
     * 
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}