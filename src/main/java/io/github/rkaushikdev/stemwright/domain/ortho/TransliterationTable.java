package io.github.rkaushikdev.stemwright.domain.ortho;

import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Collation;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Typing a language you do not have a keyboard for.
 *
 * <p>A learner on a laptop with no Greek layout types {@code anthrwpos} and means {@code ανθρωπος},
 * and refusing that is refusing most of the people the desk is for. So a language may declare a
 * transliteration table, and an answer is tried both as typed and as transliterated.
 *
 * <p>The table is longest match first, which is the only thing that makes a multi letter mapping
 * work: {@code th} has to be tried before {@code t}, and a table applied in declaration order would
 * turn {@code theos} into something with a stray {@code h} in it.
 *
 * @param language the language key
 * @param rules the rules, longest source first and in collation order within one length
 */
public record TransliterationTable(String language, List<TransliterationRule> rules) {

    /** The table of a language that declares none. Nothing transliterates under it. */
    public static final TransliterationTable NONE = new TransliterationTable("none", List.of());

    /** The Greek table a first year course needs, in the beta code most editions use. */
    public static final TransliterationTable GREEK_BETA_CODE = of(
            "greek",
            List.of(
                    new TransliterationRule("th", "θ"),
                    new TransliterationRule("ph", "φ"),
                    new TransliterationRule("ch", "χ"),
                    new TransliterationRule("ps", "ψ"),
                    new TransliterationRule("ks", "ξ"),
                    new TransliterationRule("ou", "ου"),
                    new TransliterationRule("a", "α"),
                    new TransliterationRule("b", "β"),
                    new TransliterationRule("g", "γ"),
                    new TransliterationRule("d", "δ"),
                    new TransliterationRule("e", "ε"),
                    new TransliterationRule("z", "ζ"),
                    new TransliterationRule("h", "η"),
                    new TransliterationRule("i", "ι"),
                    new TransliterationRule("k", "κ"),
                    new TransliterationRule("l", "λ"),
                    new TransliterationRule("m", "μ"),
                    new TransliterationRule("n", "ν"),
                    new TransliterationRule("o", "ο"),
                    new TransliterationRule("p", "π"),
                    new TransliterationRule("r", "ρ"),
                    new TransliterationRule("s", "σ"),
                    new TransliterationRule("t", "τ"),
                    new TransliterationRule("u", "υ"),
                    new TransliterationRule("w", "ω"),
                    new TransliterationRule("x", "χ")));

    public TransliterationTable {
        rules = List.copyOf(rules);
    }

    /**
     * Checks a table as written, reporting every blank or repeated source at once, then lower cases
     * and normalises each source, normalises each target and sorts longest source first.
     */
    public static TransliterationTable of(String language, List<TransliterationRule> rules) {
        ProblemCollector problems = new ProblemCollector();
        Set<String> seen = new HashSet<>();

        for (int index = 0; index < rules.size(); index += 1) {
            String from = sourceOf(rules.get(index));
            String path = "rules." + index + ".from";
            if (from.isEmpty()) {
                problems.add(path, "must not be blank");
                continue;
            }
            if (!seen.add(from)) {
                problems.add(path, "is mapped twice: " + from);
            }
        }

        problems.throwIfAny("transliteration for " + language + " is not usable");

        List<TransliterationRule> sorted = new ArrayList<>(rules.size());
        for (TransliterationRule rule : rules) {
            sorted.add(new TransliterationRule(sourceOf(rule), Normalise.normaliseInput(rule.to())));
        }
        sorted.sort(Comparator.comparingInt(
                        (TransliterationRule rule) -> rule.from().length())
                .reversed()
                .thenComparing(TransliterationRule::from, Collation.ORDER));

        return new TransliterationTable(Identifiers.asSlug("language", language), sorted);
    }

    private static String sourceOf(TransliterationRule rule) {
        return Text.lower(Normalise.normaliseInput(rule.from()));
    }

    /**
     * Rewrites the text by the table, taking the longest rule that matches at each position and
     * keeping anything no rule matches as it is. The text is normalised and lower cased first.
     */
    public String transliterate(String text) {
        String source = Text.lower(Normalise.normaliseInput(text));
        StringBuilder out = new StringBuilder(source.length());
        int index = 0;

        while (index < source.length()) {
            Optional<TransliterationRule> rule = ruleAt(source, index);
            if (rule.isEmpty()) {
                out.append(source.charAt(index));
                index += 1;
                continue;
            }
            out.append(rule.get().to());
            index += rule.get().from().length();
        }
        return out.toString();
    }

    private Optional<TransliterationRule> ruleAt(String source, int index) {
        return rules.stream()
                .filter(candidate -> source.startsWith(candidate.from(), index))
                .findFirst();
    }

    /**
     * True when the typed text becomes the expected form once transliterated, ignoring the expected
     * form's accents and case. Always false under a table with no rules.
     */
    public boolean matchesTransliterated(String expected, String given) {
        if (rules.isEmpty()) {
            return false;
        }
        String target = Text.lower(Normalise.stripMarks(Normalise.normaliseInput(expected)));
        return transliterate(given).equals(target);
    }

    /** True when the text is not blank and every cluster of it is the source of a rule on its own. */
    public boolean isTransliterable(String text) {
        String source = Text.lower(Normalise.normaliseInput(text));
        return !source.isEmpty()
                && Normalise.markClusters(source).stream().allMatch(cluster -> rules.stream()
                        .anyMatch(rule -> rule.from().equals(Text.lower(cluster))));
    }

    /**
     * The rule whose source is exactly the given text.
     *
     * @throws ValidationException on the {@code from} field when the table has no such rule
     */
    public TransliterationRule requireRule(String from) {
        return rules.stream()
                .filter(candidate -> candidate.from().equals(from))
                .findFirst()
                .orElseThrow(() -> ValidationException.field("from", "is not in the " + language + " table: " + from));
    }
}
