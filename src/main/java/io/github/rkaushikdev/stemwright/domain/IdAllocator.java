package io.github.rkaushikdev.stemwright.domain;

import io.github.rkaushikdev.stemwright.domain.text.Text;

/**
 * Hands out allocated ids: a counter plus a run token, so two processes writing the same journal do
 * not collide.
 *
 * <p>Built through {@link Identifiers#createAllocator(String, long)}. Not safe for concurrent use;
 * each writer owns its allocator.
 */
public final class IdAllocator {

    private final String token;
    private long counter;

    IdAllocator(String token, long start) {
        this.token = token;
        this.counter = start;
    }

    public String next(IdKind kind) {
        counter += 1;
        String tail = Text.padStart(Long.toString(counter, 36), 4, '0');
        return kind.prefix() + "_" + token + tail;
    }
}
