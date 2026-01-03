# Commands cheat sheet

Quick reference for the MagicUtils command system.

## Minimal annotated command

```java
@CommandInfo(name = "donate", description = "Main command")
public final class DonateCommand extends MagicCommand {
    public CommandResult execute(@Sender MagicSender sender) {
        return CommandResult.success("ok");
    }
}
```

## Subcommands and nested paths

```java
@SubCommand(name = "give")
public CommandResult give(@Sender MagicSender sender, Player target) { ... }

@SubCommand(path = {"npc", "commands"}, name = "add")
public CommandResult addNpcCommand(...) { ... }
```

## Options / flags

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

## Optional + default values

```java
public CommandResult set(
        @DefaultValue("en") String lang,
        @OptionalArgument Player target
) { ... }
```

## Greedy text

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

## Sender injection

```java
public CommandResult execute(@Sender MagicSender sender) { ... }
```

Allowed senders:

`ANY`, `PLAYER`, `CONSOLE`, `BLOCK`, `MINECART`, `PROXIED`, `REMOTE`.

## Builder API snippet

```java
CommandSpec<CommandSender> spec = CommandSpec.<CommandSender>builder("donate")
        .description("Main command")
        .aliases("d")
        .execute(ctx -> CommandResult.success("ok"))
        .subCommand(SubCommandSpec.<CommandSender>builder("give")
                .argument(CommandArgument.builder("player", Player.class).build())
                .execute(ctx -> CommandResult.success("ok"))
                .build())
        .build();
```
