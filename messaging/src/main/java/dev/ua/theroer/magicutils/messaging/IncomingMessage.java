package dev.ua.theroer.magicutils.messaging;

/**
 * A received message as seen by a typed subscriber.
 *
 * <p>Lazily decodes the payload into the subscriber's requested type on first
 * access to {@link #payload()}, so a handler that only inspects the
 * {@link #source() source} never pays the deserialization cost.</p>
 *
 * @param <T> payload type
 */
public final class IncomingMessage<T> {
    private final MessageEnvelope envelope;
    private final MessageCodec codec;
    private final Class<T> type;
    private boolean decoded;
    private T payload;

    IncomingMessage(MessageEnvelope envelope, MessageCodec codec, Class<T> type) {
        this.envelope = envelope;
        this.codec = codec;
        this.type = type;
    }

    /**
     * Returns the channel this message arrived on.
     *
     * @return channel name
     */
    public String channel() {
        return envelope.channel();
    }

    /**
     * Returns the member that sent this message.
     *
     * @return originating source
     */
    public MessageSource source() {
        return envelope.source();
    }

    /**
     * Returns the addressing hint the sender used.
     *
     * @return target
     */
    public Target target() {
        return envelope.target();
    }

    /**
     * Returns the underlying envelope for advanced use.
     *
     * @return envelope
     */
    public MessageEnvelope envelope() {
        return envelope;
    }

    /**
     * Returns the decoded payload, deserializing on first access.
     *
     * @return decoded payload (may be null for empty payloads)
     */
    public T payload() {
        if (!decoded) {
            payload = codec.decodePayload(envelope.payload(), type);
            decoded = true;
        }
        return payload;
    }
}
