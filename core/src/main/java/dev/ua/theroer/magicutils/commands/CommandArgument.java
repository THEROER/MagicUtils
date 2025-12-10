package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.lang.InternalMessages;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.Getter;

/**
 * Represents a command argument with its properties and permission
 * requirements.
 */
@Getter
public class CommandArgument {
    private final String name;
    private final Class<?> type;
    private final boolean optional;
    private final String defaultValue;
    private final List<String> suggestions;
    private final String permission;
    private final PermissionConditionType permissionCondition;
    private final String[] permissionConditionArgs;
    private final CompareMode compareMode;
    private final String permissionMessage;
    private final String permissionNode;
    private final boolean includeArgumentSegment;
    private final MagicPermissionDefault permissionDefault;
    private final boolean greedy;
    private final boolean permissionDeclared;
    private final boolean senderParameter;
    private final AllowedSender[] allowedSenders;

    private CommandArgument(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.optional = builder.optional;
        this.defaultValue = builder.defaultValue;
        this.suggestions = new ArrayList<>(builder.suggestions);
        this.permission = builder.permission;
        this.permissionCondition = builder.permissionCondition;
        this.permissionConditionArgs = builder.permissionConditionArgs;
        this.compareMode = builder.compareMode;
        this.permissionMessage = builder.permissionMessage;
        this.permissionNode = builder.permissionNode;
        this.includeArgumentSegment = builder.includeArgumentSegment;
        this.permissionDefault = builder.permissionDefault;
        this.greedy = builder.greedy;
        this.permissionDeclared = builder.permissionDeclared;
        this.senderParameter = builder.senderParameter;
        this.allowedSenders = builder.allowedSenders;
    }

    /**
     * Checks if argument has permission.
     * 
     * @return true if permission is set
     */
    public boolean hasPermission() {
        return permissionDeclared;
    }

    /**
     * Whether this argument should be auto-filled with the executing sender.
     *
     * @return true if represents sender
     */
    public boolean isSenderParameter() {
        return senderParameter;
    }

    /**
     * Allowed sender kinds for this parameter.
     *
     * @return array of allowed senders
     */
    public AllowedSender[] getAllowedSenders() {
        return allowedSenders;
    }

    /**
     * Checks if argument has permission condition.
     * 
     * @return true if permission condition is set
     */
    public boolean hasPermissionCondition() {
        return permissionCondition != null && permissionCondition != PermissionConditionType.ALWAYS;
    }

    /**
     * Condition type controlling when to check permission.
     *
     * @return condition type
     */
    public PermissionConditionType getPermissionCondition() {
        return permissionCondition;
    }

    /**
     * Names of arguments participating in the permission condition.
     *
     * @return array of argument names
     */
    public String[] getPermissionConditionArgs() {
        return permissionConditionArgs;
    }

    /**
     * Comparison strategy for permission checks.
     *
     * @return compare mode
     */
    public CompareMode getCompareMode() {
        return compareMode;
    }

    /**
     * Default permission state for this argument.
     *
     * @return permission default
     */
    public MagicPermissionDefault getPermissionDefault() {
        return permissionDefault;
    }

    /**
     * Custom permission node segment (defaults to argument name).
     *
     * @return custom node or null
     */
    public String getPermissionNode() {
        return permissionNode;
    }

    /**
     * Whether permission node should include ".argument." prefix.
     *
     * @return true if ".argument." is included
     */
    public boolean isIncludeArgumentSegment() {
        return includeArgumentSegment;
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
        private PermissionConditionType permissionCondition = PermissionConditionType.ALWAYS;
        private String[] permissionConditionArgs = new String[0];
        private CompareMode compareMode = CompareMode.AUTO;
        private String permissionMessage = InternalMessages.CMD_NO_PERMISSION.get();
        private String permissionNode = null;
        private boolean includeArgumentSegment = true;
        private MagicPermissionDefault permissionDefault = MagicPermissionDefault.OP;
        private boolean greedy = false;
        private boolean permissionDeclared = false;
        private boolean senderParameter = false;
        private AllowedSender[] allowedSenders = new AllowedSender[] { AllowedSender.ANY };

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
            this.permissionDeclared = true;
            return this;
        }

        /**
         * Sets structured permission condition.
         *
         * @param condition condition type
         * @return this builder
         */
        public Builder permissionCondition(PermissionConditionType condition) {
            this.permissionCondition = condition;
            return this;
        }

        /**
         * Sets arguments used in the permission condition.
         *
         * @param args argument names
         * @return this builder
         */
        public Builder permissionConditionArgs(String[] args) {
            this.permissionConditionArgs = args != null ? args : new String[0];
            return this;
        }

        /**
         * Sets comparison strategy for permission checks.
         *
         * @param mode compare mode
         * @return this builder
         */
        public Builder compareMode(CompareMode mode) {
            this.compareMode = mode != null ? mode : CompareMode.AUTO;
            return this;
        }

        /**
         * Overrides generated permission node segment.
         *
         * @param node custom node
         * @return this builder
         */
        public Builder permissionNode(String node) {
            this.permissionNode = node;
            return this;
        }

        /**
         * Controls whether to include ".argument." segment in generated node.
         *
         * @param include true to include, false to omit
         * @return this builder
         */
        public Builder includeArgumentSegment(boolean include) {
            this.includeArgumentSegment = include;
            return this;
        }

        /**
         * Sets default permission state.
         *
         * @param defaultValue default permission
         * @return this builder
         */
        public Builder permissionDefault(MagicPermissionDefault defaultValue) {
            this.permissionDefault = defaultValue != null ? defaultValue : MagicPermissionDefault.OP;
            return this;
        }

        /**
         * Explicitly mark that a permission annotation exists even if value is empty.
         *
         * @return this builder
         */
        public Builder markPermissionDeclared() {
            this.permissionDeclared = true;
            return this;
        }

        /**
         * Sets the permission condition for the argument.
         * 
         * @param condition the permission condition
         * @return this builder
         */
        @Deprecated
        public Builder permissionCondition(String condition) {
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
         * Marks this argument as greedy (consume remaining input).
         *
         * @return this builder
         */
        public Builder greedy() {
            this.greedy = true;
            return this;
        }

        /**
        * Mark this parameter as the executing sender and restrict allowed sender types.
        *
        * @param allowed allowed sender kinds
        * @return this builder
        */
        public Builder sender(AllowedSender[] allowed) {
            this.senderParameter = true;
            this.allowedSenders = allowed != null && allowed.length > 0 ? allowed : new AllowedSender[] { AllowedSender.ANY };
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
