# Migration Guide

This page shows how to move from the older manual wiring style to the current
bootstrap-first setup.

## 1. Manual Bootstrap -> `buildRuntime()`

### Bukkit/Paper

Before:

```java
Platform platform = new BukkitPlatformProvider(this);
ConfigManager configManager = new ConfigManager(platform);
Logger logger = new Logger(platform, this, configManager);
LanguageManager languageManager = new LanguageManager(this, configManager);
languageManager.init("en");
languageManager.addMagicUtilsMessages();
logger.setLanguageManager(languageManager);
Messages.register(getName(), languageManager);
```

After:

```java
BukkitBootstrap.RuntimeResult magic = BukkitBootstrap.forPlugin(this)
        .buildRuntime();
```

What you gain:

- one managed shutdown handle
- consistent logger/lang/messages wiring
- optional command registry integration
- easy access through `MagicRuntime`

### Fabric

Before:

```java
Platform platform = new FabricPlatformProvider(server);
ConfigManager configManager = new ConfigManager(platform);
Logger logger = new Logger(platform, configManager, "MyMod");
```

After:

```java
FabricBootstrap.RuntimeResult magic = FabricBootstrap.forMod("mymod", () -> server)
        .buildRuntime();
```

Important: Fabric command registration still happens inside
`CommandRegistrationCallback.EVENT`.

### Velocity

Before:

```java
Platform platform = new VelocityPlatformProvider(proxy, slf4j, dataDirectory, this);
ConfigManager configManager = new ConfigManager(platform);
LoggerCore logger = new LoggerCore(platform, configManager, this, "MyPlugin");
```

After:

```java
VelocityBootstrap.RuntimeResult magic = VelocityBootstrap.forPlugin(proxy, this, "MyPlugin", dataDirectory)
        .slf4j(slf4j)
        .buildRuntime();
```

## 2. `initialize(...)` -> Explicit Registry Or Bootstrap

Older code often used the static default registry:

```java
CommandRegistry.initialize(plugin, "myplugin", logger);
CommandRegistry.register(plugin, new DonateCommand());
```

Preferred now:

```java
CommandRegistry registry = CommandRegistry.create(plugin, "myplugin", logger);
registry.registerCommand(new DonateCommand());
```

Or through bootstrap:

```java
BukkitBootstrap.RuntimeResult magic = BukkitBootstrap.forPlugin(plugin)
        .permissionPrefix("myplugin")
        .enableCommands()
        .configureCommands(registry -> registry.registerCommand(new DonateCommand()))
        .buildRuntime();
```

Why prefer this:

- easier testing
- fewer hidden globals
- clearer ownership in multi-plugin or multi-mod setups

## 3. `CommandSpec.builder(...)` -> `MagicCommand.builder(...)`

Older builder-based command code often produced a detached spec:

```java
CommandSpec<CommandSender> spec = CommandSpec.<CommandSender>builder("donate")
        .execute(ctx -> CommandResult.success("ok"))
        .build();

registry.registerSpec(spec);
```

Preferred now:

```java
MagicCommand command = MagicCommand.<CommandSender>builder("donate")
        .execute(ctx -> CommandResult.success("ok"))
        .build();

registry.registerCommand(command);
```

This matters because builder-authored commands are now real `MagicCommand`
instances. They can use the same runtime adaptation API as annotation-based
commands:

- `withName(...)`
- `addAlias(...)`
- `addSubCommand(...)`
- `setExecute(...)`
- `mount(existingCommand)`

`registerSpec(...)` still works for migration, but it is now the compatibility
path rather than the primary one.

## 4. Ad-Hoc Reload Logic -> `MagicRuntime` Bindings

Older reload code often looked like this:

```java
if (client != null) {
    client.close();
}
client = MagicHttpClient.builder(platform, configManager)
        .baseUrl(config.monitoring.baseUrl)
        .build();
```

Preferred now:

```java
MagicRuntimeConfigBinding<ServiceConfig, MagicHttpClient> binding = runtime.bindConfig(
        "http.monitoring",
        ServiceConfig.class,
        config -> MagicHttpClient.builder(runtime.platform(), runtime.configManager())
                .baseUrl(config.monitoring.baseUrl)
                .build(),
        "monitoring"
);
```

Or use the higher-level profile wrapper:

```java
MagicHttpClientProfile<ServiceConfig> monitoring = MagicHttpClientProfile
        .builder(runtime, "http.monitoring", ServiceConfig.class)
        .sections("monitoring")
        .baseUrl(config -> config.monitoring.baseUrl)
        .build();
```

## 4. Manual Shutdown -> One Runtime Close

Before:

```java
configManager.shutdown();
CommandRegistry.shutdown(plugin);
Messages.unregister(getName());
```

After:

```java
magic.runtime().close();
```

That only works when the services were created through bootstrap or registered
inside the runtime.

## 5. When Not To Migrate

Keep the manual style when:

- you are integrating MagicUtils into an unusual platform
- you need partial module wiring without the bootstrap defaults
- you intentionally manage service lifecycles yourself

Even in those cases, `MagicRuntime` is still useful as a local lifecycle
container.
