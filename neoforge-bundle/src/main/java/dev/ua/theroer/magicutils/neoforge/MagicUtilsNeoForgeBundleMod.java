package dev.ua.theroer.magicutils.neoforge;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.bootstrap.NeoForgeBootstrap;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsCommandSupport;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsService;
import dev.ua.theroer.magicutils.diagnostics.MagicUtilsBundleCommand;
import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerRegistry;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Standalone MagicUtils NeoForge mod. Boots a shared {@link MagicRuntime} via
 * {@link NeoForgeBootstrap}, enables diagnostics and registers the
 * {@code /magicutils} (alias {@code mu}) command with diagnostics sub-commands —
 * the NeoForge counterpart of the Bukkit and Fabric bundles.
 *
 * <p>Shipped as its own mod (own {@code neoforge.mods.toml}) so consumer mods can
 * jar-in-jar it and get the shared runtime + command without shading MagicUtils
 * into their own classes.</p>
 */
@Mod(MagicUtilsNeoForgeBundleMod.MOD_ID)
public final class MagicUtilsNeoForgeBundleMod {
    public static final String MOD_ID = "magicutils";
    private static final String MOD_NAME = "MagicUtils";

    private MagicRuntime runtime;

    public MagicUtilsNeoForgeBundleMod(IEventBus modEventBus, ModContainer modContainer) {
        NeoForgeBootstrap.RuntimeResult bootstrap = NeoForgeBootstrap
                .forMod(MOD_NAME, ServerLifecycleHooks::getCurrentServer)
                // Own language scope so the command's `@magicutils.*` descriptions
                // resolve, without touching the global Messages manager consumer
                // mods rely on (mirrors the Fabric bundle's settings).
                .initLanguage(true)
                .registerMessages(true)
                .addMagicUtilsMessages(true)
                .bindLoggerLanguage(false)
                .setMessagesManager(false)
                .enableCommands()
                .enableDiagnostics()
                .permissionPrefix("magicutils")
                .buildRuntime();
        this.runtime = bootstrap.runtime();

        Logger logger = bootstrap.logger();
        LoggerCore loggerCore = logger != null ? logger.getCore() : null;
        CommandRegistry commandRegistry = bootstrap.commandRegistry();
        final String bundleVersion = resolveBundleVersion(modContainer);

        if (commandRegistry != null) {
            // Register the diagnostics suite-name parser once (so
            // `/magicutils diagnostics suite <name>` resolves). The command
            // manager exists as soon as commands are enabled.
            DiagnosticsCommandSupport.registerTypeParsers(commandRegistry.commandManager());
            NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                    CommandRegistry.registerAll(MOD_NAME, event.getDispatcher(),
                            new MagicUtilsBundleCommand(
                                    loggerCore,
                                    bundleVersion,
                                    MagicUtilsConsumerRegistry::snapshot,
                                    MagicUtilsConsumerRegistry::find,
                                    this::diagnosticsService,
                                    commandRegistry::commandManager)));
        }

        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            MagicRuntime current = runtime;
            if (current != null) {
                current.close();
                runtime = null;
            }
        });
    }

    private static String resolveBundleVersion(ModContainer modContainer) {
        try {
            return modContainer.getModInfo().getVersion().toString();
        } catch (RuntimeException versionError) {
            // Defensive: reading the version can resolve a maven range against the
            // game version, which is unavailable this early on some setups. Fall back
            // rather than aborting the mod constructor.
            return "unknown";
        }
    }

    private DiagnosticsService diagnosticsService() {
        MagicRuntime current = runtime;
        if (current == null) {
            return null;
        }
        return current.findComponent(DiagnosticsService.class).orElse(null);
    }
}
