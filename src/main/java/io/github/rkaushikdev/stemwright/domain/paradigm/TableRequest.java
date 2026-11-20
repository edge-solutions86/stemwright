package io.github.rkaushikdev.stemwright.domain.paradigm;

import io.github.rkaushikdev.stemwright.domain.features.FeatureBundle;
import java.util.Objects;
import java.util.Optional;

/**
 * Which grid to print: one dimension down the side, one across the top, and the values every other
 * required dimension is held at.
 *
 * @param rowDimension the dimension key for the rows
 * @param columnDimension the dimension key for the columns
 * @param fixed the values of the dimensions the grid does not use
 */
public record TableRequest(String rowDimension, String columnDimension, Optional<FeatureBundle> fixed) {

    public TableRequest {
        Objects.requireNonNull(rowDimension, "rowDimension");
        Objects.requireNonNull(columnDimension, "columnDimension");
        Objects.requireNonNull(fixed, "fixed");
    }

    /** A grid that fixes nothing, for a schema with no third required dimension. */
    public static TableRequest of(String rowDimension, String columnDimension) {
        return new TableRequest(rowDimension, columnDimension, Optional.empty());
    }

    /** A copy holding the other dimensions at the bundle's values. */
    public TableRequest withFixed(FeatureBundle fixed) {
        return new TableRequest(rowDimension, columnDimension, Optional.of(fixed));
    }
}
