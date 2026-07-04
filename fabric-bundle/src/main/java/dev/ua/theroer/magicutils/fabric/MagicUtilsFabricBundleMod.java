package dev.ua.theroer.magicutils.fabric;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.bootstrap.FabricBootstrap;
import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsCommandSupport;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsService;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Standalone MagicUtils Fabric mod. Boots a shared {@link MagicRuntime} via
 * {@link FabricBootstrap}, enables diagnostics and registers the
 * {@code /magicutils} (alias {@code mu}) command with diagnostics sub-commands.
 */
public final class MagicUtilsFabricBundleMod implements ModInitializer {
    private static final String MOD_ID = "magicutils-fabric-bundle";
    private static final String MOD_NAME = "MagicUtils";
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    private final AtomicReference<MinecraftServer> serverRef = new AtomicReference<>();
    private MagicRuntime runtime;

    @Override
    public void onInitialize() {
        FabricBootstrap.RuntimeResult bootstrap = FabricBootstrap.forMod(MOD_NAME, serverRef::get)
                .initLanguage(false)
                .bindLoggerLanguage(false)
                .setMessagesManager(false)
                .registerMessages(false)
                .addMagicUtilsMessages(false)
                .enableCommands()
                .enableDiagnostics()
                .permissionPrefix("magicutils")
                .buildRuntime();
        runtime = bootstrap.runtime();

        Logger logger = bootstrap.logger();
        LoggerCore loggerCore = logger != null ? logger.getCore() : null;
        CommandRegistry commandRegistry = bootstrap.commandRegistry();
        String bundleVersion = resolveBundleVersion();

        ServerLifecycleEvents.SERVER_STARTING.register(serverRef::set);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            serverRef.set(null);
            MagicRuntime current = runtime;
            if (current != null) {
                current.close();
                runtime = null;
            }
        });

        if (commandRegistry != null) {
            // Register the diagnostics suite-name parser once (so
            // `/magicutils diagnostics suite <name>` resolves). This must NOT go
            // inside the command-registration callback below: that callback fires
            // on every (re)registration (e.g. `/reload`), and the type-parser
            // registry appends without de-duplicating, so it would accumulate
            // duplicate parsers. The command manager exists as soon as commands
            // are enabled, before the callback ever runs.
            DiagnosticsCommandSupport.registerTypeParsers(commandRegistry.commandManager());
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                    commandRegistry.registerAllCommands(dispatcher, new MagicUtilsFabricBundleCommand(
                            loggerCore,
                            bundleVersion,
                            this::diagnosticsService)));
        }

        LOG.info("MagicUtils Fabric bundle loaded.");
    }

    private DiagnosticsService diagnosticsService() {
        MagicRuntime current = runtime;
        if (current == null) {
            return null;
        }
        return current.findComponent(DiagnosticsService.class).orElse(null);
    }

    private static String resolveBundleVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
