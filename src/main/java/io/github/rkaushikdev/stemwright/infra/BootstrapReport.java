package io.github.rkaushikdev.stemwright.infra;

import java.util.Objects;
import java.util.Optional;

/**
 * What a start up did: the {@link ReplayReport replay} of the journal entries after the snapshot it
 * loaded, and which snapshot that was.
 *
 * @param applied the journal entries written into the stores after the snapshot
 * @param skipped the journal entries of a kind the replay does not handle, passed over
 * @param lastSeq the sequence number of the last journal entry replayed, or zero when there was none
 * @param fromSnapshot the sequence of the snapshot loaded first, absent when there was none and the
 *     whole journal was replayed
 */
public record BootstrapReport(int applied, int skipped, long lastSeq, Optional<Long> fromSnapshot) {

    public BootstrapReport {
        Objects.requireNonNull(fromSnapshot, "fromSnapshot");
    }
}
