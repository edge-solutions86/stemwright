package io.github.rkaushikdev.stemwright.infra;

import io.github.rkaushikdev.stemwright.infra.json.Json;
import java.util.Objects;

/**
 * An entry as a caller hands it to {@link Journal#append}: what happened, without the sequence
 * number and the stamp, which only the journal assigns.
 *
 * @param kind what the entry records
 * @param payload the record itself
 */
public record JournalAppend(JournalKind kind, Json payload) {

    public JournalAppend {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(payload, "payload");
    }
}
