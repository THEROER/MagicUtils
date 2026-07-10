package dev.ua.theroer.magicutils.messaging.velocity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.LegacyChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import dev.ua.theroer.magicutils.messaging.MessageCodec;
import dev.ua.theroer.magicutils.messaging.MessageEnvelope;
import dev.ua.theroer.magicutils.messaging.MessageTransport;
import dev.ua.theroer.magicutils.messaging.Target;
import dev.ua.theroer.magicutils.platform.PlatformLogger;

/**
 * Default (Redis-less) messaging transport for the Velocity proxy.
 *
 * <p>Listens on the BungeeCord plugin channel for {@code Forward} frames sent by
 * backends and hands the embedded envelope to the bus so proxy-side subscribers
 * receive backend messages. To send from the proxy to backends, it wraps the
 * envelope in a {@code Forward} frame and dispatches it to the relevant
 * server(s), which Velocity relays to that server's backend plugin.</p>
 *
 * <p>The transport must be registered as a Velocity event listener via
 * {@code proxy.getEventManager().register(plugin, transport)} (done by the
 * bootstrap wiring).</p>
 */
public final class VelocityPluginMessageTransport implements MessageTransport {
    private static final LegacyChannelIdentifier BUNGEE_CHANNEL = new LegacyChannelIdentifier("BungeeCord");
    private static final String FORWARD_SUBCHANNEL = "magicutils:bus";

    private final ProxyServer proxy;
    private final Object plugin;
    private final MessageCodec codec;
    private final PlatformLogger logger;

    private volatile EnvelopeSink sink;
    private volatile boolean running;

    /**
     * Creates the transport.
     *
     * @param proxy velocity proxy
     * @param plugin owning plugin instance (for channel registration)
     * @param codec envelope codec
     * @param logger platform logger
     */
    public VelocityPluginMessageTransport(ProxyServer proxy, Object plugin, MessageCodec codec, PlatformLogger logger) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public String name() {
        return "plugin-messaging";
    }

    @Override
    public void start(EnvelopeSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
        proxy.getChannelRegistrar().register(BUNGEE_CHANNEL);
        this.running = true;
    }

    @Override
    public void publish(MessageEnvelope envelope) {
        if (!running) {
            throw new IllegalStateException("Transport is not running");
        }
        byte[] payload = codec.encode(envelope);
        byte[] frame = buildForwardFrame(payload);
        Target target = envelope.target();
        if (target.kind() == Target.Kind.SERVER && target.serverName() != null) {
            proxy.getServer(target.serverName())
                    .ifPresent(server -> sendToServer(server, frame));
        } else {
            // BROADCAST / ALL_BACKENDS / PLAYER: send to every backend, bus filters.
            for (RegisteredServer server : proxy.getAllServers()) {
                sendToServer(server, frame);
            }
        }
    }

    @Override
    public boolean isConnected() {
        return running;
    }

    /**
     * Handles inbound BungeeCord {@code Forward} frames from backends.
     *
     * @param event plugin message event
     */
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!running || !(event.getIdentifier().getId().equals(BUNGEE_CHANNEL.getId()))) {
            return;
        }
        // Only handle messages originating from a backend server connection.
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }
        byte[] data = event.getData();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            String subChannel = in.readUTF();
            if (!"Forward".equals(subChannel)) {
                return;
            }
            in.readUTF(); // destination ("ALL" or server name) - Velocity already routes backend↔backend
            String forwardChannel = in.readUTF();
            if (!FORWARD_SUBCHANNEL.equals(forwardChannel)) {
                return;
            }
            short length = in.readShort();
            byte[] payload = new byte[length];
            in.readFully(payload);
            EnvelopeSink target = sink;
            if (target != null) {
                target.accept(codec.decode(payload));
            }
        } catch (IOException error) {
            logger.warn("Failed to read Forward frame on the proxy", error);
        }
    }

    @Override
    public void close() {
        running = false;
        sink = null;
        try {
            proxy.getChannelRegistrar().unregister(BUNGEE_CHANNEL);
        } catch (RuntimeException ignored) {
            // registrar may already be gone during shutdown
        }
    }

    private void sendToServer(RegisteredServer server, byte[] frame) {
        try {
            server.sendPluginMessage(BUNGEE_CHANNEL, frame);
        } catch (RuntimeException error) {
            logger.warn("Failed to send plugin message to server '" + server.getServerInfo().getName() + "'", error);
        }
    }

    private byte[] buildForwardFrame(byte[] payload) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            // Proxy → backend: a plain sub-channel frame the backend reads as our bus.
            out.writeUTF(FORWARD_SUBCHANNEL);
            out.writeShort(payload.length);
            out.write(payload);
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to build proxy frame", error);
        }
    }
}
