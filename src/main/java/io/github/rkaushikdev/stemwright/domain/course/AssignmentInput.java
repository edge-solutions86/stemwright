package io.github.rkaushikdev.stemwright.domain.course;

import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * An assignment as an instructor or a request writes it, before {@link Assignment#of} checks it
 * against the course and the schema.
 *
 * @param key the assignment key, which becomes a slug
 * @param title the printed name, defaulted to the key when absent
 * @param deckKey the course deck the assignment narrows, which becomes a slug
 * @param narrowing the written selector that narrows the deck, the whole deck when absent
 * @param from the first day of the assignment window
 * @param to the last day of the assignment window, the day it is due
 * @param masteredCells the mastery target, if there is one
 * @param practiceDays the effort target, if there is one
 * @param toleranceLevel the tolerance level the assignment marks at, overriding the deck's
 * @param policyPatch the assignment's adjustment of the drilling policy, none when absent
 */
public record AssignmentInput(
        String key,
        Optional<String> title,
        String deckKey,
        Optional<String> narrowing,
        LocalDate from,
        LocalDate to,
        Optional<Integer> masteredCells,
        Optional<Integer> practiceDays,
        Optional<String> toleranceLevel,
        Optional<DrillPolicyPatch> policyPatch) {

    public AssignmentInput {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(deckKey, "deckKey");
        Objects.requireNonNull(narrowing, "narrowing");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(masteredCells, "masteredCells");
        Objects.requireNonNull(practiceDays, "practiceDays");
        Objects.requireNonNull(toleranceLevel, "toleranceLevel");
        Objects.requireNonNull(policyPatch, "policyPatch");
    }

    /** A builder whose optional parts all start absent. */
    public static Builder builder(String key, String deckKey, LocalDate from, LocalDate to) {
        return new Builder(key, deckKey, from, to);
    }

    /** Collects the optional parts of an assignment; any left unset take the defaults above. */
    public static final class Builder {

        private final String key;
        private final String deckKey;
        private final LocalDate from;
        private final LocalDate to;
        private String title;
        private String narrowing;
        private Integer masteredCells;
        private Integer practiceDays;
        private String toleranceLevel;
        private DrillPolicyPatch policyPatch;

        private Builder(String key, String deckKey, LocalDate from, LocalDate to) {
            this.key = Objects.requireNonNull(key, "key");
            this.deckKey = Objects.requireNonNull(deckKey, "deckKey");
            this.from = Objects.requireNonNull(from, "from");
            this.to = Objects.requireNonNull(to, "to");
        }

        public Builder title(String title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /** The selector as written, such as {@code number=sg}. */
        public Builder narrowing(String narrowing) {
            this.narrowing = Objects.requireNonNull(narrowing, "narrowing");
            return this;
        }

        public Builder masteredCells(int masteredCells) {
            this.masteredCells = masteredCells;
            return this;
        }

        public Builder practiceDays(int practiceDays) {
            this.practiceDays = practiceDays;
            return this;
        }

        public Builder toleranceLevel(String toleranceLevel) {
            this.toleranceLevel = Objects.requireNonNull(toleranceLevel, "toleranceLevel");
            return this;
        }

        public Builder policyPatch(DrillPolicyPatch policyPatch) {
            this.policyPatch = Objects.requireNonNull(policyPatch, "policyPatch");
            return this;
        }

        public AssignmentInput build() {
            return new AssignmentInput(
                    key,
                    Optional.ofNullable(title),
                    deckKey,
                    Optional.ofNullable(narrowing),
                    from,
                    to,
                    Optional.ofNullable(masteredCells),
                    Optional.ofNullable(practiceDays),
                    Optional.ofNullable(toleranceLevel),
                    Optional.ofNullable(policyPatch));
        }
    }
}
