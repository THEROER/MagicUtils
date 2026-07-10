package dev.ua.theroer.magicutils.messaging;

import org.jetbrains.annotations.Nullable;

/**
 * In-process transport that delivers published envelopes straight back to the
 * local sink.
 *
 * <p>Useful as a no-network default (single server), and as a test double. Since
 * {@link MessageEnvelope#acceptedBy} rejects a member's own messages, a lone
 * member never sees its own traffic; wire two loopback transports together via
 * {@link #link(LoopbackTransport)} to simulate two members in a unit test.</p>
 */
public final class LoopbackTransport implements MessageTransport {
    private volatile EnvelopeSink sink;
    private volatile @Nullable LoopbackTransport peer;

    /**
     * Creates an unlinked loopback transport.
     */
    public LoopbackTransport() {
    }

    @Override
    public String name() {
        return "loopback";
    }

    @Override
    public void start(EnvelopeSink sink) {
        this.sink = sink;
    }

    /**
     * Links this transport to a peer so published envelopes are delivered to it,
     * emulating a two-member network in-process.
     *
     * @param other the peer transport
     */
    public void link(LoopbackTransport other) {
        this.peer = other;
        other.peer = this;
    }

    @Override
    public void publish(MessageEnvelope envelope) {
        LoopbackTransport target = peer;
        EnvelopeSink localSink = sink;
        if (target != null && target.sink != null) {
            target.sink.accept(envelope);
        }
        // Deliver to self too; the bus filters out own-origin messages.
        if (localSink != null) {
            localSink.accept(envelope);
        }
    }

    @Override
    public void close() {
        sink = null;
        peer = null;
    }
}
