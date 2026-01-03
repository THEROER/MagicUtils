# Commands

MagicUtils commands are annotation-first but can be combined with a builder
API. Commands are registered per platform via `CommandRegistry`.

## Annotations

- `@CommandInfo` on the command class
- `@SubCommand` on each command method
- `@OptionalArgument` for optional parameters
- `@Suggest` for tab completion
- `@Option` for named options and flags

## Example

```java
@CommandInfo(name = "donate", permission = "donate.use")
public final class DonateCommand extends MagicCommand {

    @SubCommand(name = "give")
    public CommandResult give(Player sender,
            @Option(shortNames = {"a"}, longNames = {"amount"}) int amount,
            @Option(shortNames = {"s"}, longNames = {"silent"}, flag = true) boolean silent,
            @OptionalArgument Player target) {
        return CommandResult.success("ok");
    }
}
```

### Options and flags

- `@Option(shortNames = {"a"}, longNames = {"amount"})` enables `-a 5` and
  `--amount 5`.
- `flag = true` makes it a boolean flag (`-s`, `--silent`).

## Registration

### Bukkit/Paper

```java
CommandRegistry.initialize(plugin, "myplugin", logger);
CommandRegistry.register(new DonateCommand());
```

### Fabric

```java
CommandRegistry.initialize("mymod", "mymod", logger);
CommandRegistry.register(new DonateCommand());
```
