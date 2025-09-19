package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.lang.InternalMessages;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a command argument with its properties and permission
 * requirements.
 */
public class CommandArgument {
    private final String name;
    private final Class<?> type;
    private final boolean optional;
    private final String defaultValue;
    private final List<String> suggestions;
    private final String permission;
    private final String permissionCondition;
    private final String permissionMessage;

    private CommandArgument(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.optional = builder.optional;
        this.defaultValue = builder.defaultValue;
        this.suggestions = new ArrayList<>(builder.suggestions);
        this.permission = builder.permission;
        this.permissionCondition = builder.permissionCondition;
        this.permissionMessage = builder.permissionMessage;
    }

    /**
     * Gets the argument name.
     * 
     * @return the argument name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the argument type.
     * 
     * @return the argument type
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * Checks if the argument is optional.
     * 
     * @return true if optional
     */
    public boolean isOptional() {
        return optional;
    }

    /**
     * Gets the default value for the argument.
     * 
     * @return the default value
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Gets the list of suggestions for the argument.
     * 
     * @return the suggestions list
     */
    public List<String> getSuggestions() {
        return new ArrayList<>(suggestions);
    }

    /**
     * Gets the required permission for the argument.
     * 
     * @return the permission string
     */
    public String getPermission() {
        return permission;
    }

    /**
     * Gets the permission condition for the argument.
     * 
     * @return the permission condition
     */
    public String getPermissionCondition() {
        return permissionCondition;
    }

    /**
     * Gets the permission denied message for the argument.
     * 
     * @return the permission message
     */
    public String getPermissionMessage() {
        return permissionMessage;
    }

    /**
     * Checks if argument has permission.
     * 
     * @return true if permission is set
     */
    public boolean hasPermission() {
        return permission != null && !permission.isEmpty();
    }

    /**
     * Checks if argument has permission condition.
     * 
     * @return true if permission condition is set
     */
    public boolean hasPermissionCondition() {
        return permissionCondition != null && !permissionCondition.isEmpty();
    }

    /**
     * Checks if argument contains specific suggestion.
     * 
     * @param suggestion the suggestion to check
     * @return true if suggestion exists
     */
    public boolean hasSuggestion(String suggestion) {
        return suggestions.contains(suggestion);
    }

    /**
     * Creates a builder for CommandArgument.
     * 
     * @param name the argument name
     * @param type the argument type
     * @return a new Builder instance
     */
    public static Builder builder(String name, Class<?> type) {
        return new Builder(name, type);
    }

    /**
     * Builder for CommandArgument.
     */
    public static class Builder {
        private final String name;
        private final Class<?> type;
        private boolean optional = false;
        private String defaultValue = null;
        private final List<String> suggestions = new ArrayList<>();
        private String permission = null;
        private String permissionCondition = null;
        private String permissionMessage = InternalMessages.CMD_NO_PERMISSION.get();

        /**
         * Constructs a new Builder for CommandArgument.
         * 
         * @param name the argument name
         * @param type the argument type
         */
        public Builder(String name, Class<?> type) {
            this.name = name;
            this.type = type;
        }

        /**
         * Sets the argument as optional.
         * 
         * @return this builder
         */
        public Builder optional() {
            this.optional = true;
            return this;
        }

        /**
         * Sets the default value for the argument.
         * 
         * @param defaultValue the default value
         * @return this builder
         */
        public Builder defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            this.optional = true;
            return this;
        }

        /**
         * Adds suggestions for the argument.
         * 
         * @param suggestions the suggestions
         * @return this builder
         */
        public Builder suggestions(String... suggestions) {
            this.suggestions.addAll(Arrays.asList(suggestions));
            return this;
        }

        /**
         * Adds suggestions for the argument.
         * 
         * @param suggestions the suggestions list
         * @return this builder
         */
        public Builder suggestions(List<String> suggestions) {
            this.suggestions.addAll(suggestions);
            return this;
        }

        /**
         * Sets the required permission for the argument.
         * 
         * @param permission the permission string
         * @return this builder
         */
        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }

        /**
         * Sets the permission condition for the argument.
         * 
         * @param condition the permission condition
         * @return this builder
         */
        public Builder permissionCondition(String condition) {
            this.permissionCondition = condition;
            return this;
        }

        /**
         * Sets the permission denied message for the argument.
         * 
         * @param message the permission message
         * @return this builder
         */
        public Builder permissionMessage(String message) {
            this.permissionMessage = message;
            return this;
        }

        /**
         * Builds the CommandArgument instance.
         * 
         * @return the CommandArgument
         */
        public CommandArgument build() {
            return new CommandArgument(this);
        }
    }

    @Override
    public String toString() {
        return "CommandArgument{" +
                "name='" + name + '\'' +
                ", type=" + type.getSimpleName() +
                ", optional=" + optional +
                ", defaultValue='" + defaultValue + '\'' +
                ", suggestions=" + suggestions +
                ", permission='" + permission + '\'' +
                ", permissionCondition='" + permissionCondition + '\'' +
                '}';
    }
}
