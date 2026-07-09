package dev.ua.theroer.magicutils.bungee;

import dev.ua.theroer.magicutils.bootstrap.BungeeBootstrap;
import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsCommandSupport;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsService;
import dev.ua.theroer.magicutils.diagnostics.MagicUtilsBundleCommand;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.bungee.BungeeMagicUtilsConsumerRegistry;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Standalone MagicUtils BungeeCord plugin. Boots a shared {@link MagicRuntime}
 * via {@link BungeeBootstrap}, enables diagnostics and registers the
 * {@code /magicutils} (alias {@code mu}) command with diagnostics sub-commands.
 *
 * <p>This is the BungeeCord-side counterpart to {@code velocity-bundle},
 * {@code bukkit-bundle} and {@code fabric-bundle}: a drop-in plugin server owners
 * can install so downstream MagicUtils consumers on the proxy share one runtime
 * instead of shading their own copy. Consumer plugins register with the shared
 * runtime through {@link BungeeMagicUtilsConsumerRegistry} so
 * {@code /magicutils mods} lists them live.</p>
 *
 * <p>The BungeeCord plugin descriptor is the hand-authored {@code plugin.yml}
 * resource, so its {@code version} is filled from the build's Gradle version via
 * {@code processResources} token expansion, matching the {@code plugin.yml} flow
 * of {@code bukkit-bundle} and the {@code velocity-plugin.json} flow of
 * {@code velocity-bundle}.</p>
 */
public final class MagicUtilsBungeeBundlePlugin extends Plugin {
    private static final String PLUGIN_NAME = "MagicUtils";

    private MagicRuntime runtime;

    /**
     * Boots the shared runtime when the proxy enables the plugin.
     */
    @Override
    public void onEnable() {
        BungeeBootstrap.RuntimeResult bootstrap = BungeeBootstrap
                .forPlugin(this, PLUGIN_NAME)
                .logger(getLogger())
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
            // `/magicutils diagnostics suite <name>` resolves. Bungee commands
            // register immediately (no re-registration callback like Fabric), so
            // the parser is added exactly once here.
            DiagnosticsCommandSupport.registerTypeParsers(commandRegistry.commandManager());
            commandRegistry.registerCommand(new MagicUtilsBundleCommand(
                    loggerCore,
                    resolveBundleVersion(),
                    BungeeMagicUtilsConsumerRegistry::snapshot,
                    BungeeMagicUtilsConsumerRegistry::find,
                    this::diagnosticsService,
                    commandRegistry::commandManager));
        }

        getLogger().info("MagicUtils Bungee bundle loaded.");
    }

    /**
     * Tears down the shared runtime when the proxy disables the plugin.
     */
    @Override
    public void onDisable() {
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
        String version = getDescription().getVersion();
        return version != null && !version.isBlank() ? version : "unknown";
    }
}
