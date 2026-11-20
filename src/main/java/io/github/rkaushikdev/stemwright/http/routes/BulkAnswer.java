package io.github.rkaushikdev.stemwright.http.routes;

import java.util.Objects;

/**
 * One answer of a batch, as {@link BulkRoutes#readBatch} reads it off a request body.
 *
 * @param cellId the cell answered
 * @param given what the learner typed
 */
public record BulkAnswer(String cellId, String given) {

    public BulkAnswer {
        Objects.requireNonNull(cellId, "cellId");
        Objects.requireNonNull(given, "given");
    }
}
