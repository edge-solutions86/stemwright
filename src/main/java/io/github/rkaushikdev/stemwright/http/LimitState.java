package io.github.rkaushikdev.stemwright.http;

/**
 * Where one key stands in its current window, as {@link Limiter#check} counted it.
 *
 * @param key the key that was counted
 * @param count the requests in the window, this one included
 * @param windowStart when the window began, in epoch milliseconds
 * @param remaining how many more requests the window allows, held at zero from the second request
 *     on
 */
public record LimitState(String key, int count, long windowStart, int remaining) {

    /** True once the count has gone past the limit's maximum; reaching it exactly is still allowed. */
    public boolean isOver(RateLimit limit) {
        return count > limit.max();
    }
}
