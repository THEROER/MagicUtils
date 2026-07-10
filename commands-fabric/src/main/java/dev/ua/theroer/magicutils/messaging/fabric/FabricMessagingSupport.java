package dev.ua.theroer.magicutils.messaging.fabric;

import java.util.UUID;
import java.util.function.Consumer;

import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.messaging.MessageSource;
import dev.ua.theroer.magicutils.messaging.MessagingService;
import dev.ua.theroer.magicutils.messaging.redis.RedisConfig;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import org.jetbrains.annotations.Nullable;

/**
 * Wires a {@link MessagingService} into a Fabric mod's {@link MagicRuntime}.
 *
 * <p>Fabric mods use the platform-agnostic Redis transport for cross-server
 * messaging. Unlike the Bukkit/Bungee/Velocity backends there is no default
 * plugin-messaging transport here: riding the proxy's vanilla channel from a mod
 * would require registering a foreign custom-payload id, which modern Minecraft
 * only allows through the platform networking API. So no {@code defaultTransport}
 * is supplied; when Redis is disabled {@link MessagingService} falls back to an
 * in-process loopback transport (see {@code MessagingService.resolveTransport}),
 * which keeps the bus usable but does not reach other servers. Enable Redis for
 * actual cross-server delivery.</p>
 */
public final class FabricMessagingSupport {
    private FabricMessagingSupport() {
    }

    /**
     * Installs the messaging service on the runtime.
     *
     * @param runtime mod runtime
     * @param modName owning mod id, used to scope the message source
     * @param redis redis settings, or null to leave Redis disabled
     * @param configurer optional callback to tweak the service builder
     * @return the installed service
     */
    public static MessagingService install(
            MagicRuntime runtime,
            String modName,
            @Nullable RedisConfig.Redis redis,
            @Nullable Consumer<MessagingService.Builder> configurer) {
        Platform platform = runtime.platform();
        PlatformLogger logger = platform.logger();

        MessageSource self = MessageSource.backend(
                modName + ":" + UUID.randomUUID(),
                null); // server name is not known to a backend until the proxy tells it

        MessagingService.Builder builder = MessagingService.builder(self)
                .logger(logger)
                .redis(redis)
                .hostsPlayer(id -> hostsPlayer(platform, id));

        if (configurer != null) {
            configurer.accept(builder);
        }

        MessagingService service = builder.build();
        runtime.putComponent(MessagingService.class, service);
        runtime.manage("messaging.service", service);
        if (service.transportName().equals("loopback")) {
            logger.info("MagicUtils messaging enabled (transport: loopback) — "
                    + "enable Redis for cross-server delivery on Fabric");
        } else {
            logger.info("MagicUtils messaging enabled (transport: " + service.transportName() + ")");
        }
        return service;
    }

    private static boolean hostsPlayer(Platform platform, UUID id) {
        return id != null && platform.playerById(id) != null;
    }
}
