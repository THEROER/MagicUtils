package dev.ua.theroer.magicutils.messaging;

import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * Wire representation of a single message on the bus.
 *
 * <p>The envelope is transport-neutral: both the default plugin-messaging
 * transport and the Redis transport serialize it to bytes (via
 * {@link MessageCodec}) and reconstruct it on the receiving side. It carries the
 * logical {@code channel}, the {@link Target addressing hint}, the originating
 * {@link MessageSource}, and the already-serialized {@code payload} bytes so the
 * transport never needs to understand the user payload type.</p>
 */
public final class MessageEnvelope {
    private final UUID id;
    private final String channel;
    private final Target target;
    private final MessageSource source;
    private final byte[] payload;
    private final long timestamp;

    /**
     * Creates an envelope.
     *
     * @param id unique message id (for de-duplication / correlation)
     * @param channel logical channel name
     * @param target addressing hint
     * @param source originating member
     * @param payload serialized payload bytes
     * @param timestamp epoch millis when the message was created
     */
    public MessageEnvelope(UUID id, String channel, Target target, MessageSource source,
                           byte[] payload, long timestamp) {
        this.id = Objects.requireNonNull(id, "id");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.target = Objects.requireNonNull(target, "target");
        this.source = Objects.requireNonNull(source, "source");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.timestamp = timestamp;
    }

    /**
     * Creates an envelope with a fresh id and the current timestamp.
     *
     * @param channel logical channel name
     * @param target addressing hint
     * @param source originating member
     * @param payload serialized payload bytes
     * @return new envelope
     */
    public static MessageEnvelope create(String channel, Target target, MessageSource source, byte[] payload) {
        return new MessageEnvelope(UUID.randomUUID(), channel, target, source, payload, System.currentTimeMillis());
    }

    /**
     * Returns the unique message id.
     *
     * @return message id
     */
    public UUID id() {
        return id;
    }

    /**
     * Returns the logical channel name.
     *
     * @return channel name
     */
    public String channel() {
        return channel;
    }

    /**
     * Returns the addressing hint.
     *
     * @return target
     */
    public Target target() {
        return target;
    }

    /**
     * Returns the originating member.
     *
     * @return source
     */
    public MessageSource source() {
        return source;
    }

    /**
     * Returns the serialized payload bytes.
     *
     * @return payload bytes (do not mutate)
     */
    public byte[] payload() {
        return payload;
    }

    /**
     * Returns the creation timestamp (epoch millis).
     *
     * @return timestamp
     */
    public long timestamp() {
        return timestamp;
    }

    /**
     * Decides whether a local member should accept this envelope given the target.
     *
     * <p>Transports that cannot pre-filter (for example Redis pub/sub, which
     * broadcasts to every subscriber) call this on receipt so addressing is honoured
     * uniformly regardless of transport.</p>
     *
     * @param self the local member
     * @param hostsPlayer predicate answering whether the local member hosts a player id;
     *                    may be null on the proxy or when player hosting is unknown
     * @return true when the local member is an intended recipient
     */
    public boolean acceptedBy(MessageSource self, @Nullable java.util.function.Predicate<UUID> hostsPlayer) {
        Objects.requireNonNull(self, "self");
        // Never deliver a message back to its own originator.
        if (self.id().equals(source.id())) {
            return false;
        }
        return switch (target.kind()) {
            case BROADCAST -> true;
            case PROXY -> self.isProxy();
            case ALL_BACKENDS -> !self.isProxy();
            case SERVER -> !self.isProxy()
                    && target.serverName() != null
                    && target.serverName().equalsIgnoreCase(self.serverName());
            case PLAYER -> !self.isProxy()
                    && hostsPlayer != null
                    && target.playerId() != null
                    && hostsPlayer.test(target.playerId());
        };
    }
}
