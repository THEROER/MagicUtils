package dev.ua.theroer.magicutils.platform.bukkit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Detects when the MagicUtils runtime is loaded more than once on a single
 * Bukkit/Paper server and warns the server owner, with instructions.
 *
 * <p>On Paper every plugin has its own isolated classloader. A plugin that
 * <em>shades</em> MagicUtils therefore carries a private copy of the runtime:
 * its classes are physically distinct from any other plugin's copy and from a
 * standalone MagicUtils plugin. Two shaded consumers (say {@code AliasCreator}
 * and {@code DonateMenu}) thus run two independent MagicUtils instances -
 * separate consumer registries, configs and {@code /mu} commands - which the
 * server owner never asked for and cannot see from the plugin list. The
 * developer may ship only a shaded build, or ship both shaded and external
 * without explaining the difference, so the owner has no other signal.</p>
 *
 * <p>This detector gives them one. Because the isolated copies share no static
 * state, the rendezvous point is the JVM-global {@link System#getProperties()}
 * (one properties table per server process, visible to every classloader).
 * Every MagicUtils instance records its host here at startup; when more than one
 * distinct host is present, a single loud warning is logged naming the hosts and
 * telling the owner to rebuild the consumers in {@code external} mode and install
 * one standalone MagicUtils.</p>
 *
 * <p>The warning is advisory: the server still boots (each copy works in
 * isolation), the owner is just told how to collapse them into one shared
 * runtime.</p>
 */
final class MagicUtilsDuplicationDetector {

    /** JVM-global property holding the recorded hosts, one per {@code ;}. */
    private static final String HOSTS_PROPERTY = "magicutils.bukkit.hosts";

    /** JVM-global latch so the multi-instance warning is logged only once. */
    private static final String WARNED_PROPERTY = "magicutils.bukkit.duplicationWarned";

    /** Host name a standalone MagicUtils plugin records itself under. */
    private static final String STANDALONE_PLUGIN_NAME = "MagicUtils";

    private MagicUtilsDuplicationDetector() {
    }

    /**
     * Records the MagicUtils host {@code plugin} in the JVM-global registry and,
     * if this brings the number of distinct hosts above one, logs a single
     * warning describing the duplication and how to fix it.
     *
     * <p>Called once per MagicUtils instance at bootstrap, for both standalone
     * and shaded hosts, before any of the consumer-registry short-circuits.</p>
     *
     * @param plugin the plugin whose classloader carries this MagicUtils copy
     */
    static void record(JavaPlugin plugin) {
        record(plugin.getName(), plugin.getPluginMeta().getVersion(), plugin.getLogger());
    }

    /**
     * Core of {@link #record(JavaPlugin)} in terms of plain values, so it is
     * testable without constructing a Bukkit {@link JavaPlugin} (whose
     * constructor requires a running server). Package-private for the test.
     *
     * @param name    host plugin name
     * @param version host plugin version (may be {@code null}/blank)
     * @param logger  the host plugin's logger, used for the warning
     */
    static void record(String name, String version, Logger logger) {
        boolean standalone = STANDALONE_PLUGIN_NAME.equalsIgnoreCase(name);

        // System.getProperties() is the one object every isolated plugin
        // classloader shares, so synchronize on it to make read-modify-write of
        // the host list atomic across the copies loading concurrently.
        Properties properties = System.getProperties();
        Map<String, Host> hosts;
        boolean alreadyWarned;
        synchronized (properties) {
            hosts = parseHosts(properties.getProperty(HOSTS_PROPERTY));
            // Keyed by plugin name: re-registering the same host (e.g. a reload)
            // updates rather than duplicates it, so the count reflects distinct
            // hosts, not startups.
            hosts.put(name, new Host(name, version, standalone));
            properties.setProperty(HOSTS_PROPERTY, renderHosts(hosts));
            alreadyWarned = Boolean.parseBoolean(properties.getProperty(WARNED_PROPERTY));
            if (hosts.size() > 1 && !alreadyWarned) {
                properties.setProperty(WARNED_PROPERTY, "true");
            }
        }

        if (hosts.size() > 1 && !alreadyWarned) {
            warn(logger, hosts.values());
        }
    }

    private static void warn(Logger logger, Iterable<Host> hosts) {
        StringBuilder message = new StringBuilder();
        message.append("MagicUtils is loaded more than once on this server:");
        for (Host host : hosts) {
            message.append("\n - ")
                    .append(host.standalone ? "standalone plugin " : "bundled in ")
                    .append(host.name);
            if (host.version != null && !host.version.isBlank()) {
                message.append(' ').append(host.version);
            }
        }
        message.append("\nEach copy is isolated (separate config, consumer registry and /mu commands),")
                .append(" so plugins do not actually share one MagicUtils.")
                .append("\nTo run a single shared MagicUtils: install the standalone MagicUtils plugin,")
                .append(" and rebuild the plugins above with -Pmagicutils_embed=false (embed mode external).");
        logger.warning(message.toString());
    }

    private static Map<String, Host> parseHosts(String raw) {
        Map<String, Host> hosts = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return hosts;
        }
        for (String entry : raw.split(";")) {
            if (entry.isBlank()) {
                continue;
            }
            String[] parts = entry.split("\\|", 3);
            String name = parts[0];
            String version = parts.length > 1 ? parts[1] : "";
            boolean standalone = parts.length > 2 && Boolean.parseBoolean(parts[2]);
            hosts.put(name, new Host(name, version, standalone));
        }
        return hosts;
    }

    private static String renderHosts(Map<String, Host> hosts) {
        StringBuilder rendered = new StringBuilder();
        for (Host host : hosts.values()) {
            if (rendered.length() > 0) {
                rendered.append(';');
            }
            // name|version|standalone. Names/versions from plugin metadata; strip
            // the field separators defensively so a stray '|'/';' cannot corrupt
            // the encoding.
            rendered.append(sanitize(host.name))
                    .append('|')
                    .append(sanitize(host.version))
                    .append('|')
                    .append(host.standalone);
        }
        return rendered.toString();
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('|', '_').replace(';', '_');
    }

    /** A single MagicUtils host: the plugin carrying a copy of the runtime. */
    private record Host(String name, String version, boolean standalone) {
    }
}
