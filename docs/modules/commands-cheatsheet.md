# Commands Cheat Sheet

Quick reference for the MagicUtils command system.

## Minimal Registry Setup

```java
CommandRegistry registry = CommandRegistry.create(plugin, "myplugin", logger);
registry.registerCommand(new DonateCommand());
```

## Minimal Annotated Command

```java
@CommandInfo(name = "donate", description = "Main command")
public final class DonateCommand extends MagicCommand {
    public CommandResult execute(@Sender MagicSender sender) {
        return CommandResult.success("ok");
    }
}
```

## Threading (Async)

```java
@CommandInfo(name = "donate", threading = CommandThreading.ASYNC)
public final class DonateCommand extends MagicCommand {
    @SubCommand(name = "give", threading = CommandThreading.ASYNC)
    public CommandResult give(@Sender MagicSender sender, Player target) { ... }
}
```

## Subcommands And Nested Paths

```java
@SubCommand(name = "give")
public CommandResult give(@Sender MagicSender sender, Player target) { ... }

@SubCommand(path = {"npc", "commands"}, name = "add")
public CommandResult addNpcCommand(...) { ... }
```

## Options / Flags

```java
public CommandResult give(
        @Option(shortNames = {"a"}, longNames = {"amount"}) int amount,
        @Option(shortNames = {"s"}, longNames = {"silent"}, flag = true) boolean silent
) { ... }
```

Accepted forms:

- `--amount 5`
- `-a 5`
- `--silent` / `-s`

## Optional + Default Values

```java
public CommandResult set(
        @DefaultValue("en") String lang,
        @OptionalArgument Player target
) { ... }
```

## Greedy Text

```java
public CommandResult say(@Greedy String message) { ... }
```

## Suggestions

```java
@Suggest("@players") Player target
@Suggest("{easy,hard}") String mode
@Suggest("getItems") String item
```

Suggestion methods can be:

- `List<String> getItems()`
- `List<String> getItems(Player player)`
- `List<String> getItems(ServerCommandSource sender)`
- `List<String> getItems(CommandSource sender)`

## Sender Injection

```java
public CommandResult execute(@Sender MagicSender sender) { ... }
```

Allowed senders:

`ANY`, `PLAYER`, `CONSOLE`, `BLOCK`, `MINECART`, `PROXIED`, `REMOTE`.

## Builder API Snippet

```java
CommandSpec<CommandSender> spec = CommandSpec.<CommandSender>builder("donate")
        .description("Main command")
        .aliases("d")
        .threading(CommandThreading.ASYNC)
        .execute(ctx -> CommandResult.success("ok"))
        .subCommand(SubCommandSpec.<CommandSender>builder("give")
                .argument(CommandArgument.builder("player", Player.class).build())
                .threading(CommandThreading.ASYNC)
                .execute(ctx -> CommandResult.success("ok"))
                .build())
        .build();
```
