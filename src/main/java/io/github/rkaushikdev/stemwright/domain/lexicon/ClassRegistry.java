package io.github.rkaushikdev.stemwright.domain.lexicon;

import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Collation;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The inflection classes of one language, checked as a tree.
 *
 * <p>The tree is load bearing rather than decorative. A paradigm profile is attached to a class, and
 * a lexeme with no profile of its own inherits the nearest ancestor that has one, so moving a
 * profile up the tree silently changes what a hundred lexemes are graded against. Every lookup here
 * is therefore explicit about which class it resolved to.
 *
 * @param language the language key
 * @param classes every class by its key, in declaration order
 */
public record ClassRegistry(String language, Map<String, InflectionClass> classes) {

    private static final int LABEL_LIMIT = 96;
    private static final int NOTE_LIMIT = 400;

    private static final Comparator<InflectionClass> KEY_ORDER =
            Comparator.comparing(InflectionClass::key, Collation.ORDER);

    public ClassRegistry {
        classes = Collections.unmodifiableMap(new LinkedHashMap<>(classes));
    }

    /**
     * Checks the declarations and links them into a tree, reporting every duplicate key, unknown
     * part of speech, unknown parent, part of speech that disagrees with its parent and cycle at
     * once. A label, parent key or note that is not usable text fails on the spot.
     */
    public static ClassRegistry of(String language, List<InflectionClassInput> inputs) {
        ProblemCollector problems = new ProblemCollector();
        Map<String, InflectionClass> classes = new LinkedHashMap<>();

        for (InflectionClassInput input : inputs) {
            String key = Identifiers.asSlug("class", input.key());
            String path = "classes." + key;
            if (classes.containsKey(key)) {
                problems.add(path, "is declared twice");
                continue;
            }
            Optional<PartOfSpeech> partOfSpeech = PartOfSpeech.fromKey(input.partOfSpeech());
            if (partOfSpeech.isEmpty()) {
                problems.add(path + ".partOfSpeech", "is not a part of speech: " + input.partOfSpeech());
                continue;
            }
            classes.put(
                    key,
                    new InflectionClass(
                            key,
                            input.label()
                                    .map(label -> Normalise.requireText(path + ".label", label, LABEL_LIMIT))
                                    .orElse(key),
                            partOfSpeech.get(),
                            input.parent().map(parent -> Identifiers.asSlug(path + ".parent", parent)),
                            input.note().map(note -> Normalise.requireText(path + ".note", note, NOTE_LIMIT))));
        }

        for (InflectionClass declared : classes.values()) {
            if (declared.parent().isEmpty()) {
                continue;
            }
            String parentKey = declared.parent().get();
            InflectionClass parent = classes.get(parentKey);
            if (parent == null) {
                problems.add("classes." + declared.key() + ".parent", "names an unknown class: " + parentKey);
                continue;
            }
            if (parent.partOfSpeech() != declared.partOfSpeech()) {
                problems.add(
                        "classes." + declared.key() + ".partOfSpeech",
                        "is " + declared.partOfSpeech().key() + " but its parent " + parent.key() + " is "
                                + parent.partOfSpeech().key());
            }
        }

        for (InflectionClass declared : classes.values()) {
            if (hasCycle(classes, declared.key())) {
                problems.add("classes." + declared.key() + ".parent", "takes part in a cycle");
            }
        }

        problems.throwIfAny("inflection classes for " + language + " are not usable");
        return new ClassRegistry(Identifiers.asSlug("language", language), classes);
    }

    private static boolean hasCycle(Map<String, InflectionClass> classes, String start) {
        Set<String> seen = new HashSet<>(Set.of(start));
        Optional<String> current = parentOf(classes, start);
        while (current.isPresent()) {
            if (!seen.add(current.get())) {
                return true;
            }
            current = parentOf(classes, current.get());
        }
        return false;
    }

    private static Optional<String> parentOf(Map<String, InflectionClass> classes, String key) {
        return Optional.ofNullable(classes.get(key)).flatMap(InflectionClass::parent);
    }

    public Optional<InflectionClass> classOf(String key) {
        return Optional.ofNullable(classes.get(key));
    }

    public InflectionClass requireClass(String key) {
        return classOf(key)
                .orElseThrow(() -> ValidationException.field("class", "is not a class of " + language + ": " + key));
    }

    /** The class itself first, then each ancestor up to the root; empty for a key the registry lacks. */
    public List<InflectionClass> chain(String key) {
        List<InflectionClass> chain = new ArrayList<>();
        Optional<InflectionClass> current = classOf(key);
        while (current.isPresent()) {
            chain.add(current.get());
            current = current.get().parent().flatMap(this::classOf);
        }
        return chain;
    }

    /** True when the ancestor sits above the class in the tree. A class is not its own descendant. */
    public boolean isDescendantOf(String key, String ancestor) {
        return chain(key).stream().skip(1).anyMatch(candidate -> candidate.key().equals(ancestor));
    }

    /** The classes whose parent is the given one, in key order. */
    public List<InflectionClass> childrenOf(String key) {
        return sortedWhere(candidate -> candidate.parent().filter(key::equals).isPresent());
    }

    /** The classes with no parent, in key order. */
    public List<InflectionClass> roots() {
        return sortedWhere(candidate -> candidate.parent().isEmpty());
    }

    /** The classes of one part of speech, in key order. */
    public List<InflectionClass> classesFor(PartOfSpeech partOfSpeech) {
        return sortedWhere(candidate -> candidate.partOfSpeech() == partOfSpeech);
    }

    private List<InflectionClass> sortedWhere(Predicate<InflectionClass> keep) {
        return classes.values().stream().filter(keep).sorted(KEY_ORDER).toList();
    }

    /**
     * Walks up from a class and returns the first ancestor for which the caller has something. This
     * is how a profile, an orthography override or a note is resolved, and the class it resolved
     * through comes back with it so a report can say where the answer came from.
     *
     * @param lookup what the caller holds for a class key, empty when it holds nothing
     * @return the first value found and the class that supplied it, or empty when no class in the
     *     chain has one
     */
    public <T> Optional<Resolved<T>> resolveThroughChain(String key, Function<? super String, Optional<T>> lookup) {
        for (InflectionClass candidate : chain(key)) {
            Optional<T> value = lookup.apply(candidate.key());
            if (value.isPresent()) {
                return Optional.of(new Resolved<>(value.get(), candidate.key()));
            }
        }
        return Optional.empty();
    }

    /**
     * A value found by walking up the class tree, with the class that supplied it.
     *
     * @param value what the lookup returned
     * @param through the key of the class the value was found on, the start class or an ancestor
     */
    public record Resolved<T>(T value, String through) {}
}
