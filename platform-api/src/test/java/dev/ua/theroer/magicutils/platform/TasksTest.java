package dev.ua.theroer.magicutils.platform;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class TasksTest {

    @Test
    void runOnMainCompletesExceptionallyWhenDispatchFails() {
        IllegalStateException failure = new IllegalStateException("boom");
        TestPlatform platform = new TestPlatform(false, failure);

        CompletableFuture<Void> future = Tasks.runOnMain(platform, () -> fail("task should not run"));

        CompletionException error = assertThrows(CompletionException.class, future::join);
        assertSame(failure, error.getCause());
    }

    @Test
    void callOnMainCompletesExceptionallyWhenDispatchFails() {
        IllegalStateException failure = new IllegalStateException("boom");
        TestPlatform platform = new TestPlatform(false, failure);

        CompletableFuture<String> future = Tasks.callOnMain(platform, () -> {
            fail("supplier should not run");
            return "unreachable";
        });

        CompletionException error = assertThrows(CompletionException.class, future::join);
        assertSame(failure, error.getCause());
    }

    @Test
    void thenOnMainCompletesExceptionallyWhenDispatchFails() {
        IllegalStateException failure = new IllegalStateException("boom");
        TestPlatform platform = new TestPlatform(false, failure);

        CompletableFuture<String> future = Tasks.thenOnMain(platform, CompletableFuture.completedFuture("value"));

        CompletionException error = assertThrows(CompletionException.class, future::join);
        assertSame(failure, error.getCause());
    }

    private static final class TestPlatform implements Platform {
        private final boolean mainThread;
        private final RuntimeException dispatchFailure;

        private TestPlatform(boolean mainThread, RuntimeException dispatchFailure) {
            this.mainThread = mainThread;
            this.dispatchFailure = dispatchFailure;
        }

        @Override
        public Path configDir() {
            return Path.of(".");
        }

        @Override
        public PlatformLogger logger() {
            return NoOpLogger.INSTANCE;
        }

        @Override
        public Audience console() {
            return NoOpAudience.INSTANCE;
        }

        @Override
        public Collection<Audience> onlinePlayers() {
            return Collections.emptyList();
        }

        @Override
        public void runOnMain(Runnable task) {
            if (dispatchFailure != null) {
                throw dispatchFailure;
            }
            if (task != null) {
                task.run();
            }
        }

        @Override
        public boolean isMainThread() {
            return mainThread;
        }
    }

    private enum NoOpLogger implements PlatformLogger {
        INSTANCE;

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void warn(String message, Throwable throwable) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }

        @Override
        public void debug(String message) {
        }
    }

    private enum NoOpAudience implements Audience {
        INSTANCE;

        @Override
        public void send(Component component) {
        }
    }
}
