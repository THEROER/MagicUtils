package dev.ua.theroer.magicutils.messaging.bukkit;

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

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.Nullable;

/**
 * Default (Redis-less) messaging transport for Bukkit/Paper backends.
 *
 * <p>Rides the proxy's BungeeCord plugin-messaging channel. Backends cannot open
 * a socket to the proxy directly, so this transport uses the {@code Forward}
 * sub-channel: a backend sends {@code Forward ALL <sub> <bytes>} and the proxy
 * relays the raw {@code <bytes>} to every other backend that listens on
 * {@code <sub>}. That gives full backend↔backend and backend↔proxy delivery over
 * the vanilla mechanism, with no Redis required.</p>
 *
 * <p>The one native constraint: plugin messages travel through a player
 * connection, so at least one player must be online for a backend to reach the
 * proxy. When no player is online, outgoing messages are dropped (and logged at
 * debug); the Redis transport is the way to remove that constraint.</p>
 */
public final class BukkitPluginMessageTransport implements MessageTransport, PluginMessageListener {
    /** The vanilla BungeeCord/Velocity plugin channel. */
    static final String BUNGEE_CHANNEL = "BungeeCord";
    /** Our sub-channel carried inside BungeeCord Forward frames. */
    static final String FORWARD_SUBCHANNEL = "magicutils:bus";

    private final JavaPlugin plugin;
    private final MessageCodec codec;
    private final PlatformLogger logger;

    private volatile EnvelopeSink sink;
    private volatile boolean running;

    /**
     * Creates the transport.
     *
     * @param plugin owning plugin
     * @param codec envelope codec
     * @param logger platform logger
     */
    public BukkitPluginMessageTransport(JavaPlugin plugin, MessageCodec codec, PlatformLogger logger) {
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
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
        this.running = true;
    }

    @Override
    public void publish(MessageEnvelope envelope) {
        if (!running) {
            throw new IllegalStateException("Transport is not running");
        }
        Player carrier = anyPlayer();
        if (carrier == null) {
            logger.debug("Dropping message on channel '" + envelope.channel()
                    + "': no player online to carry it through the proxy");
            return;
        }
        byte[] payload = codec.encode(envelope);
        byte[] frame = buildForwardFrame(envelope.target(), payload);
        carrier.sendPluginMessage(plugin, BUNGEE_CHANNEL, frame);
    }

    @Override
    public boolean isConnected() {
        return running && anyPlayer() != null;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!running || !BUNGEE_CHANNEL.equals(channel)) {
            return;
        }
        // The proxy strips the Forward/destination header before delivering, so a
        // backend receives a plain <sub-channel><short len><payload> frame here.
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String subChannel = in.readUTF();
            if (!FORWARD_SUBCHANNEL.equals(subChannel)) {
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
            logger.warn("Failed to read forwarded plugin message", error);
        }
    }

    @Override
    public void close() {
        running = false;
        sink = null;
        try {
            Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        } catch (RuntimeException ignored) {
            // messenger may already be torn down during shutdown
        }
    }

    /**
     * Builds a BungeeCord {@code Forward} frame. The destination is "ALL" for
     * broadcast/all-backends, or the specific server name for a SERVER target;
     * PROXY/PLAYER targets go out as ALL and are filtered by the bus on receipt.
     */
    private byte[] buildForwardFrame(Target target, byte[] payload) {
        String destination = target.kind() == Target.Kind.SERVER && target.serverName() != null
                ? target.serverName()
                : "ALL";
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Forward");
            out.writeUTF(destination);
            out.writeUTF(FORWARD_SUBCHANNEL);
            out.writeShort(payload.length);
            out.write(payload);
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to build Forward frame", error);
        }
    }

    private @Nullable Player anyPlayer() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            return player;
        }
        return null;
    }
}
