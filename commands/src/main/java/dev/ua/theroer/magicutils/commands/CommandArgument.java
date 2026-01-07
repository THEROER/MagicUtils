package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.lang.InternalMessages;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.AccessLevel;
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
    @Getter(AccessLevel.NONE)
    private final List<String> suggestions;
    private final String permission;
    @Getter
    private final PermissionConditionType permissionCondition;
    @Getter(AccessLevel.NONE)
    private final String[] permissionConditionArgs;
    @Getter
    private final CompareMode compareMode;
    private final String permissionMessage;
    @Getter
    private final String permissionNode;
    private final boolean includeArgumentSegment;
    @Getter
    private final MagicPermissionDefault permissionDefault;
    private final boolean greedy;
    private final boolean permissionDeclared;
    private final boolean senderParameter;
    @Getter(AccessLevel.NONE)
    private final AllowedSender[] allowedSenders;
    @Getter(AccessLevel.NONE)
    private final List<String> optionShortNames;
    @Getter(AccessLevel.NONE)
    private final List<String> optionLongNames;
    private final boolean flag;
    private final List<String> contextArgs;

    private CommandArgument(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.optional = builder.optional;
        this.defaultValue = builder.defaultValue;
        this.suggestions = new ArrayList<>(builder.suggestions);
        this.permission = builder.permission;
        this.permissionCondition = builder.permissionCondition;
        this.permissionConditionArgs = builder.permissionConditionArgs != null
                ? builder.permissionConditionArgs.clone()
                : new String[0];
        this.compareMode = builder.compareMode;
        this.permissionMessage = builder.permissionMessage;
        this.permissionNode = builder.permissionNode;
        this.includeArgumentSegment = builder.includeArgumentSegment;
        this.permissionDefault = builder.permissionDefault;
        this.greedy = builder.greedy;
        this.permissionDeclared = builder.permissionDeclared;
        this.senderParameter = builder.senderParameter;
        this.allowedSenders = builder.allowedSenders != null
                ? builder.allowedSenders.clone()
                : new AllowedSender[] { AllowedSender.ANY };
        this.optionShortNames = new ArrayList<>(builder.optionShortNames);
        this.optionLongNames = new ArrayList<>(builder.optionLongNames);
        this.flag = builder.flag;
        this.contextArgs = new ArrayList<>(builder.contextArgs);
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
     * Allowed sender kinds for this parameter.
     *
     * @return array of allowed senders
     */
    public AllowedSender[] getAllowedSenders() {
        return allowedSenders.clone();
    }

    /**
     * Returns true if this argument supports named options.
     *
     * @return true when option names are defined
     */
    public boolean isOption() {
        return !optionShortNames.isEmpty() || !optionLongNames.isEmpty();
    }

    /**
     * Returns true if this option is a flag (no value required).
     *
     * @return true if flag
     */
    public boolean isFlag() {
        return flag;
    }

    /**
     * Short option names without "-" prefix.
     *
     * @return list of short names
     */
    public List<String> getShortOptionNames() {
        return new ArrayList<>(optionShortNames);
    }

    /**
     * Long option names without "--" prefix.
     *
     * @return list of long names
     */
    public List<String> getLongOptionNames() {
        return new ArrayList<>(optionLongNames);
    }

    /**
     * Returns the names of arguments whose parsed values should be available when generating suggestions.
     *
     * @return copy of the list of context argument names
     */
    public List<String> getContextArgs() {
        return new ArrayList<>(contextArgs);
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
     * Names of arguments participating in the permission condition.
     *
     * @return array of argument names
     */
    public String[] getPermissionConditionArgs() {
        return permissionConditionArgs.clone();
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
     * Available suggestions for this argument.
     *
     * @return copy of suggestion list
     */
    public List<String> getSuggestions() {
        return new ArrayList<>(suggestions);
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
        private final List<String> optionShortNames = new ArrayList<>();
        private final List<String> optionLongNames = new ArrayList<>();
        private boolean flag = false;
        private final List<String> contextArgs = new ArrayList<>();

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
            this.permissionConditionArgs = args != null ? args.clone() : new String[0];
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
            this.allowedSenders = allowed != null && allowed.length > 0 ? allowed.clone()
                    : new AllowedSender[] { AllowedSender.ANY };
            return this;
        }

        /**
         * Defines named options for this argument.
         *
         * @param shortNames short option names without "-" prefix
         * @param longNames long option names without "--" prefix
         * @return this builder
         */
        public Builder option(String[] shortNames, String[] longNames) {
            return option(shortNames, longNames, false);
        }

        /**
         * Defines named options for this argument.
         *
         * @param shortNames short option names without "-" prefix
         * @param longNames long option names without "--" prefix
         * @param flag whether this option is a flag
         * @return this builder
         */
        public Builder option(String[] shortNames, String[] longNames, boolean flag) {
            addOptionNames(this.optionShortNames, shortNames);
            addOptionNames(this.optionLongNames, longNames);
            this.flag = this.flag || flag;
            if (flag) {
                this.optional = true;
                if (this.defaultValue == null) {
                    this.defaultValue = "false";
                }
            }
            return this;
        }

        private static void addOptionNames(List<String> target, String[] names) {
            if (names == null || names.length == 0) {
                return;
            }
            for (String name : names) {
                if (name == null) {
                    continue;
                }
                String trimmed = normalizeOptionName(name);
                if (!trimmed.isEmpty() && !target.contains(trimmed)) {
                    target.add(trimmed);
                }
            }
        }

        private static String normalizeOptionName(String name) {
            String trimmed = name != null ? name.trim() : "";
            while (trimmed.startsWith("-")) {
                trimmed = trimmed.substring(1);
            }
            return trimmed;
        }

        /**
         * Adds names of arguments whose parsed values should be available as context for suggestions.
         *
         * @param contextArgs the names of context arguments
         * @return this builder
         */
        public Builder contextArgs(String... contextArgs) {
            if (contextArgs != null) {
                this.contextArgs.addAll(Arrays.asList(contextArgs));
            }
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
                ", options=" + optionShortNames + "/" + optionLongNames +
                ", flag=" + flag +
                ", contextArgs=" + contextArgs +
                '}';
    }
}
