package io.github.rkaushikdev.stemwright.io;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A csv file read as named records, which is what {@link Csv#readTable} returns.
 *
 * @param headers the column names, in file order
 * @param rows one map per line under the header, keyed by column name in header order
 */
public record CsvTable(List<String> headers, List<Map<String, String>> rows) {

    public CsvTable {
        headers = List.copyOf(headers);
        rows = rows.stream()
                .map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row)))
                .toList();
    }

    /** Refuses a table missing any of the wanted columns, naming every one that is missing. */
    public void requireColumns(List<String> wanted) {
        List<String> missing =
                wanted.stream().filter(column -> !headers.contains(column)).toList();
        if (!missing.isEmpty()) {
            throw new ValidationException("the file is missing the columns " + String.join(", ", missing));
        }
    }
}
