package dev.ua.theroer.magicutils.commands;

/**
 * Command implementation backed by a CommandSpec builder.
 */
public final class DynamicCommand extends MagicCommand {
    private final CommandSpec<?> spec;

    public DynamicCommand(CommandSpec<?> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("Command spec is required");
        }
        this.spec = spec;
        withInfo(spec.toCommandInfo());
        if (spec.executor() != null) {
            setExecute(spec.executor(), spec.arguments(), true);
        }
        for (SubCommandSpec<?> sub : spec.subCommands()) {
            addSubCommand(sub, sub.replaceExisting());
        }
    }

    public CommandSpec<?> spec() {
        return spec;
    }
}
