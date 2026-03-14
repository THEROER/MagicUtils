# Runtime

`MagicRuntime` is the managed service container behind the bootstrap-first
setup. It gives you one place to access core services, register extra
components, manage closeable resources, and rebuild config-backed clients on
reload.

## Getting A Runtime

The recommended path is `buildRuntime()`:

```java
BukkitBootstrap.RuntimeResult magic = BukkitBootstrap.forPlugin(this)
        .enableCommands()
        .buildRuntime();

MagicRuntime runtime = magic.runtime();
```

The same pattern exists for:

- `BukkitBootstrap`
- `FabricBootstrap`
- `VelocityBootstrap`

NeoForge and custom platforms can build `MagicRuntime` manually.

## Core Components

Every runtime starts with typed components for the core services:

- `Platform`
- `ConfigManager`
- `LoggerCore`
- `LanguageManager` when configured

Access them via the typed component registry:

```java
Platform platform = runtime.requireComponent(Platform.class);
ConfigManager configManager = runtime.requireComponent(ConfigManager.class);
LoggerCore logger = runtime.requireComponent(LoggerCore.class);
CommandRegistry commands = runtime.findComponent(CommandRegistry.class).orElse(null);
```

Use `findComponent(...)` when the component is optional and
`requireComponent(...)` when its absence is a bug.

## Typed Components

Register and replace typed components at runtime:

```java
runtime.putComponent(MyService.class, new MyService());

MyService service = runtime.requireComponent(MyService.class);
Optional<MyService> opt = runtime.findComponent(MyService.class);
```

`findComponent(...)` also matches assignable types, so requesting an interface
will find a registered implementation.

## Named Components

`MagicRuntime` also exposes a named registry for dynamic resources:

```java
runtime.putNamedComponent("service.cache", cacheClient);

CacheClient cache = runtime.requireNamedComponent("service.cache", CacheClient.class);
Optional<CacheClient> opt = runtime.findNamedComponent("service.cache", CacheClient.class);
```

Remove a named component when it is no longer needed:

```java
runtime.removeNamedComponent("service.cache");
```

Named components are especially useful for:

- reloadable clients
- plugin-owned service singletons
- resources keyed by logical role (`http.monitoring`, `ws.gateway`)

## Managed Resources

Use `resource(...)` when you want a stable named slot that closes replaced
resources automatically:

```java
MagicRuntimeResource<MagicHttpClient> monitoring = runtime.resource(
        "http.monitoring",
        MagicHttpClient.builder(runtime.platform(), runtime.configManager())
                .baseUrl("https://api.example.com/")
                .build()
);

MagicHttpClient client = monitoring.require();
monitoring.set(MagicHttpClient.builder(runtime.platform(), runtime.configManager())
        .baseUrl("https://api-two.example.com/")
        .build());
```

The resource is also exposed through the named component registry under the same
name.

## Config Bindings

Use `bindConfig(...)` when a closeable resource should rebuild automatically on
matching config reloads:

```java
MagicRuntimeConfigBinding<ServiceConfig, MagicHttpClient> binding = runtime.bindConfig(
        "http.monitoring",
        ServiceConfig.class,
        config -> MagicHttpClient.builder(runtime.platform(), runtime.configManager())
                .baseUrl(config.monitoring.baseUrl)
                .build(),
        "monitoring"
);

MagicHttpClient client = binding.require();
```

This pattern works well for:

- HTTP clients
- WebSocket clients
- database pools
- SDK clients

The HTTP client module also provides higher-level wrappers:

- `MagicHttpClientProfile`
- `MagicWebSocketClientProfile`

Use those when the resource being managed is specifically an HTTP or WebSocket
client.

## Lifecycle

`MagicRuntime.close()`:

- unregisters its platform shutdown hook when one was installed
- closes managed resources in reverse registration order
- closes runtime resources and config bindings
- can shut down `ConfigManager` automatically

Bootstrap helpers already configure the runtime so that `magic.runtime().close()`
is the one shutdown call you usually need in your plugin or mod.

## Building A Runtime Manually

For NeoForge or custom platforms, build it directly:

```java
MagicRuntime runtime = MagicRuntime.builder(platform, configManager, logger)
        .languageManager(languageManager)
        .component(MyPlugin.class, this)
        .manage("database", databaseClient)
        .onClose("metrics", metrics::flush)
        .manageConfigManager(true)
        .autoRegisterShutdown(true)
        .build();
```

Builder controls:

- `languageManager(...)`
- `component(...)`
- `manage(...)`
- `onClose(...)`
- `manageConfigManager(...)`
- `autoRegisterShutdown(...)`

Disable `autoRegisterShutdown(...)` when the platform already has an explicit
shutdown phase you want to own manually.
