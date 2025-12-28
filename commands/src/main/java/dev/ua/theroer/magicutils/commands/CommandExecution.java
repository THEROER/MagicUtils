package dev.ua.theroer.magicutils.commands;

import java.util.Collections;
import java.util.List;

/**
 * Execution context for builder-defined commands.
 *
 * @param <S> sender type
 */
public final class CommandExecution<S> {
    private final MagicCommand command;
    private final String commandName;
    private final String subCommandName;
    private final S sender;
    private final List<String> rawArgs;
    private final List<CommandArgument> arguments;
    private final Object[] parsedArgs;

    CommandExecution(MagicCommand command,
                     String commandName,
                     String subCommandName,
                     S sender,
                     List<String> rawArgs,
                     List<CommandArgument> arguments,
                     Object[] parsedArgs) {
        this.command = command;
        this.commandName = commandName;
        this.subCommandName = subCommandName;
        this.sender = sender;
        this.rawArgs = rawArgs != null ? rawArgs : List.of();
        this.arguments = arguments != null ? arguments : List.of();
        this.parsedArgs = parsedArgs != null ? parsedArgs : new Object[0];
    }

    /**
     * Returns the command instance.
     *
     * @return command instance
     */
    public MagicCommand command() {
        return command;
    }

    /**
     * Returns the root command name.
     *
     * @return command name
     */
    public String commandName() {
        return commandName;
    }

    /**
     * Returns the subcommand name if present.
     *
     * @return subcommand name or null
     */
    public String subCommandName() {
        return subCommandName;
    }

    /**
     * Returns the sender instance.
     *
     * @return sender
     */
    public S sender() {
        return sender;
    }

    /**
     * Returns raw arguments as an immutable list.
     *
     * @return raw argument list
     */
    public List<String> rawArgs() {
        return Collections.unmodifiableList(rawArgs);
    }

    /**
     * Returns argument descriptors as an immutable list.
     *
     * @return argument descriptors
     */
    public List<CommandArgument> arguments() {
        return Collections.unmodifiableList(arguments);
    }

    /**
     * Returns parsed arguments.
     *
     * @return parsed arguments array
     */
    public Object[] parsedArgs() {
        return parsedArgs.clone();
    }

    /**
     * Returns the parsed argument at the index.
     *
     * @param index argument index
     * @return argument value or null
     */
    public Object arg(int index) {
        if (index < 0 || index >= parsedArgs.length) {
            return null;
        }
        return parsedArgs[index];
    }

    /**
     * Returns the parsed argument at the index with type checking.
     *
     * @param index argument index
     * @param type expected type
     * @param <T> type parameter
     * @return typed value or null
     */
    public <T> T arg(int index, Class<T> type) {
        Object value = arg(index);
        if (value == null) {
            return null;
        }
        if (type != null && type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    /**
     * Returns the parsed argument by name.
     *
     * @param name argument name
     * @return argument value or null
     */
    public Object arg(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (int i = 0; i < arguments.size() && i < parsedArgs.length; i++) {
            String argName = arguments.get(i).getName();
            if (argName != null && argName.equalsIgnoreCase(name)) {
                return parsedArgs[i];
            }
        }
        return null;
    }

    /**
     * Returns the parsed argument by name with type checking.
     *
     * @param name argument name
     * @param type expected type
     * @param <T> type parameter
     * @return typed value or null
     */
    public <T> T arg(String name, Class<T> type) {
        Object value = arg(name);
        if (value == null) {
            return null;
        }
        if (type != null && type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    /**
     * Returns true if an argument value exists by name.
     *
     * @param name argument name
     * @return true if argument is present
     */
    public boolean hasArg(String name) {
        return arg(name) != null;
    }

    @Override
    public String toString() {
        return "CommandExecution{" +
                "commandName='" + commandName + '\'' +
                ", subCommandName='" + subCommandName + '\'' +
                ", sender=" + sender +
                ", rawArgs=" + rawArgs +
                ", arguments=" + arguments.size() +
                ", parsedArgs=" + parsedArgs.length +
                '}';
    }
}
