package dev.ua.theroer.magicutils.diagnostics;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.ParamName;
import dev.ua.theroer.magicutils.annotations.Sender;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import dev.ua.theroer.magicutils.annotations.Suggest;
import dev.ua.theroer.magicutils.commands.CommandManager;
import dev.ua.theroer.magicutils.commands.CommandResult;
import dev.ua.theroer.magicutils.commands.HelpCommandSupport;
import dev.ua.theroer.magicutils.commands.MagicCommand;
import dev.ua.theroer.magicutils.commands.MagicPermissionDefault;
import dev.ua.theroer.magicutils.commands.MagicSender;
import dev.ua.theroer.magicutils.commands.SubCommandSpec;
import dev.ua.theroer.magicutils.logger.LogBuilderCore;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.logger.LogTarget;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.MessageParser;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerInfo;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Loader-agnostic {@code /magicutils} bundle command shared by every platform
 * bundle (Bukkit, Fabric, NeoForge, …). It renders the bundle status, lists the
 * shared-runtime consumers, and wires the diagnostics + help sub-commands.
 *
 * <p>Everything platform-specific is injected as plain functions: the bundle
 * version, where to read the connected consumers from, and how to look one up by
 * name. Platforms with no shared-runtime consumer registry (e.g. NeoForge) just
 * pass empty suppliers and still get the status/diagnostics/help surface. This
 * replaces the three near-identical per-loader command classes.</p>
 */
@CommandInfo(
        name = "magicutils",
        aliases = {"mu"},
        description = "Show MagicUtils bundle status and connected mods",
        permissionDefault = MagicPermissionDefault.OP
)
public final class MagicUtilsBundleCommand extends MagicCommand {
    private static final DateTimeFormatter CONNECTED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z", Locale.ROOT)
                    .withZone(ZoneId.systemDefault());

    private final @Nullable LoggerCore logger;
    private final String bundleVersion;
    private final Supplier<List<MagicUtilsConsumerInfo>> consumersSupplier;
    private final Function<String, MagicUtilsConsumerInfo> consumerLookup;

    /**
     * Creates the shared bundle command.
     *
     * @param logger logger core used to render chat output (nullable)
     * @param bundleVersion the bundle's own version string
     * @param consumersSupplier source of shared-runtime consumers (may return empty)
     * @param consumerLookup lookup of one consumer by name (may return null)
     * @param diagnosticsServiceSupplier diagnostics service source, enables the
     *        {@code diagnostics} sub-commands when non-null
     * @param commandManagerSupplier command manager source, enables {@code help}
     *        when non-null
     */
    public MagicUtilsBundleCommand(
            @Nullable LoggerCore logger,
            @Nullable String bundleVersion,
            @Nullable Supplier<List<MagicUtilsConsumerInfo>> consumersSupplier,
            @Nullable Function<String, MagicUtilsConsumerInfo> consumerLookup,
            @Nullable Supplier<DiagnosticsService> diagnosticsServiceSupplier,
            @Nullable Supplier<CommandManager<?>> commandManagerSupplier
    ) {
        this.logger = logger;
        this.bundleVersion = bundleVersion != null && !bundleVersion.isBlank() ? bundleVersion : "unknown";
        this.consumersSupplier = consumersSupplier != null ? consumersSupplier : List::of;
        this.consumerLookup = consumerLookup != null ? consumerLookup : name -> null;
        if (diagnosticsServiceSupplier != null) {
            // Adds `/magicutils diagnostics`, `/magicutils diagnostics export`
            // and `/magicutils diagnostics suite <name>`.
            for (SubCommandSpec<?> spec :
                    DiagnosticsCommandSupport.<Object>createDiagnosticsSubCommands(logger, diagnosticsServiceSupplier)) {
                addSubCommand(spec);
            }
        }
        // `/magicutils help [subcommand]` — the same help renderer consumer mods
        // wire onto their own root command.
        if (commandManagerSupplier != null) {
            addSubCommand(HelpCommandSupport.createHelpSubCommand(logger, commandManagerSupplier));
        }
    }

    public CommandResult execute(@Sender MagicSender sender) {
        sendLines(sender, summaryLines());
        return CommandResult.success(false, "");
    }

