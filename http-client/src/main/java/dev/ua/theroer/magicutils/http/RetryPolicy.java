package dev.ua.theroer.magicutils.http;

import java.net.http.HttpTimeoutException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Retry policy for HTTP requests with exponential backoff.
 */
public final class RetryPolicy {
    private final boolean enabled;
    private final int maxAttempts;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double multiplier;
    private final double jitter;
    private final Set<Integer> retryStatus;
    private final boolean retryOnIo;
    private final boolean retryOnTimeout;

    RetryPolicy(boolean enabled,
                int maxAttempts,
                long initialDelayMs,
                long maxDelayMs,
                double multiplier,
                double jitter,
                Set<Integer> retryStatus,
                boolean retryOnIo,
                boolean retryOnTimeout) {
        this.enabled = enabled;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialDelayMs = Math.max(0, initialDelayMs);
        this.maxDelayMs = Math.max(this.initialDelayMs, maxDelayMs);
        this.multiplier = multiplier > 0 ? multiplier : 2.0;
        this.jitter = clampJitter(jitter);
        this.retryStatus = retryStatus != null ? retryStatus : Set.of();
        this.retryOnIo = retryOnIo;
        this.retryOnTimeout = retryOnTimeout;
    }

    public static RetryPolicy fromConfig(HttpClientConfig.RetrySettings settings) {
        if (settings == null) {
            return disabled();
        }
        return new RetryPolicy(
                settings.isEnabled(),
                settings.getMaxAttempts(),
                settings.getInitialDelayMs(),
                settings.getMaxDelayMs(),
                settings.getMultiplier(),
                settings.getJitter(),
                toSet(settings.getRetryStatus()),
                settings.isRetryOnIo(),
                settings.isRetryOnTimeout()
        );
    }

    public static RetryPolicy disabled() {
        return new RetryPolicy(false, 1, 0, 0, 1.0, 0.0, Set.of(), false, false);
    }

    public boolean isEnabled() {
        return enabled && maxAttempts > 1;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public boolean shouldRetryStatus(int status) {
        return isEnabled() && retryStatus.contains(status);
    }

    public boolean shouldRetryThrowable(Throwable throwable) {
        if (!isEnabled() || throwable == null) {
            return false;
        }
        if (retryOnTimeout && throwable instanceof HttpTimeoutException) {
            return true;
        }
        return retryOnIo && throwable instanceof java.io.IOException;
    }

    public long nextDelayMs(int attempt) {
        if (attempt <= 0) {
            return 0;
        }
        double base = initialDelayMs * Math.pow(multiplier, attempt - 1);
        long delay = Math.min(maxDelayMs, Math.round(base));
        if (jitter <= 0) {
            return delay;
        }
        double variance = delay * jitter;
        double min = Math.max(0, delay - variance);
        double max = delay + variance;
        return Math.round(ThreadLocalRandom.current().nextDouble(min, max));
    }

    private static double clampJitter(double jitter) {
        if (jitter < 0) {
            return 0;
        }
        return Math.min(1.0, jitter);
    }

    private static Set<Integer> toSet(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(values);
    }
}
