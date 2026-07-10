package dev.ua.theroer.magicutils.messaging;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

/**
 * Identifies a member of the network for message origin and routing.
 *
 * <p>A source has a stable {@link #id() id} (unique per running process) and a
 * {@link #type() type} distinguishing the proxy from backend servers. Backends
 * additionally carry a {@link #serverName() server name} as registered with the
 * proxy, which the proxy uses to route {@link Target#server(String)} messages.</p>
 */
public final class MessageSource {
    /**
     * Whether a network member is a proxy or a backend server.
     */
    public enum Type {
        /** A proxy (Velocity or BungeeCord). */
        PROXY,
        /** A backend Minecraft server. */
        BACKEND
    }

    private final String id;
    private final Type type;
    private final @Nullable String serverName;

    private MessageSource(String id, Type type, @Nullable String serverName) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.serverName = serverName;
    }

    /**
     * Creates a proxy source.
     *
     * @param id unique process id
     * @return proxy source
     */
    public static MessageSource proxy(String id) {
        return new MessageSource(id, Type.PROXY, null);
    }

    /**
     * Creates a backend source.
     *
     * @param id unique process id
     * @param serverName server name registered with the proxy (may be null when unknown)
     * @return backend source
     */
    public static MessageSource backend(String id, @Nullable String serverName) {
        return new MessageSource(id, Type.BACKEND, serverName);
    }

    /**
     * Returns the unique process id of this member.
     *
     * @return member id
     */
    public String id() {
        return id;
    }

    /**
     * Returns the member type.
     *
     * @return member type
     */
    public Type type() {
        return type;
    }

    /**
     * Returns the backend server name, when known.
     *
     * @return server name, or null for proxies / unknown backends
     */
    public @Nullable String serverName() {
        return serverName;
    }

    /**
     * Returns whether this source is a proxy.
     *
     * @return true when a proxy
     */
    public boolean isProxy() {
        return type == Type.PROXY;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MessageSource that)) {
            return false;
        }
        return id.equals(that.id) && type == that.type && Objects.equals(serverName, that.serverName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, serverName);
    }

    @Override
    public String toString() {
        return "MessageSource[" + type + ":" + id
                + (serverName != null ? "@" + serverName : "") + "]";
    }
}
