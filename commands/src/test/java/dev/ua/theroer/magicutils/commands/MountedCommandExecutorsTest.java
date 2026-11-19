package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MountedCommandExecutorsTest {

    @Test
    void resolveResultPassesCommandResultThrough() {
        CommandResult result = CommandResult.failure("nope");
        assertSame(result, MountedCommandExecutors.resolveResult(result));
    }

    @Test
    void resolveResultAwaitsFutureAndTreatsNonResultsAsSuccess() {
        CommandResult awaited = CommandResult.success("done");
        assertSame(awaited, MountedCommandExecutors.resolveResult(CompletableFuture.completedFuture(awaited)));
        assertTrue(MountedCommandExecutors.resolveResult(CompletableFuture.completedFuture("x")).isSuccess());
        assertTrue(MountedCommandExecutors.resolveResult(null).isSuccess());
    }

    @Test
    void resolveResultRethrowsTheFutureFailureCause() {
        IllegalStateException boom = new IllegalStateException("boom");
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> MountedCommandExecutors.resolveResult(CompletableFuture.failedFuture(boom)));
        assertSame(boom, thrown);
    }

    @Test
    void forMethodInvokesOnTheMountedInstanceAndUnwrapsExceptions() throws Exception {
        Probe probe = new Probe();
        Method ok = Probe.class.getDeclaredMethod("ok", String.class);
        Method fail = Probe.class.getDeclaredMethod("fail");

        CommandResult result = MountedCommandExecutors.forMethod(probe, ok)
                .execute(execution(probe, new Object[] {"hi"}));
        assertTrue(result.isSuccess());
        assertEquals("hi", probe.last);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> MountedCommandExecutors.forMethod(probe, fail).execute(execution(probe, new Object[0])));
        assertEquals("bad", thrown.getMessage());
    }

    @Test
    void wrapRebindsExecutionToTheMountedCommand() {
        Probe mounted = new Probe();
        Probe other = new Probe();
        MagicCommand[] seen = new MagicCommand[1];
        CommandExecutor<Object> wrapped = MountedCommandExecutors.wrap(mounted, execution -> {
            seen[0] = execution.command();
            return CommandResult.success();
        });

        wrapped.execute(execution(other, new Object[0]));

        assertSame(mounted, seen[0]);
        assertNull(MountedCommandExecutors.wrap(mounted, null));
    }

    private static CommandExecution<Object> execution(MagicCommand command, Object[] parsedArgs) {
        return new CommandExecution<>(command, "probe", null, new Object(), List.of(), List.of(), parsedArgs);
    }

    @CommandInfo(name = "probe")
    static final class Probe extends MagicCommand {
        private String last;

        @SuppressWarnings("unused")
        CommandResult ok(String value) {
            this.last = value;
            return CommandResult.success();
        }

        @SuppressWarnings("unused")
        void fail() {
            throw new IllegalArgumentException("bad");
        }
    }
}
