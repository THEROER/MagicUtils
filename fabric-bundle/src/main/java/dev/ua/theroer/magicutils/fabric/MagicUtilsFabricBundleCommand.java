package dev.ua.theroer.magicutils.fabric;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.Sender;
import dev.ua.theroer.magicutils.commands.CommandResult;
import dev.ua.theroer.magicutils.commands.MagicCommand;
import dev.ua.theroer.magicutils.commands.MagicPermissionDefault;
import dev.ua.theroer.magicutils.commands.MagicSender;
import dev.ua.theroer.magicutils.commands.SubCommandSpec;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsCommandSupport;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsService;
import dev.ua.theroer.magicutils.logger.LogBuilderCore;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.logger.LogTarget;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.MessageParser;
import dev.ua.theroer.magicutils.platform.Audience;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Standalone MagicUtils bundle command for Fabric. Prints the bundle status and
 * exposes the diagnostics sub-commands (unlike the Bukkit bundle, there is no
 * shared-runtime consumer registry on Fabric, so this stays intentionally small).
 */
@CommandInfo(
        name = "magicutils",
        aliases = {"mu"},
        description = "Show MagicUtils Fabric bundle status and run diagnostics",
        permissionDefault = MagicPermissionDefault.OP
)
public final class MagicUtilsFabricBundleCommand extends MagicCommand {
    private final @Nullable LoggerCore logger;
    private final String bundleVersion;

    public MagicUtilsFabricBundleCommand(
            @Nullable LoggerCore logger,
            String bundleVersion,
            @Nullable Supplier<DiagnosticsService> diagnosticsServiceSupplier
    ) {
        this.logger = logger;
        this.bundleVersion = bundleVersion != null && !bundleVersion.isBlank() ? bundleVersion : "unknown";
        if (diagnosticsServiceSupplier != null) {
            // Adds `/magicutils diagnostics`, `/magicutils diagnostics export`
            // and `/magicutils diagnostics suite <name>`.
            for (SubCommandSpec<?> spec :
                    DiagnosticsCommandSupport.<Object>createDiagnosticsSubCommands(logger, diagnosticsServiceSupplier)) {
                addSubCommand(spec);
            }
        }
    }

    public CommandResult execute(@Sender MagicSender sender) {
        sendLines(sender, summaryLines());
        return CommandResult.success(false, "");
    }

    private List<String> summaryLines() {
        List<String> lines = new ArrayList<>();
        lines.add(headerLine());
        lines.add("<gray>Bundle:</gray> <white>MagicUtils Fabric Bundle</white>");
        lines.add("<gray>Version:</gray> <white>" + escape(bundleVersion) + "</white>");
        lines.add("<gray>Try:</gray> <white>/magicutils diagnostics</white>");
        return List.copyOf(lines);
    }

    private String headerLine() {
        return "<dark_gray>[</dark_gray><aqua>MagicUtils</aqua><dark_gray>]</dark_gray> <white>Fabric bundle</white>";
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
