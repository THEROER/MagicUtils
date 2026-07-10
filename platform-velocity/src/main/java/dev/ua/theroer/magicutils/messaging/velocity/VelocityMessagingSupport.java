package dev.ua.theroer.magicutils.messaging.velocity;

import java.util.UUID;
import java.util.function.Consumer;

import com.velocitypowered.api.proxy.ProxyServer;

import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.messaging.MessageCodec;
import dev.ua.theroer.magicutils.messaging.MessageSource;
import dev.ua.theroer.magicutils.messaging.MessageTransport;
import dev.ua.theroer.magicutils.messaging.MessagingService;
import dev.ua.theroer.magicutils.messaging.redis.RedisConfig;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import org.jetbrains.annotations.Nullable;

/**
 * Wires a {@link MessagingService} into a Velocity plugin's {@link MagicRuntime}.
 *
 * <p>When Redis is enabled the Redis transport is used; otherwise the default
 * {@link VelocityPluginMessageTransport} is created and registered as a Velocity
 * event listener so it receives backend {@code Forward} frames. Either way the
 * service is registered as a runtime component and closed with the runtime.</p>
 */
public final class VelocityMessagingSupport {
    private VelocityMessagingSupport() {
    }

    /**
     * Installs the messaging service on the runtime.
     *
     * @param runtime plugin runtime
     * @param proxy velocity proxy
     * @param plugin owning plugin instance
     * @param pluginName plugin name (used to derive the member id)
     * @param redis redis settings, or null to force the default transport
     * @param configurer optional callback to tweak the service builder
     * @return the installed service
     */
    public static MessagingService install(
            MagicRuntime runtime,
            ProxyServer proxy,
            Object plugin,
            String pluginName,
            @Nullable RedisConfig.Redis redis,
            @Nullable Consumer<MessagingService.Builder> configurer) {
        Platform platform = runtime.platform();
        PlatformLogger logger = platform.logger();
        MessageCodec codec = new MessageCodec();

        MessageSource self = MessageSource.proxy(pluginName + ":" + UUID.randomUUID());

        // Track the default transport so it can be registered/unregistered as an
        // event listener; a Redis transport needs no Velocity listener.
        VelocityPluginMessageTransport[] defaultTransport = new VelocityPluginMessageTransport[1];

        MessagingService.Builder builder = MessagingService.builder(self)
                .logger(logger)
                .redis(redis)
                .codec(codec)
                .defaultTransport(() -> {
                    VelocityPluginMessageTransport transport =
                            new VelocityPluginMessageTransport(proxy, plugin, codec, logger);
                    defaultTransport[0] = transport;
                    return transport;
                });

        if (configurer != null) {
            configurer.accept(builder);
        }

        MessagingService service = builder.build();

        MessageTransport active = service.bus().transport();
        if (active == defaultTransport[0] && defaultTransport[0] != null) {
            proxy.getEventManager().register(plugin, defaultTransport[0]);
            runtime.onClose("messaging.listener",
                    () -> proxy.getEventManager().unregisterListener(plugin, defaultTransport[0]));
        }

        runtime.putComponent(MessagingService.class, service);
        runtime.manage("messaging.service", service);
        logger.info("MagicUtils messaging enabled (transport: " + service.transportName() + ")");
        return service;
    }
}
