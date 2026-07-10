package dev.ua.theroer.magicutils.messaging;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class MessageCodecTest {
    private final MessageCodec codec = new MessageCodec();

    @Test
    void payloadRoundTrips() {
        Sample sample = new Sample("hello", 42);
        byte[] bytes = codec.encodePayload(sample);
        Sample decoded = codec.decodePayload(bytes, Sample.class);
        assertEquals(sample, decoded);
    }

    @Test
    void nullPayloadEncodesEmptyAndDecodesNull() {
        byte[] bytes = codec.encodePayload(null);
        assertEquals(0, bytes.length);
        assertNull(codec.decodePayload(bytes, Sample.class));
    }

    @Test
    void envelopeRoundTripsServerTarget() {
        MessageSource source = MessageSource.backend("proc-1", "lobby");
        byte[] payload = codec.encodePayload(new Sample("x", 1));
        MessageEnvelope envelope = MessageEnvelope.create("chan", Target.server("survival"), source, payload);

        MessageEnvelope decoded = codec.decode(codec.encode(envelope));

        assertEquals(envelope.id(), decoded.id());
        assertEquals("chan", decoded.channel());
        assertEquals(Target.Kind.SERVER, decoded.target().kind());
        assertEquals("survival", decoded.target().serverName());
        assertEquals(MessageSource.Type.BACKEND, decoded.source().type());
        assertEquals("lobby", decoded.source().serverName());
        assertArrayEquals(payload, decoded.payload());
    }

    @Test
    void envelopeRoundTripsPlayerTarget() {
        UUID player = UUID.randomUUID();
        MessageSource source = MessageSource.proxy("proxy-1");
        MessageEnvelope envelope = MessageEnvelope.create("chan", Target.player(player), source, new byte[0]);

        MessageEnvelope decoded = codec.decode(codec.encode(envelope));

        assertEquals(Target.Kind.PLAYER, decoded.target().kind());
        assertEquals(player, decoded.target().playerId());
        assertEquals(MessageSource.Type.PROXY, decoded.source().type());
    }

    record Sample(String name, int value) {
    }
}
