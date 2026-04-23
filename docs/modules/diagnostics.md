# Diagnostics

MagicUtils diagnostics provide runtime self-checks that can be executed inside a
live plugin or mod. The diagnostics service is built on top of `MagicRuntime`
and ships with safe infrastructure-focused checks for runtime wiring,
filesystem access, scheduler behavior, command registration exposure, and
placeholder registry access.

## Bootstrap Wiring

Enable diagnostics from the platform bootstrap builder:

```java
BukkitBootstrap.RuntimeResult magic = BukkitBootstrap.forPlugin(plugin)
        .enableCommands()
        .enableDiagnostics()
        .configureDiagnostics(registry -> {
            registry.register(new MyDatabaseCheck());
        })
        .buildRuntime();

DiagnosticsService diagnostics = magic.diagnosticsService();
```

The service is also available from the runtime container:

```java
DiagnosticsService diagnostics =
        magic.runtime().requireComponent(DiagnosticsService.class);
```

## Running Checks

Execute the full report or a single suite:

```java
DiagnosticReport safeReport = diagnostics.runAll(DiagnosticRunRequest.safe());
DiagnosticReport standardReport = diagnostics.runSuite(
        "scheduler",
        DiagnosticRunRequest.standard()
);
```

Built-in suites include:

- `runtime`
- `filesystem`
- `config`
- `scheduler`
- `threading`
- `commands`
- `placeholders`

`SAFE` mode avoids temp-file writes and reload probes. `STANDARD` mode enables
reversible probes such as temp-file writes and config reloadability checks.

## Exporting Reports

Reports can be rendered to text or exported as JSON:

```java
List<String> lines = DiagnosticReports.renderText(safeReport);
Path exported = diagnostics.exportJson(safeReport);
```

The default export path is:

```text
<configDir>/diagnostics/latest.json
```

## Command Helper

Mount diagnostics into an existing command tree the same way you mount help:

```java
MagicCommand admin = MagicCommand.<CommandSender>builder("admin")
        .subCommand(HelpCommandSupport.createHelpSubCommand(
                logger.getCore(),
                registry::commandManager
        ))
        .subCommand(DiagnosticsCommandSupport.createDiagnosticsSubCommand(
                logger.getCore(),
                magic::diagnosticsService
        ))
        .build();
```

Supported command forms:

- `/plugin diagnostics`
- `/plugin diagnostics safe`
- `/plugin diagnostics standard`
- `/plugin diagnostics export`
- `/plugin diagnostics export standard`
- `/plugin diagnostics suite magicutils.scheduler`
- `/plugin diagnostics suite magicutils.scheduler standard`
