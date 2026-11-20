package io.github.rkaushikdev.stemwright.domain.course;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A course as an editor or a request writes it, before {@link Course#of} checks it.
 *
 * @param key the course key, which becomes a slug
 * @param title the printed name, defaulted to the key when absent
 * @param language the language key, which becomes a slug
 * @param from the first day of the term
 * @param to the last day of the term
 * @param deckKeys the decks the course teaches, as written
 * @param instructorIds the accounts that teach it
 * @param note a remark on the course
 */
public record CourseInput(
        String key,
        Optional<String> title,
        String language,
        LocalDate from,
        LocalDate to,
        List<String> deckKeys,
        List<String> instructorIds,
        Optional<String> note) {

    public CourseInput {
        Objects.requireNonNull(title, "title");
        deckKeys = List.copyOf(deckKeys);
        instructorIds = List.copyOf(instructorIds);
        Objects.requireNonNull(note, "note");
    }

    /** A builder whose title and note start absent and whose decks and instructors start empty. */
    public static Builder builder(String key, String language, LocalDate from, LocalDate to) {
        return new Builder(key, language, from, to);
    }

    /** Collects the optional parts of a course. */
    public static final class Builder {

        private final String key;
        private final String language;
        private final LocalDate from;
        private final LocalDate to;
        private String title;
        private List<String> deckKeys = List.of();
        private List<String> instructorIds = List.of();
        private String note;

        private Builder(String key, String language, LocalDate from, LocalDate to) {
            this.key = Objects.requireNonNull(key, "key");
            this.language = Objects.requireNonNull(language, "language");
            this.from = Objects.requireNonNull(from, "from");
            this.to = Objects.requireNonNull(to, "to");
        }

        public Builder title(String title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        public Builder deckKeys(String... deckKeys) {
            return deckKeys(List.of(deckKeys));
        }

        public Builder deckKeys(List<String> deckKeys) {
            this.deckKeys = List.copyOf(deckKeys);
            return this;
        }

        public Builder instructorIds(String... instructorIds) {
            return instructorIds(List.of(instructorIds));
        }

        public Builder instructorIds(List<String> instructorIds) {
            this.instructorIds = List.copyOf(instructorIds);
            return this;
        }

        public Builder note(String note) {
            this.note = Objects.requireNonNull(note, "note");
            return this;
        }

        public CourseInput build() {
            return new CourseInput(
                    key,
                    Optional.ofNullable(title),
                    language,
                    from,
                    to,
                    deckKeys,
                    instructorIds,
                    Optional.ofNullable(note));
        }
    }
}
