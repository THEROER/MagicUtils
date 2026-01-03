# Permissions

MagicUtils commands generate and check permission nodes automatically. You can
override any node, control default access, and apply conditional checks for
arguments.

## Permission prefix

Every `CommandRegistry.initialize(...)` call includes a permission prefix.
MagicUtils prepends it to all generated nodes.

```java
CommandRegistry.initialize(plugin, "donatemenu", logger);
```

## Generated nodes

When annotations omit explicit permission strings, MagicUtils builds defaults:

- Command: `commands.<command>`
- Subcommand: `commands.<command>.subcommand.<path>`
- Argument: `commands.<command>.subcommand.<path>.argument.<name>`

With prefix `donatemenu`:

- `donatemenu.commands.donate`
- `donatemenu.commands.donate.subcommand.give`
- `donatemenu.commands.donate.subcommand.give.argument.player`

## Wildcards

On Bukkit, MagicUtils registers wildcard nodes for convenience:

- `...commands.<command>.*`
- `...commands.<command>.subcommand.*`

These are registered alongside concrete nodes.

## Default access

`MagicPermissionDefault` controls who can execute when no explicit permission
is granted:

- `TRUE` -> everyone
- `OP` -> operators (default)
- `NOT_OP` -> non-operators
- `FALSE` -> nobody (explicit permission required)

On Bukkit, MagicUtils registers the permission nodes with these defaults. On
Fabric, MagicUtils checks `fabric-permissions-api` when available and falls
back to op-level checks otherwise.

## Argument permissions

Use `@Permission` on parameters to gate argument usage:

```java
public CommandResult grant(
        @Sender MagicSender sender,
        @Permission(when = "other(player)") @ParamName("player") Player target
) {
    return CommandResult.success("ok");
}
```

You can override the permission node segment and whether `.argument.` is
included:

```java
@Permission(node = "target", includeArgumentSegment = false)
```

## Conditional permission keywords

- `self(arg)` / `other(arg)` / `anyother(arg)`
- `not_null(arg)` / `exists(arg)`
- `distinct(a,b)` / `all_distinct(a,b)`
- `equals(a,b)` / `not_equals(a,b)`

Use `compare = CompareMode.UUID/NAME/EQUALS/AUTO` to control comparison rules.

## Manual checks

`MagicSender` can be used to check permissions directly:

```java
MagicSender sender = MagicSender.wrap(rawSender);
if (MagicSender.hasPermission(rawSender, "donatemenu.commands.donate")) {
    // ...
}
```
