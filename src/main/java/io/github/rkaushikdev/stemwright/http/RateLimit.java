package io.github.rkaushikdev.stemwright.http;

import io.github.rkaushikdev.stemwright.domain.errors.PolicyException;
import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import java.util.function.LongSupplier;

/**
 * A fixed window limit, kept in memory by a {@link Limiter}.
 *
 * <p>It is deliberately the simplest thing that works for one process. A sliding window would be
 * fairer at the boundary and a shared store would survive a restart, and neither is worth having
 * until this runs on more than one box. What it is here for is stopping a client in a retry loop
 * from filling a journal, not stopping an attacker.
 *
 * <p>The key is the actor when there is one and the address otherwise, so one learner behind a
 * shared campus address cannot lock out the rest of the class.
 *
 * @param windowMs how long a window lasts, in milliseconds
 * @param max how many requests a key may make in one window
 */
public record RateLimit(long windowMs, int max) {

    /** A hundred and twenty requests a minute. */
    public static final RateLimit DEFAULT_LIMIT = new RateLimit(60_000, 120);

    /**
     * The key a request is counted under: its token when it carries one, and otherwise the address
     * it was forwarded for, or {@code address:unknown}.
     */
    public static String keyFor(HttpRequest request) {
        String authorization = request.headers().containsKey("authorization")
                ? request.headers().get("authorization")
                : request.headers().get("Authorization");
        if (authorization != null && !authorization.isEmpty()) {
            return "token:" + authorization;
        }
        return "address:" + request.headers().getOrDefault("x-forwarded-for", "unknown");
    }

    /** {@link #withRateLimit(Handler, Limiter, RateLimit, LongSupplier)} at the default limit, on the system clock. */
    public static Handler withRateLimit(Handler handler, Limiter limiter) {
        return withRateLimit(handler, limiter, DEFAULT_LIMIT);
    }

    /** {@link #withRateLimit(Handler, Limiter, RateLimit, LongSupplier)} on the system clock. */
    public static Handler withRateLimit(Handler handler, Limiter limiter, RateLimit limit) {
        return withRateLimit(handler, limiter, limit, System::currentTimeMillis);
    }

    /**
     * Wraps a handler so every request is counted before it is answered. Going over the limit is a
     * policy failure rather than a validation one, because the request was well formed and a rule
     * refused it, and the http layer already maps that to a status of its own.
     *
     * @param now the current time in epoch milliseconds, read once per request
     */
    public static Handler withRateLimit(Handler handler, Limiter limiter, RateLimit limit, LongSupplier now) {
        return request -> {
            LimitState state = limiter.check(keyFor(request), now.getAsLong());
            if (state.isOver(limit)) {
                throw new PolicyException(
                        "rate.limit",
                        "too many requests, " + limit.max() + " are allowed every " + limit.windowMs()
                                + " milliseconds");
            }
            return handler.handle(request);
        };
    }

    /** The limit for a person, such as {@code 120 requests every 60 seconds}. */
    public String describe() {
        long seconds = Numbers.round(windowMs / 1000.0);
        return max + " requests every " + seconds + " seconds";
    }
}
