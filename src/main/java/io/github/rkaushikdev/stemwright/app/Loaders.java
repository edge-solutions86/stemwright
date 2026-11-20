package io.github.rkaushikdev.stemwright.app;

import io.github.rkaushikdev.stemwright.domain.course.Course;
import io.github.rkaushikdev.stemwright.domain.course.CourseScope;
import io.github.rkaushikdev.stemwright.domain.course.Roster;
import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DrillContext;
import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexicon;
import io.github.rkaushikdev.stemwright.domain.paradigm.ProfileBook;

/**
 * Loading. Five services had their own copy of "fetch it and throw a {@link NotFoundException} if
 * it is missing", and three of them named the missing thing differently, so the same failure
 * reached the http layer under three different messages.
 *
 * <p>Every loader here throws rather than returning an empty {@code Optional}. A service that wants
 * the absence has the store underneath it and can ask directly.
 */
public final class Loaders {

    private Loaders() {}

    /**
     * The language's bundle.
     *
     * @throws NotFoundException of kind {@code language}, naming the language, when none is stored
     */
    public static LanguageBundle requireLanguage(Stores stores, String language) {
        return stores.languages().get(language).orElseThrow(() -> new NotFoundException("language", language));
    }

    /**
     * The language's profile book.
     *
     * @throws NotFoundException of kind {@code profile book}, naming the language, when none is
     *     stored
     */
    public static ProfileBook requireBook(Stores stores, String language) {
        return stores.books().get(language).orElseThrow(() -> new NotFoundException("profile book", language));
    }

    /**
     * The deck with the key in the language.
     *
     * @throws NotFoundException of kind {@code deck}, naming {@code language/key}, when none is
     *     stored
     */
    public static Deck requireDeck(Stores stores, String language, String key) {
        return stores.decks().get(language, key).orElseThrow(() -> new NotFoundException("deck", language + "/" + key));
    }

    /**
     * The course with the key.
     *
     * @throws NotFoundException of kind {@code course}, naming the key, when none is stored
     */
    public static Course requireCourse(Stores stores, String key) {
        return stores.courses().get(key).orElseThrow(() -> new NotFoundException("course", key));
    }

    /**
     * The course with the key and the roster of its stored enrolments, which is what a permission
     * on the course is decided against.
     *
     * @throws NotFoundException of kind {@code course} when the course is not stored
     */
    public static CourseScope requireScope(Stores stores, String courseKey) {
        Course course = requireCourse(stores, courseKey);
        return new CourseScope(course, Roster.of(course, stores.courses().enrolments(courseKey)));
    }

    /**
     * Everything a drill over the language needs: its schema and classes, its profile book, and a
     * lexicon of every lexeme stored for it.
     *
     * @throws NotFoundException for the language when it is not configured, and otherwise for its
     *     profile book when it has none
     */
    public static DrillContext loadContext(Stores stores, String language) {
        LanguageBundle bundle = requireLanguage(stores, language);
        ProfileBook book = requireBook(stores, language);
        return new DrillContext(
                bundle.schema(),
                bundle.classes(),
                book,
                Lexicon.build(language, stores.lexemes().listByLanguage(language)));
    }
}
