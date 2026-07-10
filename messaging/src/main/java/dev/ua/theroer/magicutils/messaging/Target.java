package dev.ua.theroer.magicutils.messaging;

import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * Addressing hint describing which network members should receive a message.
 *
 * <p>Targets are transport-agnostic. The default plugin-messaging transport and
 * the Redis transport interpret each {@link Kind} on a best-effort basis; where a
 * transport cannot honour a target precisely it falls back to a broadcast and
 * lets receivers filter by {@link #serverName()} / {@link #playerId()}.</p>
 */
public final class Target {
    /**
     * Classifies the intended recipients of a message.
     */
    public enum Kind {
        /** Deliver to every network member subscribed to the channel. */
        BROADCAST,
        /** Deliver to every backend server (proxy excluded). */
        ALL_BACKENDS,
        /** Deliver to the proxy only. */
        PROXY,
        /** Deliver to a single backend server identified by {@link #serverName()}. */
        SERVER,
        /** Deliver to the backend currently hosting {@link #playerId()}. */
        PLAYER
    }

    private static final Target BROADCAST = new Target(Kind.BROADCAST, null, null);
    private static final Target ALL_BACKENDS = new Target(Kind.ALL_BACKENDS, null, null);
    private static final Target PROXY = new Target(Kind.PROXY, null, null);

    private final Kind kind;
    private final @Nullable String serverName;
    private final @Nullable UUID playerId;

    private Target(Kind kind, @Nullable String serverName, @Nullable UUID playerId) {
        this.kind = kind;
        this.serverName = serverName;
        this.playerId = playerId;
    }

    /**
     * A message for every subscriber on the channel, on every member.
     *
     * @return broadcast target
     */
    public static Target broadcast() {
        return BROADCAST;
    }

    /**
     * A message for every backend server, excluding the proxy.
     *
     * @return all-backends target
     */
    public static Target allBackends() {
        return ALL_BACKENDS;
    }

    /**
     * A message for the proxy only.
     *
     * @return proxy target
     */
    public static Target proxy() {
        return PROXY;
    }

    /**
     * A message for a single backend server by its registered name.
     *
     * @param serverName backend server name (as known to the proxy)
     * @return server target
     */
    public static Target server(String serverName) {
        return new Target(Kind.SERVER, requireText(serverName, "serverName"), null);
    }

    /**
     * A message for whichever backend currently hosts the given player.
     *
     * @param playerId player UUID
     * @return player target
     */
    public static Target player(UUID playerId) {
        return new Target(Kind.PLAYER, null, Objects.requireNonNull(playerId, "playerId"));
    }

    /**
     * Returns the target kind.
     *
     * @return target kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns the destination server name for {@link Kind#SERVER}.
     *
     * @return server name, or null for other kinds
     */
    public @Nullable String serverName() {
        return serverName;
    }

    /**
     * Returns the destination player id for {@link Kind#PLAYER}.
     *
     * @return player id, or null for other kinds
     */
    public @Nullable UUID playerId() {
        return playerId;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Target target)) {
            return false;
        }
        return kind == target.kind
                && Objects.equals(serverName, target.serverName)
                && Objects.equals(playerId, target.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, serverName, playerId);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case SERVER -> "Target[server=" + serverName + "]";
            case PLAYER -> "Target[player=" + playerId + "]";
            default -> "Target[" + kind + "]";
        };
    }
}
