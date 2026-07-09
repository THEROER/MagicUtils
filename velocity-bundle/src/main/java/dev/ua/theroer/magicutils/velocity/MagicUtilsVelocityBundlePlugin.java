package dev.ua.theroer.magicutils.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.bootstrap.VelocityBootstrap;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsCommandSupport;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsService;
import dev.ua.theroer.magicutils.diagnostics.MagicUtilsBundleCommand;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerInfo;
import dev.ua.theroer.magicutils.platform.velocity.VelocityMagicUtilsConsumerRegistry;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Standalone MagicUtils Velocity plugin. Boots a shared {@link MagicRuntime} via
 * {@link VelocityBootstrap}, enables diagnostics and registers the
 * {@code /magicutils} (alias {@code mu}) command with diagnostics sub-commands.
 *
 * <p>This is the proxy-side counterpart to {@code bukkit-bundle} and
 * {@code fabric-bundle}: a drop-in plugin server owners can install so downstream
 * MagicUtils consumers on the proxy share one runtime instead of shading their
 * own copy. Consumer plugins register with the shared runtime through
 * {@link VelocityMagicUtilsConsumerRegistry} so {@code /magicutils mods} lists
 * them live.</p>
 *
 * <p>The Velocity plugin descriptor is the hand-authored
 * {@code velocity-plugin.json} resource (not the {@code @Plugin} annotation
 * processor), so its {@code version} is filled from the build's Gradle version
 * via {@code processResources} token expansion, matching the {@code plugin.yml}
 * flow of {@code bukkit-bundle}.</p>
 */
public final class MagicUtilsVelocityBundlePlugin {
    private static final String PLUGIN_NAME = "MagicUtils";

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private MagicRuntime runtime;

    /**
     * Velocity injects the proxy, plugin logger and data directory.
     *
     * @param proxy the Velocity proxy server
     * @param logger the plugin's SLF4J logger
     * @param dataDirectory the plugin data directory
     */
    @Inject
    public MagicUtilsVelocityBundlePlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    /**
     * Boots the shared runtime once the proxy is initialized.
     *
     * @param event the proxy initialize event
     */
    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        VelocityBootstrap.RuntimeResult bootstrap = VelocityBootstrap
                .forPlugin(proxy, this, PLUGIN_NAME, dataDirectory)
                .slf4j(logger)
                .initLanguage(true)
                // Load the bundled MagicUtils translations into this plugin's own
                // language scope and register that scope, so the bundle command's
                // `@magicutils.*` descriptions (e.g. help) resolve instead of
                // showing the raw key. The global Messages manager is left alone
                // (setMessagesManager stays off) so consumer plugins keep theirs.
                .registerMessages(true)
                .addMagicUtilsMessages(true)
                .bindLoggerLanguage(false)
                .setMessagesManager(false)
                .enableCommands()
                .enableDiagnostics()
                .permissionPrefix("magicutils")
                .buildRuntime();
        runtime = bootstrap.runtime();

        LoggerCore loggerCore = bootstrap.logger();
        CommandRegistry commandRegistry = bootstrap.commandRegistry();
        if (commandRegistry != null) {
            // Register the diagnostics suite-name parser so
            // `/magicutils diagnostics suite <name>` resolves. Velocity commands
            // register immediately (no re-registration callback like Fabric), so
            // the parser is added exactly once here.
            DiagnosticsCommandSupport.registerTypeParsers(commandRegistry.commandManager());
            commandRegistry.registerCommand(new MagicUtilsBundleCommand(
                    loggerCore,
                    resolveBundleVersion(),
                    VelocityMagicUtilsConsumerRegistry::snapshot,
                    VelocityMagicUtilsConsumerRegistry::find,
                    this::diagnosticsService,
                    commandRegistry::commandManager));
        }

        logger.info("MagicUtils Velocity bundle loaded.");
    }

    /**
     * Tears down the shared runtime on proxy shutdown.
     *
     * @param event the proxy shutdown event
     */
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        MagicRuntime current = runtime;
        if (current != null) {
            current.close();
            runtime = null;
        }
    }

    private @Nullable DiagnosticsService diagnosticsService() {
        MagicRuntime current = runtime;
        if (current == null) {
            return null;
        }
        return current.findComponent(DiagnosticsService.class).orElse(null);
    }

    private String resolveBundleVersion() {
        return proxy.getPluginManager().fromInstance(this)
                .map(PluginContainer::getDescription)
                .flatMap(description -> description.getVersion())
                .orElse("unknown");
    }
}
