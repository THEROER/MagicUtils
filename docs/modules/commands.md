# Commands

MagicUtils commands are annotation-first, but the runtime model is registry
based. Each platform exposes a `CommandRegistry` that owns parsers,
permissions, and command registration for that plugin or mod.

See [Commands Cheat Sheet](commands-cheatsheet.md) for a quick reference and
[Permissions](permissions.md) for node generation details.

## Registration Models

There are three common ways to obtain a registry:

1. Bootstrap helper creates it for you.
2. `CommandRegistry.create(...)` returns an instance you keep explicitly.
3. Legacy `CommandRegistry.initialize(...)` / `createDefault(...)` creates the
   default registry for the current platform.

For multi-plugin or multi-mod setups, prefer an explicit registry instance or
the scoped static overloads. The no-arg `register(...)` methods operate on the
default registry.

## Platform Registration

### Bukkit/Paper

Bootstrap-first:

```java
BukkitBootstrap.RuntimeResult magic = BukkitBootstrap.forPlugin(plugin)
        .permissionPrefix("myplugin")
        .enableCommands()
        .configureCommands(registry -> registry.registerCommand(new DonateCommand()))
        .buildRuntime();
```

Manual registry:

```java
CommandRegistry registry = CommandRegistry.create(plugin, "myplugin", logger);
registry.registerCommand(new DonateCommand());
registry.registerCommand(new AdminCommand());
```

### Fabric

Bootstrap-first:

```java
FabricBootstrap.RuntimeResult magic = FabricBootstrap.forMod("mymod", () -> server)
        .permissionPrefix("mymod")
        .enableCommands()
        .buildRuntime();

CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
    if (magic.commandRegistry() != null) {
        magic.commandRegistry().registerCommand(dispatcher, new DonateCommand());
    }
});
```

Manual registry:

```java
CommandRegistry registry = CommandRegistry.create("mymod", "mymod", logger, 2);

CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
    registry.registerCommand(dispatcher, new DonateCommand());
});
```

### Velocity

Bootstrap-first:

```java
VelocityBootstrap.RuntimeResult magic = VelocityBootstrap.forPlugin(proxy, plugin, "MyPlugin", dataDirectory)
        .permissionPrefix("myplugin")
        .enableCommands()
        .configureCommands(registry -> registry.registerCommand(new DonateCommand()))
        .buildRuntime();
```

Manual registry:

```java
CommandRegistry registry = CommandRegistry.create(proxy, plugin, "myplugin", loggerCore);
registry.registerCommand(new DonateCommand());
```

### NeoForge

NeoForge currently uses the manual path:

```java
CommandRegistry registry = CommandRegistry.create("mymod", "mymod", loggerCore, 2);

@SubscribeEvent
public void onRegisterCommands(RegisterCommandsEvent event) {
    registry.registerCommand(event.getDispatcher(), new DonateCommand());
}
```

The second argument is always the permission prefix used when generating nodes.

## Annotation-Based Commands

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

### Common Annotations

- `@ParamName` overrides argument names for help output.
- `@OptionalArgument` or `@DefaultValue("...")` marks a parameter optional.
- `@Greedy` captures the rest of the input.
- `@Suggest("source")` adds completion hints.
- `@Sender` injects the sender and hides it from help output.
- `@Option(shortNames = {"a"}, longNames = {"amount"})` enables `-a 5` and
  `--amount 5`. Set `flag = true` for toggles such as `-s` / `--silent`.

`@Sender` supports sender filtering via `AllowedSender`:

- `ANY`, `PLAYER`, `CONSOLE`
- `BLOCK`, `MINECART`, `PROXIED`, `REMOTE`

Platform-specific sender types can also be injected directly:

- Bukkit: `CommandSender`, `Player`
- Fabric: `ServerCommandSource`, `ServerPlayerEntity`
- Velocity: `CommandSource`, `Player`, `ConsoleCommandSource`
- NeoForge: `CommandSourceStack`, `ServerPlayer`

## Suggestions And Type Parsers

Suggestions can come from:

- Built-in type parsers (players, worlds, enums, booleans).
- Special sources such as `@players`, `@worlds`, `@commands`.
- Inline lists: `@Suggest("{on,off,reset}")`.
- Methods on the command class: `@Suggest("getItems")`.

Suggestion methods can be:

- `String[] getItems()` or `List<String> getItems()`
- `getItems(Player player)`
- `getItems(ServerCommandSource sender)`
- `getItems(CommandSource sender)`

Built-in sources:

- `@players`, `@player`, `@allplayers`
- `@offlineplayers` (Bukkit only)
- `@worlds`, `@world`
- `@language_keys` (Bukkit only)
- `@commands`
- `{a,b,c}` inline list syntax

Custom parsers are registered on the registry's parser registry:

```java
registry.commandManager()
        .getTypeParserRegistry()
        .register(new MyTypeParser());
```

## Permissions

Permissions can be defined at three levels:

- `@CommandInfo.permission`
- `@SubCommand.permission`
- `@Permission` on parameters

Generated nodes use this shape when you do not provide explicit values:

- Command: `commands.<command>`
- Subcommand: `commands.<command>.subcommand.<path>`
- Argument: `commands.<command>.subcommand.<path>.argument.<name>`

