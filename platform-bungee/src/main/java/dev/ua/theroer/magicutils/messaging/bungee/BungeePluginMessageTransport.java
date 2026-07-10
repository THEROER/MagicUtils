package dev.ua.theroer.magicutils.messaging.bungee;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import dev.ua.theroer.magicutils.messaging.MessageCodec;
import dev.ua.theroer.magicutils.messaging.MessageEnvelope;
import dev.ua.theroer.magicutils.messaging.MessageTransport;
import dev.ua.theroer.magicutils.messaging.Target;
import dev.ua.theroer.magicutils.platform.PlatformLogger;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

/**
 * Default (Redis-less) messaging transport for the BungeeCord/Waterfall proxy.
 *
 * <p>Mirror of {@code VelocityPluginMessageTransport}: it listens on the
 * BungeeCord plugin channel for {@code Forward} frames from backends (feeding
 * proxy-side subscribers) and dispatches proxy-originated envelopes to backend
 * servers wrapped in the shared sub-channel frame.</p>
 *
 * <p>Registered as a Bungee {@link Listener} via
 * {@code proxy.getPluginManager().registerListener(plugin, transport)} by the
 * bootstrap wiring.</p>
 */
public final class BungeePluginMessageTransport implements MessageTransport, Listener {
    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final String FORWARD_SUBCHANNEL = "magicutils:bus";

    private final ProxyServer proxy;
    private final Plugin plugin;
    private final MessageCodec codec;
    private final PlatformLogger logger;

    private volatile EnvelopeSink sink;
    private volatile boolean running;

    /**
     * Creates the transport.
     *
     * @param proxy bungee proxy
     * @param plugin owning plugin
     * @param codec envelope codec
     * @param logger platform logger
     */
    public BungeePluginMessageTransport(ProxyServer proxy, Plugin plugin, MessageCodec codec, PlatformLogger logger) {
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
        proxy.registerChannel(BUNGEE_CHANNEL);
        this.running = true;
    }

    @Override
    public void publish(MessageEnvelope envelope) {
        if (!running) {
            throw new IllegalStateException("Transport is not running");
        }
        byte[] payload = codec.encode(envelope);
        byte[] frame = buildFrame(payload);
        Target target = envelope.target();
        if (target.kind() == Target.Kind.SERVER && target.serverName() != null) {
            ServerInfo server = proxy.getServerInfo(target.serverName());
            if (server != null) {
                sendToServer(server, frame);
            }
        } else {
            for (ServerInfo server : proxy.getServers().values()) {
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
    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!running || !BUNGEE_CHANNEL.equals(event.getTag())) {
            return;
        }
        // Only accept frames coming from a backend server connection.
        if (!(event.getSender() instanceof Server)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            String subChannel = in.readUTF();
            if (!"Forward".equals(subChannel)) {
                return;
            }
            in.readUTF(); // destination
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
            proxy.unregisterChannel(BUNGEE_CHANNEL);
        } catch (RuntimeException ignored) {
            // channel may already be unregistered during shutdown
        }
    }

    private void sendToServer(ServerInfo server, byte[] frame) {
        try {
            // queue=true so the frame is buffered until the backend connection is ready.
            server.sendData(BUNGEE_CHANNEL, frame, true);
        } catch (RuntimeException error) {
            logger.warn("Failed to send plugin message to server '" + server.getName() + "'", error);
        }
    }

    private byte[] buildFrame(byte[] payload) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(FORWARD_SUBCHANNEL);
            out.writeShort(payload.length);
            out.write(payload);
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to build proxy frame", error);
        }
    }
}
