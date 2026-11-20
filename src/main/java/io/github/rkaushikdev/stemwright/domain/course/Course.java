package io.github.rkaushikdev.stemwright.domain.course;

import io.github.rkaushikdev.stemwright.domain.IdKind;
import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.domain.time.DayRange;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A course is a term of teaching: a language, a window of dates, a set of decks and the people in
 * it. It is the layer where a department's decisions live, so that the drill layer underneath can
 * stay ignorant of terms and cohorts.
 *
 * <p>The term window is closed at both ends and it is load bearing. An assignment cannot fall
 * outside it, a shielded day outside it means nothing, and a report run after it ends is a
 * historical report rather than a live one.
 *
 * <p>Build one through {@link #of(CourseInput)}, which checks it; the canonical constructor only
 * copies what it is given.
 *
 * @param id the derived course id, see {@link #idFor}
 * @param key the course key, a slug
 * @param title the printed name, the key when none was given
 * @param language the language key, a slug
 * @param term the days the course runs, both ends included
 * @param deckKeys the decks the course teaches, in the order they were added, each once
 * @param instructorIds the accounts that teach it, at least one and each once
 * @param note a remark on the course
 */
public record Course(
        String id,
        String key,
        String title,
        String language,
        DayRange term,
        List<String> deckKeys,
        List<String> instructorIds,
        Optional<String> note) {

    private static final int TITLE_LIMIT = 160;
    private static final int NOTE_LIMIT = 800;

    public Course {
        Objects.requireNonNull(term, "term");
        deckKeys = List.copyOf(deckKeys);
        instructorIds = List.copyOf(instructorIds);
        Objects.requireNonNull(note, "note");
    }

    /** The id is derived from the language and the key rather than allocated. */
    public static String idFor(String language, String key) {
        return Identifiers.derivedId(IdKind.COURSE, language, key);
    }

    /**
     * Checks a course as written. A reversed term, a repeated deck, a repeated instructor and a
     * missing instructor are reported together; a key, language or deck key that is not a slug, and
     * an unusable title or note, fail on the spot.
     */
    public static Course of(CourseInput input) {
        ProblemCollector problems = new ProblemCollector();
        String key = Identifiers.asSlug("course.key", input.key());
        String language = Identifiers.asSlug("course.language", input.language());

        Optional<DayRange> term = problems.absorbFlat("term", () -> new DayRange(input.from(), input.to()));

        List<String> deckKeys = new ArrayList<>(input.deckKeys().size());
        for (int index = 0; index < input.deckKeys().size(); index += 1) {
            deckKeys.add(
                    Identifiers.asSlug("deckKeys." + index, input.deckKeys().get(index)));
        }
        problems.addIf(new HashSet<>(deckKeys).size() != deckKeys.size(), "deckKeys", "names the same deck twice");

        List<String> instructorIds = input.instructorIds();
        problems.addIf(
                new HashSet<>(instructorIds).size() != instructorIds.size(),
                "instructorIds",
                "names the same instructor twice");
        problems.addIf(instructorIds.isEmpty(), "instructorIds", "must name at least one instructor");

        problems.throwIfAny("course " + key + " is not usable");

        return new Course(
                idFor(language, key),
                key,
                input.title()
                        .map(title -> Normalise.requireText("course.title", title, TITLE_LIMIT))
                        .orElse(key),
                language,
                term.orElseThrow(),
                deckKeys,
                instructorIds,
                input.note().map(note -> Normalise.requireText("course.note", note, NOTE_LIMIT)));
    }

    /** Whether the day falls inside the term, either end included. */
    public boolean isRunning(LocalDate on) {
        return term.contains(on);
    }

    /** How many days the term holds, both ends counted. */
    public int termLength() {
        return term.length();
    }

    public boolean teaches(String deckKey) {
        return deckKeys.contains(deckKey);
    }

    public boolean isInstructor(String actorId) {
        return instructorIds.contains(actorId);
    }

    /**
     * This course with one more deck, checked again as a whole.
     *
     * @throws io.github.rkaushikdev.stemwright.domain.errors.ValidationException when the deck is
     *     already taught or its key is not a slug
     */
    public Course withDeck(String deckKey) {
        List<String> grown = new ArrayList<>(deckKeys);
        grown.add(deckKey);
        return withDeckKeys(grown);
    }

    /** This course without the deck, or an equal course when it never taught it. */
    public Course withoutDeck(String deckKey) {
        return withDeckKeys(deckKeys.stream()
                .filter(candidate -> !candidate.equals(deckKey))
                .toList());
    }

    /** One line for a listing, such as {@code Latin 1A runs 2024-10-07 to 2024-12-13 with 2 decks}. */
    public String describe() {
        String decks = deckKeys.isEmpty() ? "no decks yet" : deckKeys.size() + " decks";
        return title + " runs " + term.from() + " to " + term.to() + " with " + decks;
    }

    /** Rebuilds the course through {@link #of} so an edit is checked exactly as a new course is. */
    private Course withDeckKeys(List<String> nextDeckKeys) {
        CourseInput.Builder input = CourseInput.builder(key, language, term.from(), term.to())
                .title(title)
                .deckKeys(nextDeckKeys)
                .instructorIds(instructorIds);
        note.ifPresent(input::note);
        return of(input.build());
    }
}
