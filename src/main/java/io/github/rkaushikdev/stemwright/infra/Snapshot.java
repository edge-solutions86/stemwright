package io.github.rkaushikdev.stemwright.infra;

import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.errors.IntegrityException;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import io.github.rkaushikdev.stemwright.domain.time.Instants;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonArray;
import io.github.rkaushikdev.stemwright.infra.json.JsonBoolean;
import io.github.rkaushikdev.stemwright.infra.json.JsonNull;
import io.github.rkaushikdev.stemwright.infra.json.JsonNumber;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import io.github.rkaushikdev.stemwright.infra.json.JsonString;
import io.github.rkaushikdev.stemwright.infra.json.JsonSyntaxException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A snapshot is a shortcut, never a source of truth. It records the state at one journal sequence,
 * and a start up loads the newest one and replays only what came after it.
 *
 * <p>That makes the correctness argument short: if the snapshot is wrong or missing, deleting it and
 * replaying from the beginning gives the same answer. Anything that could only be recovered from a
 * snapshot would break that, so nothing is written here that the journal does not already hold.
 *
 * <p>On disk a snapshot is one json document, {@code snapshots/<seq>.json} under the {@link
 * DataLayout}, holding the sequence, the stamp and the learners first and then the payload's fields.
 *
 * @param seq the journal sequence the state was taken at
 * @param at when it was taken, in the form {@link Instants#toIsoString} prints
 * @param learners the learners it covers, sorted
 * @param payload the state: {@code ledgers}, the encoded ledgers, and {@code attempts}, the encoded
 *     attempts
 */
public record Snapshot(long seq, String at, List<String> learners, JsonObject payload) {

    public Snapshot {
        Objects.requireNonNull(at, "at");
        learners = List.copyOf(learners);
        Objects.requireNonNull(payload, "payload");
    }

    /**
     * Takes the state of the learners given: each one's ledger, when there is one, and every one of
     * their attempts, learner by learner in sorted order, so two snapshots of one state compare equal
     * whatever order the learners were named in.
     */
    public static Snapshot build(Stores stores, List<String> learnerIds, long seq, Instant at) {
        List<String> learners = learnerIds.stream().sorted().toList();
        List<Json> ledgers = new ArrayList<>();
        List<Json> attempts = new ArrayList<>();

        for (String learnerId : learners) {
            stores.ledgers().get(learnerId).ifPresent(ledger -> ledgers.add(Records.encodeLedger(ledger)));
            for (Attempt entry : stores.attempts().listByLearner(learnerId)) {
                attempts.add(Records.encodeAttempt(entry));
            }
        }

        JsonObject payload = Json.object()
                .put("ledgers", Json.array(ledgers))
                .put("attempts", Json.array(attempts))
                .build();
        return new Snapshot(seq, Instants.toIsoString(at), learners, payload);
    }

    /** The document as it is written to disk: indented by two spaces, with no line end after it. */
    public String encode() {
        JsonObject.Builder document =
                Json.object().put("seq", seq).put("at", at).put("learners", Json.strings(learners));
        payload.fields().forEach(document::put);
        return Json.stringify(document.build(), 2);
    }

    /** {@link #decode(String, String)} reporting failures under {@code snapshot}. */
    public static Snapshot decode(String text) {
        return decode(text, "snapshot");
    }

    /**
     * Reads a snapshot document back. A missing or {@code null} {@code ledgers} or {@code attempts}
     * reads as an empty list, and a learner that is not a string is read as the text JavaScript's
     * {@code String} gives it, as the original did.
     *
     * @throws IntegrityException when the text is not json or holds no {@code learners} array
     * @throws ValidationException when it is not an object, or its sequence is not a whole number or
     *     its stamp not a string, naming the field under {@code path}
     */
    public static Snapshot decode(String text, String path) {
        Json parsed;
        try {
            parsed = Json.parse(text);
        } catch (JsonSyntaxException error) {
            throw new IntegrityException(path + " is not valid json", error);
        }
        JsonObject row = Codec.requireObject(path, parsed);
        if (!(row.get("learners").orElse(Json.NULL) instanceof JsonArray learners)) {
            throw new IntegrityException(path + ".learners is not an array");
        }
        long seq = Codec.readWholeNumber(row, path, "seq");
        String at = Codec.readString(row, path, "at");
        List<String> names = learners.items().stream().map(Snapshot::asText).toList();
        JsonObject payload = Json.object()
                .put("ledgers", presentOrEmpty(row, "ledgers"))
                .put("attempts", presentOrEmpty(row, "attempts"))
                .build();
        return new Snapshot(seq, at, names, payload);
    }

    /**
     * Writes the snapshot to its place under the layout, replacing in one step any snapshot already
     * written at the same sequence, and returns where it went.
     */
    public static Path write(DataLayout layout, Snapshot snapshot) {
        Path path = layout.snapshotPath(snapshot.seq());
        DataFiles.ensureDirectory(layout.snapshotDir());
        DataFiles.atomicWrite(path, snapshot.encode());
        return path;
    }

    /**
     * The sequences of the snapshots written under the layout, oldest first, and none when the
     * snapshot directory does not exist yet. A name that is not a snapshot's is passed over.
     */
    public static List<Long> list(DataLayout layout) {
        try (Stream<Path> names = Files.list(layout.snapshotDir())) {
            return names.map(name -> DataLayout.snapshotSeq(name.getFileName().toString()))
                    .flatMap(Optional::stream)
                    .sorted()
                    .toList();
        } catch (IOException error) {
            if (DataFiles.isMissing(error)) {
                return List.of();
            }
            throw new UncheckedIOException(error);
        }
    }

    /**
     * The newest snapshot written under the layout, or nothing when there is none. A newest snapshot
     * that cannot be read back fails as {@link #decode(String)} does, and deleting it always cures
     * that: the journal holds everything it held.
     */
    public static Optional<Snapshot> latest(DataLayout layout) {
        List<Long> seqs = list(layout);
        if (seqs.isEmpty()) {
            return Optional.empty();
        }
        return DataFiles.readIfPresent(layout.snapshotPath(seqs.get(seqs.size() - 1)))
                .map(Snapshot::decode);
    }

    /**
     * Puts the snapshot's ledgers and then its attempts into the stores, the ledgers one at a time and
     * the attempts in one append once every one of them has been read. A payload field that is not an
     * array is passed over.
     *
     * @throws ValidationException when a record is not usable, naming it as {@code
     *     snapshot.ledgers.<index>} or {@code snapshot.attempts.<index>}
     */
    public void restore(Stores stores) {
        if (payload.get("ledgers").orElse(Json.NULL) instanceof JsonArray ledgers) {
            for (int index = 0; index < ledgers.size(); index += 1) {
                stores.ledgers().put(Records.decodeLedger(ledgers.get(index), "snapshot.ledgers." + index));
            }
        }
        if (payload.get("attempts").orElse(Json.NULL) instanceof JsonArray attempts) {
            List<Attempt> decoded = new ArrayList<>(attempts.size());
            for (int index = 0; index < attempts.size(); index += 1) {
                decoded.add(Records.decodeAttempt(attempts.get(index), "snapshot.attempts." + index));
            }
            stores.attempts().append(decoded);
        }
    }

    /**
     * Start up. Load the newest snapshot, then replay the journal from the entry after it. With no
     * snapshot the replay starts at one, which is the same answer taken the slow way.
     */
    public static BootstrapReport bootstrap(DataLayout layout, Journal journal, Stores stores) {
        Optional<Snapshot> snapshot = latest(layout);
        snapshot.ifPresent(found -> found.restore(stores));
        long from = snapshot.map(found -> found.seq() + 1).orElse(1L);
        ReplayReport report = Replay.apply(journal.read(from), stores);
        return new BootstrapReport(report.applied(), report.skipped(), report.lastSeq(), snapshot.map(Snapshot::seq));
    }

    private static Json presentOrEmpty(JsonObject row, String key) {
        return row.get(key).filter(value -> !(value instanceof JsonNull)).orElse(JsonArray.EMPTY);
    }

    /** The text JavaScript's {@code String(value)} gives a parsed json value. */
    private static String asText(Json value) {
        return switch (value) {
            case JsonString string -> string.value();
            case JsonNumber number -> Numbers.text(number.value());
            case JsonBoolean flag -> Boolean.toString(flag.value());
            case JsonNull nothing -> "null";
            case JsonArray array ->
                array.items().stream()
                        .map(item -> item instanceof JsonNull ? "" : asText(item))
                        .collect(Collectors.joining(","));
            case JsonObject object -> "[object Object]";
        };
    }
}
