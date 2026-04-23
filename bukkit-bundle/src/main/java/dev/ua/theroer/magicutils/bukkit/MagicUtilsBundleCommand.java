package dev.ua.theroer.magicutils.bukkit;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.ParamName;
import dev.ua.theroer.magicutils.annotations.Sender;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import dev.ua.theroer.magicutils.annotations.Suggest;
import dev.ua.theroer.magicutils.commands.CommandResult;
import dev.ua.theroer.magicutils.commands.MagicCommand;
import dev.ua.theroer.magicutils.commands.MagicPermissionDefault;
import dev.ua.theroer.magicutils.commands.MagicSender;
import dev.ua.theroer.magicutils.logger.LogBuilderCore;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.logger.LogTarget;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.MessageParser;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.bukkit.BukkitMagicUtilsConsumerRegistry.ConsumerInfo;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Bundle command that exposes information about shared-runtime MagicUtils consumers.
 */
@CommandInfo(
        name = "magicutils",
        aliases = {"mu"},
        description = "Show MagicUtils bundle status and connected plugins",
        permissionDefault = MagicPermissionDefault.OP
)
public final class MagicUtilsBundleCommand extends MagicCommand {
    private static final DateTimeFormatter CONNECTED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z", Locale.ROOT)
                    .withZone(ZoneId.systemDefault());

    private final MagicUtilsBukkitBundlePlugin bundlePlugin;
    private final @Nullable LoggerCore logger;

    public MagicUtilsBundleCommand(MagicUtilsBukkitBundlePlugin bundlePlugin, @Nullable LoggerCore logger) {
        this.bundlePlugin = Objects.requireNonNull(bundlePlugin, "bundlePlugin");
        this.logger = logger;
    }

    public CommandResult execute(@Sender MagicSender sender) {
        sendLines(sender, summaryLines());
        return CommandResult.success(false, "");
    }

    @SubCommand(name = "plugins", aliases = {"list"}, description = "List plugins using the shared MagicUtils runtime")
    public CommandResult plugins(@Sender MagicSender sender) {
        sendLines(sender, pluginListLines());
        return CommandResult.success(false, "");
    }

    @SubCommand(name = "plugin", aliases = {"info"}, description = "Show details for a plugin using the shared MagicUtils runtime")
    public CommandResult plugin(
            @Sender MagicSender sender,
            @ParamName("plugin") @Suggest("getSharedRuntimePluginSuggestions") String pluginName
    ) {
        if (pluginName == null || pluginName.isBlank()) {
            sendLines(sender, List.of(
                    headerLine(),
                    "<gray>Usage:</gray> <white>/magicutils plugin <name></white>"
            ));
            return CommandResult.failure(false);
        }

        ConsumerInfo info = bundlePlugin.findSharedRuntimeConsumer(pluginName);
        if (info == null) {
            sendLines(sender, List.of(
                    headerLine(),
                    "<red>Shared-runtime plugin not found:</red> <white>" + escape(pluginName) + "</white>",
                    "<dark_gray>Only plugins using the shared MagicUtils runtime are listed.</dark_gray>"
            ));
            return CommandResult.failure(false);
        }

        sendLines(sender, pluginDetailLines(info));
        return CommandResult.success(false, "");
    }

    public List<String> getSharedRuntimePluginSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (ConsumerInfo info : bundlePlugin.snapshotSharedRuntimeConsumers()) {
            suggestions.add(info.pluginName());
        }
        suggestions.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(suggestions);
    }

    private List<String> summaryLines() {
        List<ConsumerInfo> consumers = externalConsumers();
        List<String> lines = new ArrayList<>();
        lines.add(headerLine());
        lines.add("<gray>Bundle plugin:</gray> <white>" + escape(bundlePlugin.getName()) + "</white>");
        lines.add("<gray>Bundle version:</gray> <white>" + escape(bundlePlugin.getPluginMeta().getVersion()) + "</white>");
        lines.add("<gray>Shared-runtime consumers:</gray> <white>" + consumers.size() + "</white>");
        if (consumers.isEmpty()) {
            lines.add("<yellow>No connected shared-runtime plugins found.</yellow>");
        } else {
            lines.add("<gray>Connected:</gray> <white>" + escape(joinConsumerLabels(consumers)) + "</white>");
        }
        lines.add("<dark_gray>Only plugins using the shared MagicUtils runtime are listed here.</dark_gray>");
        lines.add("<gray>Try:</gray> <white>/magicutils plugins</white> <gray>or</gray> <white>/magicutils plugin <name></white>");
        return List.copyOf(lines);
    }

    private List<String> pluginListLines() {
        List<ConsumerInfo> consumers = externalConsumers();
        List<String> lines = new ArrayList<>();
        lines.add(headerLine());
        lines.add("<gray>Shared-runtime consumers:</gray> <white>" + consumers.size() + "</white>");
        if (consumers.isEmpty()) {
            lines.add("<yellow>No connected shared-runtime plugins found.</yellow>");
            return List.copyOf(lines);
        }

        for (ConsumerInfo info : consumers) {
            lines.add("<dark_gray>-</dark_gray> <white>" + escape(info.pluginName()) + "</white> <gray>v"
                    + escape(info.version()) + "</gray> <dark_gray>(" + escape(info.capabilitiesSummary())
                    + ")</dark_gray>");
        }
        return List.copyOf(lines);
    }

    private List<String> pluginDetailLines(ConsumerInfo info) {
        List<String> lines = new ArrayList<>();
        lines.add(headerLine());
        lines.add("<gray>Plugin:</gray> <white>" + escape(info.pluginName()) + "</white>");
        lines.add("<gray>Version:</gray> <white>" + escape(info.version()) + "</white>");
        lines.add("<gray>Main class:</gray> <white>" + escape(info.mainClass()) + "</white>");
        lines.add("<gray>Platform:</gray> <white>" + escape(info.platformType()) + "</white>");
        lines.add("<gray>Commands:</gray> <white>" + (info.commandsEnabled()
                ? "enabled (" + info.rootCommandCount() + " roots"
                + (info.permissionPrefix() != null && !info.permissionPrefix().isBlank()
                ? ", prefix=" + escape(info.permissionPrefix())
                : "")
                + ")"
                : "disabled") + "</white>");
        lines.add("<gray>Diagnostics:</gray> <white>" + (info.diagnosticsEnabled() ? "enabled" : "disabled") + "</white>");
        lines.add("<gray>Runtime state:</gray> <white>" + (info.closed() ? "closed" : "active") + "</white>");
        lines.add("<gray>Components:</gray> <white>" + info.typedComponentCount() + " typed / "
                + info.namedComponentCount() + " named</white>");
        lines.add("<gray>Connected at:</gray> <white>" + escape(CONNECTED_AT_FORMATTER.format(info.connectedAt()))
                + "</white>");
        if (info.description() != null && !info.description().isBlank()) {
            lines.add("<gray>Description:</gray> <white>" + escape(info.description()) + "</white>");
        }
        if (info.website() != null && !info.website().isBlank()) {
            lines.add("<gray>Website:</gray> <white>" + escape(info.website()) + "</white>");
        }
        if (!info.authors().isEmpty()) {
            lines.add("<gray>Authors:</gray> <white>" + escape(String.join(", ", info.authors())) + "</white>");
        }
        if (!info.namedComponentNames().isEmpty()) {
            lines.add("<gray>Named components:</gray> <white>"
                    + escape(String.join(", ", info.namedComponentNames()))
                    + "</white>");
        }
        return List.copyOf(lines);
    }

    private List<ConsumerInfo> externalConsumers() {
        List<ConsumerInfo> consumers = new ArrayList<>(bundlePlugin.snapshotSharedRuntimeConsumers());
        consumers.sort(Comparator.comparing(ConsumerInfo::pluginName, String.CASE_INSENSITIVE_ORDER));
        return consumers;
    }

    private String joinConsumerLabels(List<ConsumerInfo> consumers) {
        List<String> labels = new ArrayList<>();
        for (ConsumerInfo info : consumers) {
            labels.add(info.pluginName() + " v" + info.version());
        }
        return String.join(", ", labels);
    }

    private String headerLine() {
        return "<dark_gray>[</dark_gray><aqua>MagicUtils</aqua><dark_gray>]</dark_gray> <white>shared runtime</white>";
    }

    private void sendLines(@Nullable MagicSender sender, List<String> lines) {
        if (sender == null || lines == null || lines.isEmpty()) {
            return;
        }
        Audience audience = sender.audience();
        for (String line : lines) {
            if (logger != null) {
                new LogBuilderCore(logger, LogLevel.INFO)
                        .noPrefix()
                        .target(LogTarget.CHAT)
                        .to(audience)
                        .send(line);
            } else if (audience != null) {
                audience.send(MessageParser.parseSmart(line));
            }
        }
    }

    private static String escape(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
