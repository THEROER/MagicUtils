package dev.ua.theroer.magicutils.messaging;

/**
 * Low-level transport that moves encoded envelopes between network members.
 *
 * <p>A transport knows nothing about payload types or channels beyond routing:
 * it accepts an already-encoded {@link MessageEnvelope} on {@link #publish},
 * and pushes decoded envelopes it receives to the {@link EnvelopeSink} supplied
 * at {@link #start}. The two shipped implementations are the default
 * plugin-messaging transport (per platform) and the Redis pub/sub transport;
 * both are interchangeable behind this SPI, so a network can run with or without
 * Redis.</p>
 */
public interface MessageTransport extends AutoCloseable {
    /**
     * Receives envelopes arriving from the network.
     */
    @FunctionalInterface
    interface EnvelopeSink {
        /**
         * Accepts a received envelope for local dispatch.
         *
         * @param envelope decoded envelope
         */
        void accept(MessageEnvelope envelope);
    }

    /**
     * Human-readable transport name for diagnostics/logging (for example "plugin-messaging", "redis").
     *
     * @return transport name
     */
    String name();

    /**
     * Starts the transport and begins delivering received envelopes to the sink.
     *
     * <p>Called once before any {@link #publish}. Implementations that need a
     * background subscriber thread or channel registration set it up here.</p>
     *
     * @param sink destination for received envelopes
     */
    void start(EnvelopeSink sink);

    /**
     * Publishes an encoded envelope to the network.
     *
     * <p>The envelope's {@link Target} is advisory; transports that cannot route
     * precisely broadcast and rely on receiver-side filtering.</p>
     *
     * @param envelope envelope to send
     */
    void publish(MessageEnvelope envelope);

    /**
     * Returns whether the transport is currently connected/operational.
     *
     * <p>The default plugin-messaging transport reports connectivity based on
     * whether the platform channel is registered; Redis reports pool health.</p>
     *
     * @return true when the transport can currently deliver messages
     */
    default boolean isConnected() {
        return true;
    }

    /**
     * Stops the transport and releases resources.
     */
    @Override
    void close();
}
