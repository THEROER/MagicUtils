# Commands

MagicUtils commands are annotation-first but can be combined with a builder
API. Commands are registered per platform via `CommandRegistry`.

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

The second argument is the permission prefix used when building nodes.

## Annotation-based commands

Use `@CommandInfo` on the class and `@SubCommand` on methods. A method named
`execute` without `@SubCommand` is treated as the root handler.

```java
@CommandInfo(
        name = "donate",
        description = "DonateMenu main command",
        aliases = {"d"},
        permission = "donate.use"
)
public final class DonateCommand extends MagicCommand {

    public CommandResult execute(@Sender MagicSender sender) {
        return CommandResult.success("Opened menu");
    }

    @SubCommand(name = "give", description = "Give currency to a player")
    public CommandResult give(
            @Sender MagicSender sender,
            @ParamName("player") Player target,
            @Option(shortNames = {"a"}, longNames = {"amount"}) int amount,
            @Option(shortNames = {"s"}, longNames = {"silent"}, flag = true) boolean silent
    ) {
        return CommandResult.success(silent ? "" : "Done");
    }
}
```

Nested subcommands are supported via `path`:

```java
@SubCommand(path = {"npc", "commands"}, name = "add")
public CommandResult addNpcCommand(...) { ... }
```

### Common annotations

- `@ParamName` overrides argument names for help output.
- `@OptionalArgument` or `@DefaultValue("...")` marks a parameter optional.
- `@Greedy` captures the rest of the input (free-form text).
- `@Suggest("source")` adds tab completion hints.
- `@Sender` injects the command sender and hides it from usage/help.
- `@Option(shortNames = {"a"}, longNames = {"amount"})` enables `-a 5` and
  `--amount 5`. Set `flag = true` for `-s`/`--silent` toggles.

`@Sender` supports sender filtering via `AllowedSender` to restrict console,
players, command blocks, etc.

### Suggestions

Suggestions can come from:

- Type parsers (players, worlds, enums, booleans).
- Special sources like `@players`, `@worlds`, `@commands`.
- Explicit lists: `@Suggest("{on,off,reset}")`.
- Methods on the command class (`@Suggest("getItems")`).

Suggestion methods can be:

- `String[] getItems()` or `List<String> getItems()`.
- `getItems(Player player)` or `getItems(ServerCommandSource sender)` depending on platform.

Note: `@offlineplayers` and `@language_keys` exist on Bukkit only.

### Permissions

You can lock permissions at three levels:

- `@CommandInfo.permission` for the root command.
- `@SubCommand.permission` for subcommands.
- `@Permission` on parameters for argument-level checks.

Example with conditional permission:

```java
public CommandResult grant(
        @Sender MagicSender sender,
        @Permission(when = "other(player)", message = "No permission")
        @ParamName("player") Player target
) {
    return CommandResult.success("ok");
}
```

The permission prefix passed to `CommandRegistry.initialize(...)` is prepended
automatically.

### CommandResult

`CommandResult.success()` and `CommandResult.failure()` control whether a
message is sent back to the sender. Use `success()` to avoid output, or pass a
message for automatic reply.

## MagicSender

`MagicSender` is a platform-agnostic sender wrapper. You can use it directly
in command signatures or wrap raw senders:

```java
MagicSender sender = MagicSender.wrap(rawSender);
if (MagicSender.hasPermission(rawSender, "my.permission")) {
    // ...
}
```

## Builder API

Use the builder API when you need runtime composition:

```java
CommandSpec<CommandSender> spec = CommandSpec.<CommandSender>builder("donate")
        .description("DonateMenu main command")
        .aliases("d")
        .execute(ctx -> CommandResult.success("Opened menu"))
        .subCommand(SubCommandSpec.<CommandSender>builder("give")
                .description("Give currency")
                .argument(CommandArgument.builder("player", Player.class).build())
                .argument(CommandArgument.builder("amount", Integer.class).build())
                .execute(ctx -> CommandResult.success("ok"))
                .build())
        .build();

CommandRegistry.register(spec);
```

You can also mix annotations with dynamic overrides:

- `withName(...)`, `addAlias(...)`, `removeAlias(...)`
- `addSubCommand(SubCommandSpec)` to inject new subcommands
- `setExecute(...)` to override the root handler

Builder subcommands support nested paths as well:

```java
SubCommandSpec<CommandSender> npcAdd = SubCommandSpec.<CommandSender>builder("add")
        .path("npc", "commands")
        .description("Add NPC command")
        .execute(ctx -> CommandResult.success("ok"))
        .build();
```

## Type parsers

Custom parsers and suggestions are registered per registry:

```java
CommandRegistry.getCommandManager()
        .getTypeParserRegistry()
        .register(new MyTypeParser());
```