    @SubCommand(name = "mods", aliases = {"list", "plugins"}, description = "List mods using the shared MagicUtils runtime")
    public CommandResult mods(@Sender MagicSender sender) {
        sendLines(sender, modListLines());
        return CommandResult.success(false, "");
    }

    @SubCommand(name = "mod", aliases = {"info", "plugin"}, description = "Show details for a mod using the shared MagicUtils runtime")
    public CommandResult mod(
            @Sender MagicSender sender,
            @ParamName("mod") @Suggest("getSharedRuntimeModSuggestions") String modName
    ) {
        if (modName == null || modName.isBlank()) {
            sendLines(sender, List.of(
                    headerLine(),
                    "<gray>Usage:</gray> <white>/magicutils mod <name></white>"
            ));
            return CommandResult.failure(false);
        }
        MagicUtilsConsumerInfo info = consumerLookup.apply(modName);
        if (info == null) {
            sendLines(sender, List.of(
                    headerLine(),
                    "<red>Shared-runtime mod not found:</red> <white>" + escape(modName) + "</white>",
                    "<dark_gray>Only mods using the shared MagicUtils runtime are listed.</dark_gray>"
            ));
            return CommandResult.failure(false);
        }
        sendLines(sender, modDetailLines(info));
        return CommandResult.success(false, "");
    }

    public List<String> getSharedRuntimeModSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (MagicUtilsConsumerInfo info : externalConsumers()) {
            suggestions.add(info.pluginName());
        }
        suggestions.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(suggestions);
    }

    private List<String> summaryLines() {
        List<MagicUtilsConsumerInfo> consumers = externalConsumers();
        List<String> lines = new ArrayList<>();
        lines.add(headerLine());
        lines.add("<gray>Bundle:</gray> <white>MagicUtils</white>");
        lines.add("<gray>Version:</gray> <white>" + escape(bundleVersion) + "</white>");
        lines.add("<gray>Shared-runtime mods:</gray> <white>" + consumers.size() + "</white>");
        if (!consumers.isEmpty()) {
            lines.add("<gray>Connected:</gray> <white>" + escape(joinConsumerLabels(consumers)) + "</white>");
        }
        lines.add("<gray>Try:</gray> <white>/magicutils diagnostics</white> <gray>or</gray> <white>/magicutils help</white>");
        return List.copyOf(lines);
    }

    private List<String> modListLines() {
        List<MagicUtilsConsumerInfo> consumers = externalConsumers();
        List<String> lines = new ArrayList<>();
        lines.add(headerLine());
        lines.add("<gray>Shared-runtime mods:</gray> <white>" + consumers.size() + "</white>");
        if (consumers.isEmpty()) {
            lines.add("<yellow>No connected shared-runtime mods found.</yellow>");
            return List.copyOf(lines);
        }
        for (MagicUtilsConsumerInfo info : consumers) {
            lines.add("<dark_gray>-</dark_gray> <white>" + escape(info.pluginName()) + "</white> <gray>v"
                    + escape(info.version()) + "</gray> <dark_gray>(" + escape(info.capabilitiesSummary())
                    + ")</dark_gray>");
        }
        return List.copyOf(lines);
    }

    private List<String> modDetailLines(MagicUtilsConsumerInfo info) {
        List<String> lines = new ArrayList<>();
        lines.add(headerLine());
        lines.add("<gray>Mod:</gray> <white>" + escape(info.pluginName()) + "</white>");
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
                    + escape(String.join(", ", info.namedComponentNames())) + "</white>");
        }
        return List.copyOf(lines);
    }

    private List<MagicUtilsConsumerInfo> externalConsumers() {
        List<MagicUtilsConsumerInfo> consumers = new ArrayList<>(consumersSupplier.get());
        consumers.sort(Comparator.comparing(MagicUtilsConsumerInfo::pluginName, String.CASE_INSENSITIVE_ORDER));
        return consumers;
    }

    private String joinConsumerLabels(List<MagicUtilsConsumerInfo> consumers) {
        List<String> labels = new ArrayList<>();
        for (MagicUtilsConsumerInfo info : consumers) {
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
