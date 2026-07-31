package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.platform.Audience;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-agnostic command sender wrapper.
 */
public interface MagicSender {
    /**
     * Wrap raw sender instance into MagicSender if a platform adapter is available.
     *
     * @param sender raw sender instance
     * @return wrapped sender or null if unsupported
     */
    static @Nullable MagicSender wrap(Object sender) {
        return MagicSenderAdapters.wrap(sender);
    }

    /**
     * Checks permission on raw sender using registered adapters.
     *
     * @param sender raw sender instance
     * @param permission permission node
     * @return true if granted
     */
    static boolean hasPermission(Object sender, String permission) {
        return MagicSenderAdapters.hasPermission(sender, permission);
    }

    /**
     * Checks permission on raw sender using registered adapters and a custom op-level fallback.
     *
     * @param sender raw sender instance
     * @param permission permission node
     * @param fallbackOpLevel op level to treat as granted when no permission backend answers
     * @return true if granted
     */
    static boolean hasPermission(Object sender, String permission, int fallbackOpLevel) {
        return MagicSenderAdapters.hasPermission(sender, permission, fallbackOpLevel);
    }

    /**
     * Audience for sending messages back to the sender.
     *
     * @return audience wrapper
     */
    Audience audience();

    /**
     * Human-readable sender name.
     *
     * @return sender name
     */
    String name();

    /**
     * Optional sender id (player UUID, if available).
     *
     * @return sender id or null
     */
    default @Nullable UUID id() {
        Audience audience = audience();
        return audience != null ? audience.id() : null;
    }

    /**
     * Permission check for the sender.
     *
     * @param permission permission node
     * @return true if granted
     */
    boolean hasPermission(String permission);

    /**
     * Permission check for the sender with a custom op-level fallback.
     *
     * <p>Platforms that support fallback-aware permission checks should override this method.
     * The default implementation preserves existing behavior.</p>
     *
     * @param permission permission node
     * @param fallbackOpLevel op level to treat as granted when no permission backend answers
     * @return true if granted
     */
    default boolean hasPermission(String permission, int fallbackOpLevel) {
        return hasPermission(permission);
    }

    /**
     * IP address of the sender, if available.
     *
     * @return IP address string or null
     */
    default @Nullable String address() {
        return null;
    }

    /**
     * Teleports the sender to a location, if the platform and sender support it.
     *
     * <p>Coordinates are used rather than a platform {@code Location} so this stays
     * on the platform-agnostic interface. A {@code worldName} of null means "the
     * sender's current world"; platforms that cannot resolve a world by name may
     * ignore it. The move is asynchronous by contract: on Folia/Canvas a
     * synchronous teleport throws on any thread, so implementations must route
     * through the platform's async teleport and complete the returned future when
     * the move resolves.</p>
     *
     * <p>The default implementation is a no-op for senders that cannot be
     * teleported (console, non-player) and completes with {@code false}. Platforms
     * whose senders can move (e.g. Bukkit players) override this.</p>
     *
     * @param worldName target world name, or null for the sender's current world
     * @param x target x
     * @param y target y
     * @param z target z
     * @return a future completed with true once the sender arrives, false if the
     *         sender cannot be teleported
     */
    default CompletableFuture<Boolean> teleport(@Nullable String worldName, double x, double y, double z) {
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Raw platform sender handle.
     *
     * @return underlying sender object
     */
    @Nullable
    Object handle();

    /**
     * Attempt to unwrap the raw handle to the given type.
     *
     * @param type target class
     * @param <T> type parameter
     * @return unwrapped handle or null if not compatible
     */
    default @Nullable <T> T unwrap(Class<T> type) {
        Object raw = handle();
        if (type == null || raw == null || !type.isInstance(raw)) {
            return null;
        }
        return type.cast(raw);
    }
}
