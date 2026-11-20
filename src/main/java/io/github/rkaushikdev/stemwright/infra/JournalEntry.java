package io.github.rkaushikdev.stemwright.infra;

import io.github.rkaushikdev.stemwright.domain.errors.IntegrityException;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import io.github.rkaushikdev.stemwright.infra.json.JsonSyntaxException;
import java.util.Objects;

/**
 * One line of the journal: what happened, when, and where it falls in the order of everything else.
 *
 * @param seq the position in the journal, from one, which only the journal assigns
 * @param at the instant the entry was written, in the form {@link
 *     io.github.rkaushikdev.stemwright.domain.time.Instants#toIsoString} prints
 * @param kind what the entry records
 * @param payload the record itself
 */
public record JournalEntry(long seq, String at, JournalKind kind, Json payload) {

    public JournalEntry {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(payload, "payload");
    }

    /** The entry as one line of compact json, without the line end. */
    public String encode() {
        return Json.stringify(Json.object()
                .put("seq", seq)
                .put("at", at)
                .put("kind", kind.key())
                .put("payload", payload)
                .build());
    }

    /**
     * Reads one line back.
     *
     * <p>Decoding a line is where a torn write shows up. A half written last line is the normal way
     * an append only file ends after a crash, so that failure, and a line that is not an object or
     * names a kind this build does not know, is an {@link IntegrityException} naming the line. A line
     * that parsed but holds a field of the wrong shape fails as a {@link ValidationException} naming
     * the field, under {@code journal.<line>}.
     *
     * @param lineNumber the line's position among the journal's non blank lines, from one
     */
    public static JournalEntry decode(String line, int lineNumber) {
        Json parsed;
        try {
            parsed = Json.parse(line);
        } catch (JsonSyntaxException error) {
            throw new IntegrityException("journal line " + lineNumber + " is not valid json", error);
        }
        if (!(parsed instanceof JsonObject row)) {
            throw new IntegrityException("journal line " + lineNumber + " is not an object");
        }

        String path = "journal." + lineNumber;
        String key = Codec.readString(row, path, "kind");
        JournalKind kind = JournalKind.fromKey(key)
                .orElseThrow(
                        () -> new IntegrityException("journal line " + lineNumber + " has an unknown kind: " + key));
        Json payload = row.get("payload").orElseThrow(() -> ValidationException.field(path + ".payload", "is missing"));

        return new JournalEntry(
                Codec.readWholeNumber(row, path, "seq"), Codec.readString(row, path, "at"), kind, payload);
    }

    /** The payload as an object, refusing anything else under {@code journal.<seq>.payload}. */
    public JsonObject payloadObject() {
        return Codec.requireObject("journal." + seq + ".payload", payload);
    }
}