These nodes are prefixed by the registry permission prefix. See
[Permissions](permissions.md) for the platform-specific behaviour.

`MagicPermissionDefault` controls the default access policy:

- `TRUE`
- `OP`
- `NOT_OP`
- `FALSE`

## CommandResult

`CommandResult.success()` and `CommandResult.failure()` control whether MagicUtils
sends feedback automatically.

- `CommandResult.success()` means success with no reply text.
- `CommandResult.success("Done")` sends a success reply.
- `CommandResult.failure("No permission")` sends a failure reply.

## Threading

Commands run on the main thread by default. Use `CommandThreading.ASYNC` for
IO-heavy or CPU-heavy work:

```java
@CommandInfo(name = "donate", threading = CommandThreading.ASYNC)
public final class DonateCommand extends MagicCommand {
    public CommandResult execute(@Sender MagicSender sender) {
        return CommandResult.success("done");
    }

    @SubCommand(name = "give", threading = CommandThreading.ASYNC)
    public CommandResult give(@Sender MagicSender sender, Player target) {
        return CommandResult.success("ok");
    }
}
```

Builder equivalents:

```java
MagicCommand.<CommandSender>builder("donate")
        .threading(CommandThreading.ASYNC)
        .execute(ctx -> CommandResult.success("done"))
        .build();

SubCommandSpec.<CommandSender>builder("give")
        .threading(CommandThreading.ASYNC)
        .execute(ctx -> CommandResult.success("ok"))
        .build();
```

Only mark commands as async when your code is thread-safe. When you need to
touch platform APIs again, switch back to the main thread via
`Platform.runOnMain(...)` or `Tasks.runOnMain(...)`.

## Help Output

The help renderer respects permissions and hides commands or arguments the
sender cannot access. It is styled through `logger.{ext}` under the `help`
section.

### Standalone Help Command

Bukkit and Fabric ship a ready-to-register `HelpCommand` wrapper:

```java
registry.registerCommand(new HelpCommand(logger, registry));
```

You can rename it at runtime:

```java
registry.registerCommand(new HelpCommand(logger, registry)
        .withName("donatehelp")
        .addAlias("dhelp"));
```

### Help As A Subcommand

Use `HelpCommandSupport` when you want help inside another command tree or on
platforms that do not ship a dedicated wrapper:

```java
registry.registerCommand(new DonateCommand()
        .addSubCommand(HelpCommandSupport.createHelpSubCommand(
                "help",
                loggerCore,
                registry::commandManager
        )));
```

## MagicSender

`MagicSender` is the platform-neutral sender wrapper used throughout the
command system:

```java
MagicSender sender = MagicSender.wrap(rawSender);
if (MagicSender.hasPermission(rawSender, "my.permission")) {
    // ...
}
```

Use it when you want shared command logic across Bukkit, Fabric, Velocity, and
NeoForge without branching on raw sender types.

## Builder API

Use the builder API when you need runtime composition but still want a real
`MagicCommand` instance:

```java
MagicCommand donateCommand = MagicCommand.<CommandSender>builder("donate")
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

registry.registerCommand(donateCommand);
```

You can mix annotations with runtime overrides:

- `withName(...)`, `addAlias(...)`, `removeAlias(...)`
- `addSubCommand(SubCommandSpec<?>)`
- `setExecute(...)`
- `mount(MagicCommand)` / `mount("route", existingCommand)`

Nested builder subcommands are supported as well:

```java
SubCommandSpec<CommandSender> npcAdd = SubCommandSpec.<CommandSender>builder("add")
        .path("npc", "commands")
        .description("Add NPC command")
        .execute(ctx -> CommandResult.success("ok"))
        .build();
```

## Composing Existing Commands

Already-authored annotation commands can be mounted under another command tree
without rewriting them into `SubCommandSpec` form:

```java
MagicCommand adminCommand = MagicCommand.<CommandSender>builder("admin")
        .mount("punish", new BanCommand())
        .build();

registry.registerCommand(adminCommand);
```

This is intentionally different from `withName("punish")`:

- `withName(...)` mutates the authored command instance itself
- `mount("punish", command)` keeps the source command identity intact and only
  changes the route segment inside the parent tree

Mounting snapshots the child command structure at mount time. That means:

- changing the child name or aliases later does not rewrite the already-mounted
  parent tree
- the mounted command still executes against the original child instance
- when you override the mounted route, root aliases from the child are not
  exposed automatically

## Mutation Lifecycle

`MagicCommand` is mutable only during definition and composition:

```java
MagicCommand command = MagicCommand.<CommandSender>builder("donate")
        .execute(ctx -> CommandResult.success("ok"))
        .build()
        .addAlias("d");
```

After `registry.registerCommand(...)` or `commandManager.register(...)`, the
command is frozen. Later calls to:

- `withName(...)`
- `addAlias(...)`
- `removeAlias(...)`
- `addSubCommand(...)`
- `setExecute(...)`
- `mount(...)`

throw `IllegalStateException`. If you still use `registerSpec(...)`, it remains
supported as a compatibility path and is internally converted into the same
`MagicCommand` runtime model.
