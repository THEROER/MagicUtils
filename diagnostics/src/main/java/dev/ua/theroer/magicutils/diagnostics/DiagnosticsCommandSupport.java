package dev.ua.theroer.magicutils.diagnostics;

import dev.ua.theroer.magicutils.commands.AllowedSender;
import dev.ua.theroer.magicutils.commands.CommandArgument;
import dev.ua.theroer.magicutils.commands.CommandManager;
import dev.ua.theroer.magicutils.commands.CommandResult;
import dev.ua.theroer.magicutils.commands.CommandThreading;
import dev.ua.theroer.magicutils.commands.MagicPermissionDefault;
import dev.ua.theroer.magicutils.commands.MagicSender;
import dev.ua.theroer.magicutils.commands.SubCommandSpec;
import dev.ua.theroer.magicutils.commands.TypeParserRegistry;
import dev.ua.theroer.magicutils.logger.LogBuilderCore;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.logger.LogTarget;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.MessageParser;
import dev.ua.theroer.magicutils.platform.Audience;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Reusable diagnostics subcommand helper.
 */
@SuppressWarnings("doclint:missing")
public final class DiagnosticsCommandSupport {
    private static final String DEFAULT_SUBCOMMAND = "diagnostics";

    private DiagnosticsCommandSupport() {
    }

    public static void registerTypeParsers(TypeParserRegistry<?> registry) {
        if (registry == null) {
            return;
        }
        registerTypeParsersUnchecked(registry);
    }

