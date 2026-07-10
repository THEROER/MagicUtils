package dev.ua.theroer.magicutils.messaging.bungee;

import java.util.UUID;
import java.util.function.Consumer;

import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.messaging.MessageCodec;
import dev.ua.theroer.magicutils.messaging.MessageSource;
import dev.ua.theroer.magicutils.messaging.MessageTransport;
import dev.ua.theroer.magicutils.messaging.MessagingService;
import dev.ua.theroer.magicutils.messaging.redis.RedisConfig;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Wires a {@link MessagingService} into a BungeeCord plugin's {@link MagicRuntime}.
 *
 * <p>Mirror of the Velocity support: Redis when enabled, otherwise the default
 * {@link BungeePluginMessageTransport} registered as a Bungee listener. Service
 * registered as a runtime component and closed with the runtime.</p>
 */
public final class BungeeMessagingSupport {
    private BungeeMessagingSupport() {
    }

    /**
     * Installs the messaging service on the runtime.
     *
     * @param runtime plugin runtime
     * @param proxy bungee proxy
     * @param plugin owning plugin
     * @param pluginName plugin name (used to derive the member id)
     * @param redis redis settings, or null to force the default transport
     * @param configurer optional callback to tweak the service builder
     * @return the installed service
     */
    public static MessagingService install(
            MagicRuntime runtime,
            ProxyServer proxy,
            Plugin plugin,
            String pluginName,
            @Nullable RedisConfig.Redis redis,
            @Nullable Consumer<MessagingService.Builder> configurer) {
        Platform platform = runtime.platform();
        PlatformLogger logger = platform.logger();
        MessageCodec codec = new MessageCodec();

        MessageSource self = MessageSource.proxy(pluginName + ":" + UUID.randomUUID());

        BungeePluginMessageTransport[] defaultTransport = new BungeePluginMessageTransport[1];

        MessagingService.Builder builder = MessagingService.builder(self)
                .logger(logger)
                .redis(redis)
                .codec(codec)
                .defaultTransport(() -> {
                    BungeePluginMessageTransport transport =
                            new BungeePluginMessageTransport(proxy, plugin, codec, logger);
                    defaultTransport[0] = transport;
                    return transport;
                });

        if (configurer != null) {
            configurer.accept(builder);
        }

        MessagingService service = builder.build();

        MessageTransport active = service.bus().transport();
        if (active == defaultTransport[0] && defaultTransport[0] != null) {
            proxy.getPluginManager().registerListener(plugin, defaultTransport[0]);
            runtime.onClose("messaging.listener",
                    () -> proxy.getPluginManager().unregisterListener(defaultTransport[0]));
        }

        runtime.putComponent(MessagingService.class, service);
        runtime.manage("messaging.service", service);
        logger.info("MagicUtils messaging enabled (transport: " + service.transportName() + ")");
        return service;
    }
}
