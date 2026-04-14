package dev.ua.theroer.magicutils.logger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Serializes log {@link Component}s for console output, preferring ANSI colors when the
 * adventure ANSI serializer is on the classpath and falling back to plain text otherwise.
 *
 * <p>The ANSI serializer is loaded reflectively so platforms without it (lean shaded jars,
 * older Bungee/Fabric builds) still work — they simply receive uncolored text.</p>
 */
public final class ConsoleColorSerializer {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final AnsiSupport ANSI = AnsiSupport.load();

    private ConsoleColorSerializer() {
    }

    /**
     * Serializes a component to ANSI-colored text when possible, otherwise to plain text.
     *
     * @param component component to serialize, may be {@code null}
     * @return rendered string, or empty string when component is {@code null}
     */
    public static String serialize(Component component) {
        if (component == null) {
            return "";
        }
        if (ANSI != null) {
            String ansi = ANSI.serialize(component);
            if (ansi != null) {
                return ansi;
            }
        }
        return PLAIN.serialize(component);
    }

    /**
     * Checks if ANSI color serialization is available in the current environment.
     * 
     * @return {@code true} when the ANSI serializer was successfully loaded and colors will be emitted
     */
    public static boolean ansiAvailable() {
        return ANSI != null;
    }

    private static final class AnsiSupport {
        private final Object serializer;
        private final Method serialize;

        private AnsiSupport(Object serializer, Method serialize) {
            this.serializer = serializer;
            this.serialize = serialize;
        }

        static AnsiSupport load() {
            try {
                Class<?> serializerClass = Class.forName(
                        "net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer");
                Method ansi = serializerClass.getMethod("ansi");
                Method serialize = serializerClass.getMethod("serialize", Component.class);
                Object serializer = ansi.invoke(null);
                return new AnsiSupport(serializer, serialize);
            } catch (ClassNotFoundException ignored) {
                return null;
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException ignored) {
                return null;
            }
        }

        String serialize(Component component) {
            try {
                Object value = serialize.invoke(serializer, component);
                return value != null ? value.toString() : null;
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                return null;
            }
        }
    }
}
