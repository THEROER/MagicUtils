package dev.ua.theroer.magicutils.platform;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Normalizes "any sender-like object" into an {@link Audience}.
 *
 * <p>Plugins receive different sender types from different code paths
 * (Bukkit {@code Player}, Velocity {@code CommandSource}, raw
 * {@code CommandSender}, an existing {@link Audience}, or {@code null}).
 * This utility unifies that decoding so callers can write
 * {@code Messages.send(player, key)} without re-implementing the type
 * branching dance every time.</p>
 *
 * <p>Resolution order:</p>
 * <ol>
 *     <li>{@code null} → {@code null}.</li>
 *     <li>{@link Audience} instance → returned as is.</li>
 *     <li>Each {@linkplain #registerFactory(Function) registered
 *         factory} is tried in registration order; the first non-null
 *         result wins.</li>
 *     <li>Reflective fallback: if the object exposes a
 *         {@code getUniqueId()} returning {@link UUID}, a synthetic
 *         {@link Audience} carrying just that id is returned.</li>
 * </ol>
 *
 * <p>Platform bootstraps (Bukkit/Velocity/Bungee/Fabric/NeoForge)
 * register a factory at start-up that knows how to wrap their native
 * types into proper {@link Audience} implementations. Callers should
 * never need to know which platform they are on.</p>
 */
public final class AudienceResolver {

    private static final CopyOnWriteArrayList<Function<Object, Audience>> FACTORIES =
            new CopyOnWriteArrayList<>();

    private AudienceResolver() {
    }

    /**
     * Registers a factory that wraps platform-specific objects (e.g.
     * Bukkit {@code Player}, Velocity {@code CommandSource}) into an
     * {@link Audience}.
     *
     * <p>Factories receive the raw object and may return {@code null}
     * when they cannot handle it. Resolution stops at the first
     * non-null result.</p>
     *
     * @param factory non-null factory
     */
    public static void registerFactory(Function<Object, Audience> factory) {
        if (factory == null) {
            return;
        }
        FACTORIES.addIfAbsent(factory);
    }

    /**
     * Removes a previously registered factory.
     *
     * @param factory factory to remove
     */
    public static void unregisterFactory(Function<Object, Audience> factory) {
        if (factory == null) {
            return;
        }
        FACTORIES.remove(factory);
    }

    /**
     * Resolves any object into an {@link Audience}. Returns {@code null}
     * when no resolution is possible.
     *
     * @param who Audience, platform sender, player-like object, or
     *            {@code null}
     * @return audience or {@code null}
     */
    public static @Nullable Audience resolve(@Nullable Object who) {
        if (who == null) {
            return null;
        }
        if (who instanceof Audience audience) {
            return audience;
        }
        for (Function<Object, Audience> factory : FACTORIES) {
            try {
                Audience resolved = factory.apply(who);
                if (resolved != null) {
                    return resolved;
                }
            } catch (RuntimeException ignored) {
            }
        }
        UUID id = legacyUuid(who);
        if (id != null) {
            return new SyntheticAudience(id);
        }
        return null;
    }

    /**
     * Extracts a {@link UUID} from any object without constructing an
     * {@link Audience}. Useful for code paths that only need to look
     * up a player language preference.
     *
     * @param who Audience, player-like object, or {@code null}
     * @return UUID or {@code null}
     */
    public static @Nullable UUID extractUuid(@Nullable Object who) {
        if (who == null) {
            return null;
        }
        if (who instanceof Audience audience) {
            return audience.id();
        }
        Audience resolved = resolve(who);
        if (resolved != null) {
            return resolved.id();
        }
        return legacyUuid(who);
    }

    private static @Nullable UUID legacyUuid(Object who) {
        try {
            Method method = who.getClass().getMethod("getUniqueId");
            Object result = method.invoke(who);
            if (result instanceof UUID uuid) {
                return uuid;
            }
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
        }
        return null;
    }

    private record SyntheticAudience(UUID id) implements Audience {
        @Override
        public UUID id() {
            return id;
        }

        @Override
        public void send(net.kyori.adventure.text.Component component) {
        }
    }
}
