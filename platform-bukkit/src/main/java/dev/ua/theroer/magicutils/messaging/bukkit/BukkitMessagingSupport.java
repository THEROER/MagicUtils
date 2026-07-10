package dev.ua.theroer.magicutils.messaging.bukkit;

import java.util.UUID;
import java.util.function.Consumer;

import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.messaging.MessageCodec;
import dev.ua.theroer.magicutils.messaging.MessageSource;
import dev.ua.theroer.magicutils.messaging.MessagingService;
import dev.ua.theroer.magicutils.messaging.redis.RedisConfig;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * Wires a {@link MessagingService} into a Bukkit plugin's {@link MagicRuntime}.
 *
 * <p>Selects the Redis transport when {@code redis.enabled}, otherwise the
 * {@link BukkitPluginMessageTransport default plugin-messaging transport}, so the
 * plugin works with or without Redis. The service is registered as a runtime
 * component and closed with the runtime.</p>
 */
public final class BukkitMessagingSupport {
    private BukkitMessagingSupport() {
    }

    /**
     * Installs the messaging service on the runtime.
     *
     * @param runtime plugin runtime
     * @param plugin owning plugin
     * @param redis redis settings, or null to force the default transport
     * @param configurer optional callback to tweak the service builder
     * @return the installed service
     */
    public static MessagingService install(
            MagicRuntime runtime,
            JavaPlugin plugin,
            @Nullable RedisConfig.Redis redis,
            @Nullable Consumer<MessagingService.Builder> configurer) {
        Platform platform = runtime.platform();
        PlatformLogger logger = platform.logger();

        MessageSource self = MessageSource.backend(
                plugin.getName() + ":" + UUID.randomUUID(),
                null); // server name is not known to a backend until the proxy tells it

        MessagingService.Builder builder = MessagingService.builder(self)
                .logger(logger)
                .redis(redis)
                .defaultTransport(() -> new BukkitPluginMessageTransport(plugin, new MessageCodec(), logger))
                .hostsPlayer(id -> hostsPlayer(platform, id));

        if (configurer != null) {
            configurer.accept(builder);
        }

        MessagingService service = builder.build();
        runtime.putComponent(MessagingService.class, service);
        runtime.manage("messaging.service", service);
        logger.info("MagicUtils messaging enabled (transport: " + service.transportName() + ")");
        return service;
    }

    private static boolean hostsPlayer(Platform platform, UUID id) {
        if (platform.playerById(id) != null) {
            return true;
        }
        // Fallback for platforms whose audience lookup lags the connection state.
        Player player = Bukkit.getPlayer(id);
        return player != null && player.isOnline();
    }
}