    public static void registerTypeParsers(CommandManager<?> commandManager) {
        if (commandManager == null) {
            return;
        }
        registerTypeParsers(commandManager.getTypeParserRegistry());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerTypeParsersUnchecked(TypeParserRegistry registry) {
        registry.register(new DiagnosticSuiteNameTypeParser<>());
    }

    public static <S> SubCommandSpec<S> createDiagnosticsSubCommand(
            LoggerCore logger,
            Supplier<DiagnosticsService> serviceSupplier
    ) {
        return createDiagnosticsSubCommand("diagnostics", logger, serviceSupplier);
    }

    public static <S> SubCommandSpec<S> createDiagnosticsSubCommand(
            String subCommandName,
            LoggerCore logger,
            Supplier<DiagnosticsService> serviceSupplier
    ) {
        String name = normalizeName(subCommandName);
        return SubCommandSpec.<S>builder(name)
                .description("Runs MagicUtils runtime diagnostics")
                .permissionDefault(MagicPermissionDefault.OP)
                .threading(CommandThreading.ASYNC)
                .argument(CommandArgument.builder("sender", MagicSender.class)
                        .sender(new AllowedSender[] { AllowedSender.ANY })
                        .build())
                .argument(CommandArgument.builder("mode", DiagnosticMode.class)
                        .optional()
                        .build())
                .execute(execution -> {
                    return executeAll(
                            logger,
                            serviceSupplier,
                            execution.arg("sender", MagicSender.class),
                            execution.arg("mode", DiagnosticMode.class)
                    );
                })
                .build();
    }

    public static <S> List<SubCommandSpec<S>> createDiagnosticsSubCommands(
            LoggerCore logger,
            Supplier<DiagnosticsService> serviceSupplier
    ) {
        return createDiagnosticsSubCommands(DEFAULT_SUBCOMMAND, logger, serviceSupplier);
    }

    public static <S> List<SubCommandSpec<S>> createDiagnosticsSubCommands(
            String subCommandName,
            LoggerCore logger,
            Supplier<DiagnosticsService> serviceSupplier
    ) {
        String name = normalizeName(subCommandName);
        List<SubCommandSpec<S>> specs = new ArrayList<>();
        specs.add(createDiagnosticsSubCommand(name, logger, serviceSupplier));
        specs.add(createDiagnosticsExportSubCommand(name, logger, serviceSupplier));
        specs.add(createDiagnosticsSuiteSubCommand(name, logger, serviceSupplier));
        return List.copyOf(specs);
    }

    public static <S> SubCommandSpec<S> createDiagnosticsExportSubCommand(
            LoggerCore logger,
            Supplier<DiagnosticsService> serviceSupplier
    ) {
        return createDiagnosticsExportSubCommand(DEFAULT_SUBCOMMAND, logger, serviceSupplier);
    }

    public static <S> SubCommandSpec<S> createDiagnosticsExportSubCommand(
            String rootSubCommandName,
            LoggerCore logger,
            Supplier<DiagnosticsService> serviceSupplier
    ) {
        String rootName = normalizeName(rootSubCommandName);
        return SubCommandSpec.<S>builder("export")
                .path(rootName)
                .description("Runs diagnostics and exports the JSON report")
                .permissionDefault(MagicPermissionDefault.OP)
                .threading(CommandThreading.ASYNC)
                .argument(CommandArgument.builder("sender", MagicSender.class)
                        .sender(new AllowedSender[] { AllowedSender.ANY })
                        .build())
                .argument(CommandArgument.builder("mode", DiagnosticMode.class)
                        .optional()
                        .build())
                .execute(execution -> executeExport(
                        logger,
                        serviceSupplier,
                        execution.arg("sender", MagicSender.class),
                        execution.arg("mode", DiagnosticMode.class)
                ))
                .build();
    }

    public static <S> SubCommandSpec<S> createDiagnosticsSuiteSubCommand(
            LoggerCore logger,
            Supplier<DiagnosticsService> serviceSupplier
    ) {
        return createDiagnosticsSuiteSubCommand(DEFAULT_SUBCOMMAND, logger, serviceSupplier);
    }

    public static <S> SubCommandSpec<S> createDiagnosticsSuiteSubCommand(
            String rootSubCommandName,
            LoggerCore logger,
            Supplier<DiagnosticsService> serviceSupplier
    ) {
        String rootName = normalizeName(rootSubCommandName);
        return SubCommandSpec.<S>builder("suite")
                .path(rootName)
                .description("Runs diagnostics for a single suite")
                .permissionDefault(MagicPermissionDefault.OP)
                .threading(CommandThreading.ASYNC)
                .argument(CommandArgument.builder("sender", MagicSender.class)
                        .sender(new AllowedSender[] { AllowedSender.ANY })
                        .build())
                .argument(CommandArgument.builder("suite", DiagnosticSuiteName.class).build())
                .argument(CommandArgument.builder("mode", DiagnosticMode.class)
                        .optional()
                        .build())
                .execute(execution -> executeSuite(
                        logger,
                        serviceSupplier,
                        execution.arg("sender", MagicSender.class),
                        execution.arg("suite", DiagnosticSuiteName.class),
                        execution.arg("mode", DiagnosticMode.class)
                ))
                .build();
    }

    public static List<String> renderReport(DiagnosticReport report) {
        return DiagnosticReports.renderText(report);
    }

    static List<String> renderReport(DiagnosticReport report, @Nullable MagicSender sender) {
        if (sender != null && sender.id() != null) {
            return DiagnosticReports.renderText(report);
        }
        return DiagnosticReports.renderVerboseText(report);
    }

    private static String normalizeName(@Nullable String subCommandName) {
        return subCommandName != null && !subCommandName.isBlank()
                ? subCommandName.trim()
                : DEFAULT_SUBCOMMAND;
    }

    private static CommandResult executeAll(
            @Nullable LoggerCore logger,
            Supplier<DiagnosticsService> serviceSupplier,
            @Nullable MagicSender sender,
            @Nullable DiagnosticMode mode
    ) {
        DiagnosticsRuntime runtime = resolveRuntime(serviceSupplier, sender);
        if (runtime.errorResult() != null) {
            return runtime.errorResult();
        }
        return completeReport(
                logger,
                runtime.sender(),
                runtime.service().runAll(request(mode)),
                runtime.service(),
                false
        );
    }

    private static CommandResult executeExport(
            @Nullable LoggerCore logger,
            Supplier<DiagnosticsService> serviceSupplier,
            @Nullable MagicSender sender,
            @Nullable DiagnosticMode mode
    ) {
        DiagnosticsRuntime runtime = resolveRuntime(serviceSupplier, sender);
        if (runtime.errorResult() != null) {
            return runtime.errorResult();
        }
        return completeReport(
                logger,
                runtime.sender(),
                runtime.service().runAll(request(mode)),
                runtime.service(),
                true
        );
    }

    private static CommandResult executeSuite(
            @Nullable LoggerCore logger,
            Supplier<DiagnosticsService> serviceSupplier,
            @Nullable MagicSender sender,
            @Nullable DiagnosticSuiteName suiteName,
            @Nullable DiagnosticMode mode
    ) {
        DiagnosticsRuntime runtime = resolveRuntime(serviceSupplier, sender);
        if (runtime.errorResult() != null) {
            return runtime.errorResult();
        }
        if (suiteName == null) {
            return CommandResult.failure("Suite name is required: /<command> diagnostics suite <suite>");
        }
        return completeReport(
                logger,
                runtime.sender(),
                runtime.service().runSuite(suiteName.value(), request(mode)),
                runtime.service(),
                false
        );
    }

    private static CommandResult completeReport(
            @Nullable LoggerCore logger,
            MagicSender sender,
            DiagnosticReport report,
            DiagnosticsService service,
            boolean exportJson
    ) {
        sendLines(logger, sender, renderReport(report, sender));
        if (exportJson) {
            Path exportPath = service.exportJson(report);
            sendLines(logger, sender, List.of(
                    "<dark_gray>[</dark_gray><green>Export</green><dark_gray>]</dark_gray> <gray>Saved diagnostics report to</gray> <white>"
                            + DiagnosticReports.escapeText(exportPath.toAbsolutePath().toString())
                            + "</white>"
            ));
        }
        return CommandResult.success(false, "");
    }

    private static DiagnosticsRuntime resolveRuntime(
            Supplier<DiagnosticsService> serviceSupplier,
            @Nullable MagicSender sender
    ) {
        if (sender == null) {
            return new DiagnosticsRuntime(null, null, CommandResult.failure("Sender unavailable"));
        }
        DiagnosticsService service = serviceSupplier != null ? serviceSupplier.get() : null;
        if (service == null) {
            return new DiagnosticsRuntime(sender, null, CommandResult.failure("Diagnostics unavailable (service not ready)"));
        }
        return new DiagnosticsRuntime(sender, service, null);
    }

    private static DiagnosticRunRequest request(@Nullable DiagnosticMode mode) {
        return new DiagnosticRunRequest(
                mode != null ? mode : DiagnosticMode.SAFE,
                true,
                DiagnosticRunRequest.DEFAULT_TIMEOUT,
                null
        );
    }

    private static void sendLines(@Nullable LoggerCore logger, MagicSender sender, List<String> lines) {
        Objects.requireNonNull(sender, "sender");
        if (lines == null || lines.isEmpty()) {
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

    private record DiagnosticsRuntime(
            @Nullable MagicSender sender,
            @Nullable DiagnosticsService service,
            @Nullable CommandResult errorResult
    ) {
    }
}
