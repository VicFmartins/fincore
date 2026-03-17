package com.fincore.infrastructure.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class WriteRateLimiter {
    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupWindow = new AtomicLong(Long.MIN_VALUE);

    public WriteRateLimiter(WriteRateLimitProperties properties) {
        this.maxRequests = properties.getMaxRequests();
        Duration window = Objects.requireNonNull(properties.getWindow(), "window must not be null");
        this.windowMillis = Math.max(1L, window.toMillis());
    }

    public RateLimitDecision allow(String key) {
        long now = System.currentTimeMillis();
        long currentWindow = now / windowMillis;

        WindowCounter updated = counters.compute(key, (ignored, current) -> {
            if (current == null || current.window() != currentWindow) {
                return new WindowCounter(currentWindow, 1);
            }
            return new WindowCounter(current.window(), current.count() + 1);
        });

        cleanupStaleEntries(currentWindow);

        boolean allowed = updated.count() <= maxRequests;
        long resetAtMillis = (updated.window() + 1) * windowMillis;
        return new RateLimitDecision(allowed, Math.max(0L, resetAtMillis - now));
    }

    public void reset() {
        counters.clear();
        lastCleanupWindow.set(Long.MIN_VALUE);
    }

    private void cleanupStaleEntries(long currentWindow) {
        long previousCleanup = lastCleanupWindow.get();
        if (previousCleanup == currentWindow || !lastCleanupWindow.compareAndSet(previousCleanup, currentWindow)) {
            return;
        }
        counters.entrySet().removeIf(entry -> entry.getValue().window() < currentWindow);
    }

    public record RateLimitDecision(boolean allowed, long retryAfterMillis) {
    }

    private record WindowCounter(long window, int count) {
    }
}
