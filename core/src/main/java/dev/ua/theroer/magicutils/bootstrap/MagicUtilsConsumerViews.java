package dev.ua.theroer.magicutils.bootstrap;

import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerRuntimeView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Builds live {@link MagicUtilsConsumerRuntimeView}s from a consumer's {@link
 * MagicRuntime}. Both the Fabric and NeoForge bootstraps register a view here so
 * the component/count extraction is written once. The dynamic bits that live
 * outside core (root command count from the command registry, whether the
 * diagnostics service is present) are passed in as suppliers so core stays free
 * of the commands/diagnostics wiring while still reading them live.
 */
public final class MagicUtilsConsumerViews {
    private MagicUtilsConsumerViews() {
    }

    /**
     * Live view backed by {@code runtime}. Every accessor reads through to the
     * runtime (or the supplied suppliers) at call time, so the registry always
     * reports the consumer's current state.
     *
     * @param runtime the consumer runtime to read components/closed from
     * @param rootCommandCount live root command count (0 when commands are off)
     * @param diagnosticsEnabled live diagnostics-present check
     */
    public static MagicUtilsConsumerRuntimeView liveView(
            MagicRuntime runtime,
            IntSupplier rootCommandCount,
            BooleanSupplier diagnosticsEnabled
    ) {
        return new MagicUtilsConsumerRuntimeView() {
            @Override
            public int rootCommandCount() {
                return rootCommandCount.getAsInt();
            }

            @Override
            public int typedComponentCount() {
                return runtime.components().size();
            }

            @Override
            public int namedComponentCount() {
                return runtime.namedComponents().size();
            }

            @Override
            public List<String> namedComponentNames() {
                return new ArrayList<>(runtime.namedComponents().keySet());
            }

            @Override
            public boolean diagnosticsEnabled() {
                return diagnosticsEnabled.getAsBoolean();
            }

            @Override
            public boolean closed() {
                return runtime.isClosed();
            }
        };
    }
}
