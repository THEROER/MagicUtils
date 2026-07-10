package dev.ua.theroer.magicutils.messaging;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Serializes {@link MessageEnvelope} instances and user payloads to/from bytes.
 *
 * <p>The envelope header is encoded as compact JSON; the user payload is encoded
 * separately by {@link #encodePayload(Object)} and carried as opaque bytes inside
 * the envelope. This keeps the transport free of any knowledge of user types and
 * lets a single codec instance be shared across transports.</p>
 */
public final class MessageCodec {
    private final ObjectMapper mapper;

    /**
     * Creates a codec with a default {@link ObjectMapper}.
     */
    public MessageCodec() {
        this(new ObjectMapper());
    }

    /**
     * Creates a codec backed by the given mapper.
     *
     * @param mapper Jackson mapper used for both envelope and payloads
     */
    public MessageCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Returns the underlying mapper (for advanced payload handling).
     *
     * @return object mapper
     */
    public ObjectMapper mapper() {
        return mapper;
    }

    /**
     * Serializes a user payload object to bytes.
     *
     * @param payload payload object (may be null → empty bytes)
     * @return serialized payload bytes
     */
    public byte[] encodePayload(Object payload) {
        if (payload == null) {
            return new byte[0];
        }
        try {
            return mapper.writeValueAsBytes(payload);
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to serialize messaging payload", error);
        }
    }

    /**
     * Deserializes payload bytes into the requested type.
     *
     * @param payload serialized payload bytes
     * @param type target type
     * @param <T> payload type
     * @return deserialized payload, or null when the bytes are empty
     */
    public <T> T decodePayload(byte[] payload, Class<T> type) {
        if (payload == null || payload.length == 0) {
            return null;
        }
        try {
            return mapper.readValue(payload, type);
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to deserialize messaging payload", error);
        }
    }

    /**
     * Serializes a full envelope (header + embedded payload) to bytes.
     *
     * @param envelope envelope to encode
     * @return serialized envelope bytes
     */
    public byte[] encode(MessageEnvelope envelope) {
        ObjectNode root = mapper.createObjectNode();
        root.put("id", envelope.id().toString());
        root.put("channel", envelope.channel());
        root.put("ts", envelope.timestamp());

        ObjectNode target = root.putObject("target");
        target.put("kind", envelope.target().kind().name());
        if (envelope.target().serverName() != null) {
            target.put("server", envelope.target().serverName());
        }
        if (envelope.target().playerId() != null) {
            target.put("player", envelope.target().playerId().toString());
        }

        ObjectNode source = root.putObject("source");
        source.put("id", envelope.source().id());
        source.put("type", envelope.source().type().name());
        if (envelope.source().serverName() != null) {
            source.put("server", envelope.source().serverName());
        }

        root.put("payload", envelope.payload());
        try {
            return mapper.writeValueAsBytes(root);
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to serialize messaging envelope", error);
        }
    }

    /**
     * Deserializes envelope bytes back into an {@link MessageEnvelope}.
     *
     * @param bytes serialized envelope bytes
     * @return decoded envelope
     */
    public MessageEnvelope decode(byte[] bytes) {
        try {
            JsonNode root = mapper.readTree(bytes);
            UUID id = UUID.fromString(root.path("id").asText());
            String channel = root.path("channel").asText();
            long ts = root.path("ts").asLong();

            JsonNode targetNode = root.path("target");
            Target target = decodeTarget(targetNode);

            JsonNode sourceNode = root.path("source");
            MessageSource.Type type = MessageSource.Type.valueOf(sourceNode.path("type").asText());
            String sourceId = sourceNode.path("id").asText();
            String sourceServer = sourceNode.hasNonNull("server") ? sourceNode.get("server").asText() : null;
            MessageSource source = type == MessageSource.Type.PROXY
                    ? MessageSource.proxy(sourceId)
                    : MessageSource.backend(sourceId, sourceServer);

            byte[] payload = root.path("payload").binaryValue();
            if (payload == null) {
                payload = new byte[0];
            }
            return new MessageEnvelope(id, channel, target, source, payload, ts);
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to deserialize messaging envelope", error);
        }
    }

    private static Target decodeTarget(JsonNode targetNode) {
        Target.Kind kind = Target.Kind.valueOf(targetNode.path("kind").asText());
        return switch (kind) {
            case BROADCAST -> Target.broadcast();
            case ALL_BACKENDS -> Target.allBackends();
            case PROXY -> Target.proxy();
            case SERVER -> Target.server(targetNode.path("server").asText());
            case PLAYER -> Target.player(UUID.fromString(targetNode.path("player").asText()));
        };
    }
}
