package io.github.rkaushikdev.stemwright.http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts requests per key in fixed windows of a {@link RateLimit}, in memory.
 *
 * <p>Every method holds the limiter's lock, so concurrent requests under one key are each counted
 * once.
 */
public final class Limiter {

    private final RateLimit limit;
    private final Map<String, Window> windows = new HashMap<>();

    private Limiter(RateLimit limit) {
        this.limit = limit;
    }

    /** A limiter at {@link RateLimit#DEFAULT_LIMIT}. */
    public static Limiter create() {
        return create(RateLimit.DEFAULT_LIMIT);
    }

    public static Limiter create(RateLimit limit) {
        return new Limiter(limit);
    }

    /**
     * Counts one request under the key at the given time. A key with no window, or whose window began
     * a full window length ago or more, starts a new window with this request as its first.
     *
     * @param now the time of the request, in epoch milliseconds
     */
    public synchronized LimitState check(String key, long now) {
        Window existing = windows.get(key);
        if (existing == null || now - existing.start >= limit.windowMs()) {
            windows.put(key, new Window(now));
            return new LimitState(key, 1, now, limit.max() - 1);
        }
        existing.count += 1;
        return new LimitState(key, existing.count, existing.start, Math.max(0, limit.max() - existing.count));
    }

    /** Forgets every window. */
    public synchronized void reset() {
        windows.clear();
    }

    /** The keys with a window, sorted. */
    public synchronized List<String> keys() {
        return windows.keySet().stream().sorted().toList();
    }

    /** One key's current window. */
    private static final class Window {

        private final long start;
        private int count = 1;

        private Window(long start) {
            this.start = start;
        }
    }
}
