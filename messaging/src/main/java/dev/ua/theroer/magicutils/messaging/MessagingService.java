package dev.ua.theroer.magicutils.messaging;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import dev.ua.theroer.magicutils.messaging.redis.RedisConfig;
import dev.ua.theroer.magicutils.messaging.redis.RedisMessageTransport;
import dev.ua.theroer.magicutils.platform.ListenerSubscription;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import org.jetbrains.annotations.Nullable;

/**
 * High-level entry point for MagicUtils cross-server messaging.
 *
 * <p>Owns a {@link MessageBus} over a chosen {@link MessageTransport} and is the
 * component registered with a {@code MagicRuntime}. The transport is selected at
 * build time: when Redis is enabled in {@link RedisConfig.Redis} a
 * {@link RedisMessageTransport} is used; otherwise the platform's default
 * plugin-messaging transport is used, so the network runs without Redis.</p>
 */
public final class MessagingService implements AutoCloseable {
    private final MessageBus bus;
    private final String transportName;

    private MessagingService(MessageBus bus, String transportName) {
        this.bus = bus;
        this.transportName = transportName;
    }

    /**
     * Creates a service builder.
     *
     * @param self this member's identity
     * @return builder
     */
    public static Builder builder(MessageSource self) {
        return new Builder(self);
    }

    /**
     * Returns the underlying bus.
     *
     * @return message bus
     */
    public MessageBus bus() {
        return bus;
    }

    /**
     * Returns the name of the active transport ("redis" or "plugin-messaging" etc.).
     *
     * @return transport name
     */
    public String transportName() {
        return transportName;
    }

    /**
     * Returns whether the active transport is currently connected.
     *
     * @return true when connected
     */
    public boolean isConnected() {
        return bus.transport().isConnected();
    }

    /**
     * Publishes a payload on a channel to a target. Delegates to the bus.
     *
     * @param target addressing hint
     * @param channel channel name
     * @param payload payload object
     */
    public void publish(Target target, String channel, @Nullable Object payload) {
        bus.publish(target, channel, payload);
    }

    /**
     * Broadcasts a payload on a channel. Delegates to the bus.
     *
     * @param channel channel name
     * @param payload payload object
     */
    public void broadcast(String channel, @Nullable Object payload) {
        bus.broadcast(channel, payload);
    }

    /**
     * Subscribes a typed handler to a channel. Delegates to the bus.
     *
     * @param channel channel name
     * @param type payload type
     * @param handler handler
     * @param <T> payload type
     * @return subscription handle
     */
    public <T> ListenerSubscription subscribe(String channel, Class<T> type, MessageHandler<T> handler) {
        return bus.subscribe(channel, type, handler);
    }

    @Override
    public void close() {
        bus.close();
    }

    /**
     * Builder that wires the transport and bus together.
     */
    public static final class Builder {
        private final MessageSource self;
        private @Nullable RedisConfig.Redis redis;
        private @Nullable Supplier<MessageTransport> defaultTransport;
        private @Nullable MessageCodec codec;
        private PlatformLogger logger = NoopLogger.INSTANCE;
        private @Nullable Predicate<UUID> hostsPlayer;

        private Builder(MessageSource self) {
            this.self = Objects.requireNonNull(self, "self");
        }

        /**
         * Supplies Redis settings; when {@code enabled}, the Redis transport is used.
         *
         * @param redis redis settings (nullable → treated as disabled)
         * @return builder
         */
        public Builder redis(@Nullable RedisConfig.Redis redis) {
            this.redis = redis;
            return this;
        }

        /**
         * Supplies the platform's default (plugin-messaging) transport factory,
         * used when Redis is disabled or unavailable.
         *
         * @param defaultTransport factory for the default transport
         * @return builder
         */
        public Builder defaultTransport(Supplier<MessageTransport> defaultTransport) {
            this.defaultTransport = defaultTransport;
            return this;
        }

        /**
         * Overrides the codec used for envelope/payload serialization.
         *
         * @param codec message codec
         * @return builder
         */
        public Builder codec(MessageCodec codec) {
            this.codec = codec;
            return this;
        }

        /**
         * Sets the logger.
         *
         * @param logger platform logger
         * @return builder
         */
        public Builder logger(PlatformLogger logger) {
            if (logger != null) {
                this.logger = logger;
            }
            return this;
        }

        /**
         * Sets the player-hosting predicate for {@link Target#player(UUID)} delivery.
         *
         * @param hostsPlayer predicate
         * @return builder
         */
        public Builder hostsPlayer(@Nullable Predicate<UUID> hostsPlayer) {
            this.hostsPlayer = hostsPlayer;
            return this;
        }

        /**
         * Builds the service, selecting and starting the transport.
         *
         * @return messaging service
         */
        public MessagingService build() {
            MessageCodec resolvedCodec = codec != null ? codec : new MessageCodec();
            MessageTransport transport = resolveTransport(resolvedCodec);
            MessageBus bus = MessageBus.builder(self, transport)
                    .codec(resolvedCodec)
                    .logger(logger)
                    .hostsPlayer(hostsPlayer)
                    .build();
            return new MessagingService(bus, transport.name());
        }

        private MessageTransport resolveTransport(MessageCodec resolvedCodec) {
            if (redis != null && redis.isEnabled()) {
                try {
                    return new RedisMessageTransport(redis, resolvedCodec, logger);
                } catch (RuntimeException | LinkageError error) {
                    logger.error("Redis transport requested but could not be initialized; "
                            + "falling back to the default transport", asThrowable(error));
                }
            }
            if (defaultTransport != null) {
                MessageTransport transport = defaultTransport.get();
                if (transport != null) {
                    return transport;
                }
            }
            // Last resort: a self-contained loopback so the bus is always usable.
            return new LoopbackTransport();
        }

        private static Throwable asThrowable(Object error) {
            return error instanceof Throwable throwable ? throwable : new RuntimeException(String.valueOf(error));
        }
    }

    private enum NoopLogger implements PlatformLogger {
        INSTANCE;

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void warn(String message, Throwable throwable) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }

        @Override
        public void debug(String message) {
        }
    }
}
