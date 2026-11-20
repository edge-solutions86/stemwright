package io.github.rkaushikdev.stemwright.app;

import java.util.Objects;

/**
 * The ports the services need and the infrastructure fills, one store per kind of record.
 *
 * <p>Nothing here mentions http, a file system or a journal. That is the point: the services are
 * testable against a map and the persistence layer is replaceable without touching them.
 *
 * <p>The stores are synchronous. A single call is expected to be atomic; several calls made by one
 * service operation are not atomic together, which is why the services order their writes so that
 * an interrupted operation leaves something the next one can correct.
 *
 * @param languages the configured languages
 * @param lexemes the lexemes of every language
 * @param books the profile book of each language
 * @param decks the decks of every language
 * @param ledgers the mastery ledger of each learner
 * @param attempts the attempt log
 * @param sessions the drill sessions
 * @param courses the courses with their enrolments and assignments
 */
public record Stores(
        LanguageStore languages,
        LexemeStore lexemes,
        BookStore books,
        DeckStore decks,
        LedgerStore ledgers,
        AttemptStore attempts,
        SessionStore sessions,
        CourseStore courses) {

    public Stores {
        Objects.requireNonNull(languages, "languages");
        Objects.requireNonNull(lexemes, "lexemes");
        Objects.requireNonNull(books, "books");
        Objects.requireNonNull(decks, "decks");
        Objects.requireNonNull(ledgers, "ledgers");
        Objects.requireNonNull(attempts, "attempts");
        Objects.requireNonNull(sessions, "sessions");
        Objects.requireNonNull(courses, "courses");
    }
}
