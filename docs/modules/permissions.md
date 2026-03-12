# Permissions

MagicUtils commands generate and evaluate permission nodes automatically. The
registry prefix controls namespacing, while annotations control explicit nodes,
defaults, and conditional argument checks.

## Permission Prefix

Every command registry has a permission prefix. You set it either directly on
the registry:

```java
CommandRegistry registry = CommandRegistry.create(plugin, "donatemenu", logger);
```

or through the bootstrap helper:

```java
BukkitBootstrap.forPlugin(plugin)
        .permissionPrefix("donatemenu")
        .enableCommands()
        .buildRuntime();
```

MagicUtils prepends that prefix to all generated nodes.

## Generated Nodes

When annotations omit explicit permission strings, MagicUtils builds defaults:

- Command: `commands.<command>`
- Subcommand: `commands.<command>.subcommand.<path>`
- Argument: `commands.<command>.subcommand.<path>.argument.<name>`

With prefix `donatemenu`:

- `donatemenu.commands.donate`
- `donatemenu.commands.donate.subcommand.give`
- `donatemenu.commands.donate.subcommand.give.argument.player`

## Default Access

`MagicPermissionDefault` controls what happens when the permission node is not
granted explicitly:

- `TRUE` -> everyone
- `OP` -> operators or elevated senders
- `NOT_OP` -> non-operators / non-elevated senders
- `FALSE` -> nobody

Platform behaviour differs slightly:

- Bukkit registers permission nodes with Bukkit's permission manager and uses
  Bukkit permission defaults.
- Fabric checks `fabric-permissions-api-v0` when available and falls back to
  op-level checks.
- NeoForge falls back to command-source permission level checks.
- Velocity relies on the proxy's permission checks and uses the default policy
  only when the node itself is absent.

## Wildcards

On Bukkit, MagicUtils also registers wildcard nodes:

- `...commands.<command>.*`
- `...commands.<command>.subcommand.*`

Velocity also honours prefix-style wildcard checks such as `prefix.*` and
`prefix<node>.*` when the proxy reports them as granted.

## Explicit Permission Annotations

Use explicit nodes when you want stable names independent of the generated
shape:

```java
@CommandInfo(
        name = "donate",
        permission = "donatemenu.open",
        permissionDefault = MagicPermissionDefault.TRUE
)
public final class DonateCommand extends MagicCommand {
}
```

Subcommands support the same fields:

```java
@SubCommand(
        name = "reload",
        permission = "donatemenu.admin.reload",
        permissionDefault = MagicPermissionDefault.OP
)
public CommandResult reload(@Sender MagicSender sender) {
    return CommandResult.success("Reloaded");
}
```

## Argument Permissions

Use `@Permission` on parameters to gate argument usage:

```java
public CommandResult grant(
        @Sender MagicSender sender,
        @Permission(when = "other(player)") @ParamName("player") Player target
) {
    return CommandResult.success("ok");
}
```

You can override the generated node segment:

```java
@Permission(node = "target", includeArgumentSegment = false)
```

## Conditional Permission Keywords

- `self(arg)` / `other(arg)` / `anyother(arg)`
- `not_null(arg)` / `exists(arg)`
- `distinct(a,b)` / `all_distinct(a,b)`
- `equals(a,b)` / `not_equals(a,b)`

Use `compare = CompareMode.UUID/NAME/EQUALS/AUTO` to control how values are
compared.

## Manual Checks

`MagicSender` exposes direct permission checks when you need custom logic
outside annotation processing:

```java
MagicSender sender = MagicSender.wrap(rawSender);
if (MagicSender.hasPermission(rawSender, "donatemenu.commands.donate")) {
    // ...
}
```

You can also use the registry prefix when building related manual nodes so the
manual and generated permissions stay in the same namespace.
