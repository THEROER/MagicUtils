# Core / Common Logic

Use this page when your plugin or mod has a thin platform entrypoint and most of
the real logic lives in shared `common` or `core` code.

That usually means:

- Bukkit/Fabric/Velocity/NeoForge only bootstrap the runtime
- config, lang, logger, HTTP, placeholders, and business logic live in common
- platform modules stay as adapters instead of owning the feature logic

## Typical Module Layout

```text
my-plugin/
|- common/
|- bukkit/
|- fabric/
`- velocity/
```

In this structure:

- `bukkit/`, `fabric/`, and `velocity/` bootstrap MagicUtils for their runtime
- `common/` receives `MagicRuntime` and owns the actual feature logic
- platform modules do not reimplement the same services three times

## The Recommended Split

Keep these parts in the platform layer:

- bootstrap helpers such as `BukkitBootstrap`, `FabricBootstrap`,
  `VelocityBootstrap`
- platform event registration
- command registration against the platform dispatcher
- external placeholder bridge setup
- plugin or mod lifecycle entrypoints

Keep these parts in `common` or other shared modules:

- services built on `MagicRuntime`
- config models and reload logic
- logger and language driven messaging
- HTTP clients and runtime config bindings
- placeholder logic used by your own code
- business rules that should work across every platform

## Common Code Should Depend On Shared Abstractions

Inside common code, depend on `MagicRuntime` or the core services it exposes:

- `Platform`
- `ConfigManager`
- `LoggerCore`
- `LanguageManager`
- named runtime resources and config bindings

This keeps the shared layer free from Bukkit, Fabric, Velocity, or NeoForge
classes.

## Wiring Shared Services From The Platform Layer

The platform entrypoint should bootstrap MagicUtils and hand the runtime to your
common services:

```java
public final class MyPlugin extends JavaPlugin {
    private BukkitBootstrap.RuntimeResult magic;
    private CommonBootstrap bootstrap;

    @Override
    public void onEnable() {
        magic = BukkitBootstrap.forPlugin(this)
                .enableCommands()
                .buildRuntime();

        bootstrap = new CommonBootstrap(magic.runtime());
        bootstrap.start();
    }

    @Override
    public void onDisable() {
        if (magic != null) {
            magic.runtime().close();
            magic = null;
        }
    }
}
```

The same structure works on Fabric, Velocity, and NeoForge: platform code
creates the runtime, shared code consumes it.

`forPlugin(...)`, `forMod(...)`, and the other bootstrap helpers belong to the
platform module only. `common` should consume the resulting runtime, not create
it.

## Example Shared Service

```java
public final class CommonBootstrap {
    private final MagicRuntime runtime;
    private final ConfigManager configManager;
    private final LoggerCore logger;
    private final Optional<LanguageManager> languages;

    public CommonBootstrap(MagicRuntime runtime) {
        this.runtime = runtime;
        this.configManager = runtime.configManager();
        this.logger = runtime.logger();
        this.languages = runtime.findComponent(LanguageManager.class);
    }

    public void start() {
        logger.info("Starting shared services");

        languages.ifPresent(manager -> logger.info("Language manager is available"));

        runtime.bindConfig(
                "http.backend",
                BackendConfig.class,
                config -> MagicHttpClient.builder(runtime.platform(), configManager)
                        .baseUrl(config.baseUrl)
                        .build(),
                "backend"
        );
    }
}
```

This is the typical multi-platform pattern: the shared layer receives one
runtime from the adapter layer and builds everything else on top of it.

## What Works Well In Common

The `magicutils-core` path is especially good for:

- `MagicRuntime`
- `ConfigManager`
- `LoggerCore`
- optional `LanguageManager`
- runtime-managed HTTP or WebSocket clients
- shared placeholder evaluation
- reloadable services built with `bindConfig(...)`

If your modules already target multiple platforms, this is usually where the
majority of the code should live.

## Player Events

`Platform` provides normalized player lifecycle and message events that work
across all platforms without importing Bukkit, Fabric, or Velocity types.

### Player Lifecycle

Subscribe to join/leave events:

```java
ListenerSubscription sub = platform.subscribePlayerLifecycle(event -> {
    if (event.type() == PlayerLifecycleType.JOIN) {
        logger.info(event.playerName() + " joined");
    }
});
```

`PlayerLifecycle` contains:

- `playerId()` — player UUID (when available)
- `playerName()` — display/login name
- `type()` — `JOIN` or `LEAVE`

### Player Messages

Subscribe to chat messages and commands:

```java
ListenerSubscription sub = platform.subscribePlayerMessages(event -> {
    if (event.type() == PlayerMessageType.CHAT) {
        logger.info(event.playerName() + ": " + event.message());
    }
});
```

`PlayerMessage` contains:

- `playerId()` — player UUID (when available)
- `playerName()` — display/login name
- `message()` — raw chat content or command line
- `type()` — `CHAT` or `COMMAND`

Both subscriptions return `ListenerSubscription` which can be closed to
unsubscribe. Both records expose `isValid()` for null-safety checks.

## What Should Stay Out Of Common

Try not to leak platform-specific APIs into the shared layer.

Avoid putting these directly into `common`:

- Bukkit `JavaPlugin`, Fabric callbacks, Velocity annotations, NeoForge events
- platform command dispatcher registration
- direct calls to platform plugin managers or server APIs
- external bridge setup that only exists on one runtime

Keep those in the platform module and pass only the shared abstractions
downward.

## If You Really Need A Custom Platform

That is a separate case from normal `common` code.

If you are actually building a new adapter around `Platform`,
`ShutdownHookRegistrar`, or your own bootstrap path, use the Runtime guide and
the platform API as the source of truth, but keep that adapter layer small and
let the feature logic remain in shared services.
