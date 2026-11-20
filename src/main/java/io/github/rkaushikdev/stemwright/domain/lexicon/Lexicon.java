package io.github.rkaushikdev.stemwright.domain.lexicon;

import io.github.rkaushikdev.stemwright.domain.Grouping;
import io.github.rkaushikdev.stemwright.domain.errors.ConflictException;
import io.github.rkaushikdev.stemwright.domain.errors.NotFoundException;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * An immutable collection of lexemes with the indexes the drill layer needs. Rebuilt rather than
 * mutated: a session holds one for the length of a request and the store hands out a fresh one
 * after a write, which keeps a queue from changing under a learner mid session.
 *
 * <p>The indexes are derived from one list of entries, so the only way to make a lexicon is {@link
 * #build}, and every change returns a new one.
 */
public final class Lexicon {

    private final String language;
    private final Map<String, Lexeme> byId;
    private final Map<String, List<Lexeme>> byLemma;
    private final Map<String, List<Lexeme>> byClass;
    private final Map<String, List<Lexeme>> byTag;

    private Lexicon(
            String language,
            Map<String, Lexeme> byId,
            Map<String, List<Lexeme>> byLemma,
            Map<String, List<Lexeme>> byClass,
            Map<String, List<Lexeme>> byTag) {
        this.language = language;
        this.byId = Collections.unmodifiableMap(byId);
        this.byLemma = frozen(byLemma);
        this.byClass = frozen(byClass);
        this.byTag = frozen(byTag);
    }

    private static Map<String, List<Lexeme>> frozen(Map<String, List<Lexeme>> groups) {
        Map<String, List<Lexeme>> copy = new LinkedHashMap<>();
        groups.forEach((key, bucket) -> copy.put(key, List.copyOf(bucket)));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Indexes the entries by id, by lower cased lemma, by class and by tag, each bucket in {@link
     * Lexeme#ORDER}.
     *
     * @throws ConflictException when two entries share an id
     */
    public static Lexicon build(String language, Collection<Lexeme> entries) {
        Map<String, Lexeme> byId = new LinkedHashMap<>();
        for (Lexeme entry : entries) {
            if (byId.containsKey(entry.id())) {
                throw new ConflictException("lexeme " + entry.id() + " appears twice in the lexicon");
            }
            byId.put(entry.id(), entry);
        }

        Map<String, List<Lexeme>> byTag = new LinkedHashMap<>();
        for (Lexeme entry : entries) {
            for (String tag : entry.tags()) {
                byTag.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(entry);
            }
        }
        byTag.values().forEach(bucket -> bucket.sort(Lexeme.ORDER));

        return new Lexicon(
                language,
                byId,
                Grouping.groupSorted(entries, entry -> Text.lower(entry.lemma()), Lexeme.ORDER),
                Grouping.groupSorted(entries, Lexeme::classKey, Lexeme.ORDER),
                byTag);
    }

    public static Lexicon empty(String language) {
        return build(language, List.of());
    }

    public String language() {
        return language;
    }

    /** Every entry by id, in the order the entries were given. */
    public Map<String, Lexeme> byId() {
        return byId;
    }

    /** The entries by lower cased lemma, each bucket in homograph order. */
    public Map<String, List<Lexeme>> byLemma() {
        return byLemma;
    }

    /** The entries by class key, each bucket in {@link Lexeme#ORDER}. */
    public Map<String, List<Lexeme>> byClass() {
        return byClass;
    }

    /** The entries by tag, each bucket in {@link Lexeme#ORDER}. */
    public Map<String, List<Lexeme>> byTag() {
        return byTag;
    }

    public int size() {
        return byId.size();
    }

    /** Every entry, in {@link Lexeme#ORDER}. */
    public List<Lexeme> allLexemes() {
        return byId.values().stream().sorted(Lexeme.ORDER).toList();
    }

    public Optional<Lexeme> lexemeById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * The entry with the id.
     *
     * @throws NotFoundException when the lexicon holds no such id
     */
    public Lexeme requireLexeme(String id) {
        return lexemeById(id).orElseThrow(() -> new NotFoundException("lexeme", id));
    }

    /** Every homograph of a lemma, in homograph order, whatever the case of the lemma asked for. */
    public List<Lexeme> lexemesByLemma(String lemma) {
        return byLemma.getOrDefault(Text.lower(lemma), List.of());
    }

    /** The homograph index a new lexeme with this lemma would take: one past the highest held. */
    public int nextHomograph(String lemma) {
        return lexemesByLemma(lemma).stream().mapToInt(Lexeme::homograph).max().orElse(0) + 1;
    }

    /** The lexemes of exactly this class, not of the classes beneath it. */
    public List<Lexeme> lexemesByClass(String classKey) {
        return byClass.getOrDefault(classKey, List.of());
    }

    /**
     * Every lexeme of a class or of any class beneath it, in {@link Lexeme#ORDER}. This is the
     * lookup a deck uses, because a deck scoped to the second declension means the neuters and the
     * r stems too.
     */
    public List<Lexeme> lexemesUnderClass(ClassRegistry classes, String classKey) {
        return allLexemes().stream()
                .filter(entry ->
                        entry.classKey().equals(classKey) || classes.isDescendantOf(entry.classKey(), classKey))
                .toList();
    }

    public List<Lexeme> lexemesByTag(String tag) {
        return byTag.getOrDefault(tag, List.of());
    }

    /** Every tag in use, in natural string order. */
    public List<String> tags() {
        return byTag.keySet().stream().sorted().toList();
    }

    /**
     * A new lexicon with the entry added, leaving this one alone.
     *
     * @throws ConflictException when the lexicon already holds the entry's id
     */
    public Lexicon add(Lexeme entry) {
        if (byId.containsKey(entry.id())) {
            throw new ConflictException(
                    "lexeme " + entry.lemma() + " with homograph " + entry.homograph() + " is already in the lexicon");
        }
        return build(
                language,
                Stream.concat(byId.values().stream(), Stream.of(entry)).toList());
    }

    /**
     * A new lexicon with the entry put in place of the one with the same id, leaving this one alone.
     *
     * @throws NotFoundException when the lexicon holds no entry with that id
     */
    public Lexicon replace(Lexeme entry) {
        if (!byId.containsKey(entry.id())) {
            throw new NotFoundException("lexeme", entry.id());
        }
        return build(
                language,
                Stream.concat(entriesOtherThan(entry.id()), Stream.of(entry)).toList());
    }

    /**
     * A new lexicon without the entry, leaving this one alone.
     *
     * @throws NotFoundException when the lexicon holds no such id
     */
    public Lexicon remove(String id) {
        if (!byId.containsKey(id)) {
            throw new NotFoundException("lexeme", id);
        }
        return build(language, entriesOtherThan(id).toList());
    }

    private Stream<Lexeme> entriesOtherThan(String id) {
        return byId.values().stream().filter(candidate -> !candidate.id().equals(id));
    }

    /**
     * Counts the entries by the part of speech of their class, and the entries missing a tag or a
     * gloss. An entry whose class the registry lacks counts as {@code unknown}.
     */
    public LexiconStats stats(ClassRegistry classes) {
        Map<String, Integer> byPartOfSpeech = new LinkedHashMap<>();
        int untagged = 0;
        int withoutGloss = 0;

        for (Lexeme entry : byId.values()) {
            String part = classes.classOf(entry.classKey())
                    .map(found -> found.partOfSpeech().key())
                    .orElse("unknown");
            byPartOfSpeech.merge(part, 1, Integer::sum);
            if (entry.tags().isEmpty()) {
                untagged += 1;
            }
            if (entry.glosses().isEmpty()) {
                withoutGloss += 1;
            }
        }

        return new LexiconStats(byId.size(), byPartOfSpeech, byClass.size(), untagged, withoutGloss);
    }
}
