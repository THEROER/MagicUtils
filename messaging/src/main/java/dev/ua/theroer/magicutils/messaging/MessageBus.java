package dev.ua.theroer.magicutils.messaging;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import dev.ua.theroer.magicutils.platform.ListenerSubscription;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import org.jetbrains.annotations.Nullable;

/**
 * Typed cross-server publish/subscribe bus for MagicUtils plugins.
 *
 * <p>Wraps a pluggable {@link MessageTransport} (the default per-platform
 * plugin-messaging transport, or Redis) with a channel-oriented, typed API:
 * {@link #subscribe(String, Class, MessageHandler)} and
 * {@link #publish(Target, String, Object)}. Payloads are serialized with a shared
 * {@link MessageCodec}. The bus applies {@link Target} addressing on receipt so
 * routing behaves identically regardless of which transport is in use.</p>
 *
 * <p>The bus is {@link AutoCloseable} and is meant to be registered with a
 * {@code MagicRuntime} so it is torn down with the plugin.</p>
 */
public final class MessageBus implements AutoCloseable {
    private final MessageSource self;
    private final MessageTransport transport;
    private final MessageCodec codec;
    private final PlatformLogger logger;
    private final Map<String, CopyOnWriteArrayList<Subscription<?>>> subscriptions = new ConcurrentHashMap<>();
    private volatile @Nullable Predicate<UUID> hostsPlayer;
    private volatile boolean closed;

    private MessageBus(Builder builder) {
        this.self = builder.self;
        this.transport = builder.transport;
        this.codec = builder.codec != null ? builder.codec : new MessageCodec();
        this.logger = builder.logger;
        this.hostsPlayer = builder.hostsPlayer;
        this.transport.start(this::dispatch);
    }

    /**
     * Creates a bus builder.
     *
     * @param self this member's identity
     * @param transport underlying transport
     * @return builder
     */
    public static Builder builder(MessageSource self, MessageTransport transport) {
        return new Builder(self, transport);
    }

    /**
     * Returns this member's identity.
     *
     * @return self source
     */
    public MessageSource self() {
        return self;
    }

    /**
     * Returns the underlying transport.
     *
     * @return transport
     */
    public MessageTransport transport() {
        return transport;
    }

    /**
     * Returns the codec used for payload serialization.
     *
     * @return message codec
     */
    public MessageCodec codec() {
        return codec;
    }

    /**
     * Sets the predicate answering whether this member hosts a given player id.
     *
     * <p>Used to honour {@link Target#player(UUID)} on backends. Ignored on the
     * proxy. May be updated at any time (for example wired to the online-player
     * registry).</p>
     *
     * @param hostsPlayer predicate, or null to disable player-target delivery
     */
    public void hostsPlayer(@Nullable Predicate<UUID> hostsPlayer) {
        this.hostsPlayer = hostsPlayer;
    }

    /**
     * Publishes a payload on a channel to the given target.
     *
     * @param target addressing hint
     * @param channel logical channel name
     * @param payload payload object (serialized with the shared codec; may be null)
     */
    public void publish(Target target, String channel, @Nullable Object payload) {
        if (closed) {
            throw new IllegalStateException("MessageBus is closed");
        }
        byte[] bytes = codec.encodePayload(payload);
        MessageEnvelope envelope = MessageEnvelope.create(channel, target, self, bytes);
        try {
            transport.publish(envelope);
        } catch (RuntimeException error) {
            logger.warn("Failed to publish message on channel '" + channel + "'", error);
            throw error;
        }
    }

    /**
     * Broadcasts a payload on a channel to every subscriber on the network.
     *
     * @param channel logical channel name
     * @param payload payload object
     */
    public void broadcast(String channel, @Nullable Object payload) {
        publish(Target.broadcast(), channel, payload);
    }

    /**
     * Subscribes a typed handler to a channel.
     *
     * @param channel logical channel name
     * @param type payload type
     * @param handler message handler
     * @param <T> payload type
     * @return subscription handle; close it to unsubscribe
     */
    public <T> ListenerSubscription subscribe(String channel, Class<T> type, MessageHandler<T> handler) {
        Subscription<T> subscription = new Subscription<>(channel, type, handler);
        subscriptions.computeIfAbsent(channel, key -> new CopyOnWriteArrayList<>()).add(subscription);
        return () -> {
            CopyOnWriteArrayList<Subscription<?>> list = subscriptions.get(channel);
            if (list != null) {
                list.remove(subscription);
                if (list.isEmpty()) {
                    subscriptions.remove(channel, list);
                }
            }
        };
    }

    private void dispatch(MessageEnvelope envelope) {
        if (closed) {
            return;
        }
        if (!envelope.acceptedBy(self, hostsPlayer)) {
            return;
        }
        List<Subscription<?>> handlers = subscriptions.get(envelope.channel());
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        for (Subscription<?> subscription : handlers) {
            deliver(subscription, envelope);
        }
    }

    private <T> void deliver(Subscription<T> subscription, MessageEnvelope envelope) {
        try {
            IncomingMessage<T> message = new IncomingMessage<>(envelope, codec, subscription.type);
            subscription.handler.onMessage(message);
        } catch (RuntimeException error) {
            logger.warn("Messaging handler for channel '" + envelope.channel() + "' threw", error);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        subscriptions.clear();
        try {
            transport.close();
        } catch (Exception error) {
            logger.warn("Failed to close messaging transport '" + transport.name() + "'", error);
        }
    }

    private record Subscription<T>(String channel, Class<T> type, MessageHandler<T> handler) {
    }

    /**
     * Builder for {@link MessageBus}.
     */
    public static final class Builder {
        private final MessageSource self;
        private final MessageTransport transport;
        private @Nullable MessageCodec codec;
        private PlatformLogger logger = NoopLogger.INSTANCE;
        private @Nullable Predicate<UUID> hostsPlayer;

        private Builder(MessageSource self, MessageTransport transport) {
            this.self = java.util.Objects.requireNonNull(self, "self");
            this.transport = java.util.Objects.requireNonNull(transport, "transport");
        }

        /**
         * Overrides the codec (and thus the Jackson mapper) used for payloads.
         *
         * @param codec message codec
         * @return builder
         */
        public Builder codec(MessageCodec codec) {
            this.codec = codec;
            return this;
        }

        /**
         * Sets the logger for transport/handler failures.
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
         * Sets the initial player-hosting predicate for {@link Target#player(UUID)} delivery.
         *
         * @param hostsPlayer predicate, or null
         * @return builder
         */
        public Builder hostsPlayer(@Nullable Predicate<UUID> hostsPlayer) {
            this.hostsPlayer = hostsPlayer;
            return this;
        }

        /**
         * Builds and starts the bus.
         *
         * @return message bus
         */
        public MessageBus build() {
            return new MessageBus(this);
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
