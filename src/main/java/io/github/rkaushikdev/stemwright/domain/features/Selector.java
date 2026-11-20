package io.github.rkaushikdev.stemwright.domain.features;

import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A region of the paradigm rather than a single cell. Deck membership, assignment scope and coverage
 * reports are all stated with these, because "the plural of every case" is what a syllabus actually
 * says.
 *
 * <p>The written form is comma separated clauses, such as {@code case=nom|gen,number=pl} or {@code
 * number=pl,gender!=neut}. A dimension the selector says nothing about is unconstrained, so an empty
 * selector matches everything. That is deliberate: a deck with no scope is the whole paradigm, not
 * an empty one.
 *
 * @param clauses the clauses, in schema order, each with its values sorted
 */
public record Selector(List<SelectorClause> clauses) {

    /** The selector that constrains nothing and so matches every bundle. */
    public static final Selector EMPTY = new Selector(List.of());

    public Selector {
        clauses = List.copyOf(clauses);
    }

    /**
     * Checks the clauses against the schema, refusing an unknown dimension or value and a dimension
     * constrained twice in the same direction, then puts them in schema order with sorted values.
     */
    public static Selector of(FeatureSchema schema, Collection<SelectorClause> clauses) {
        ProblemCollector problems = new ProblemCollector();
        Set<String> seen = new HashSet<>();
        for (SelectorClause clause : clauses) {
            String key = directionKey(clause);
            if (schema.dimensionOf(clause.dimension()).isEmpty()) {
                problems.add(clause.dimension(), "is not a dimension of " + schema.language());
                continue;
            }
            if (!seen.add(key)) {
                problems.add(clause.dimension(), "is constrained twice in the same direction");
            }
            if (clause.values().isEmpty()) {
                problems.add(clause.dimension(), "must name at least one value");
            }
            for (String value : clause.values()) {
                if (!schema.hasValue(clause.dimension(), value)) {
                    problems.add(clause.dimension(), "does not admit the value " + value);
                }
            }
        }
        problems.throwIfAny("selector is not valid for " + schema.language());

        List<SelectorClause> sorted = new ArrayList<>(clauses);
        sorted.sort(Comparator.comparingInt(clause -> schema.dimensionIndex(clause.dimension())));
        return new Selector(sorted.stream()
                .map(clause -> new SelectorClause(
                        clause.dimension(), clause.values().stream().sorted().toList(), clause.negated()))
                .toList());
    }

    private static String directionKey(SelectorClause clause) {
        return clause.dimension() + (clause.negated() ? "!" : "");
    }

    /** {@link #parse(FeatureSchema, String, String)} reporting under {@code selector}. */
    public static Selector parse(FeatureSchema schema, String text) {
        return parse(schema, text, "selector");
    }

    /** Reads the written form, case insensitively. An empty string is {@link #EMPTY}. */
    public static Selector parse(FeatureSchema schema, String text, String path) {
        String cleaned = Text.lower(Normalise.normaliseInput(text));
        if (cleaned.isEmpty()) {
            return EMPTY;
        }
        List<SelectorClause> clauses = new ArrayList<>();
        for (String part : Text.split(cleaned, ",")) {
            String trimmed = Text.trim(part);
            if (trimmed.isEmpty()) {
                throw ValidationException.field(path, "has an empty clause: " + text);
            }
            boolean negated = trimmed.contains("!=");
            String separator = negated ? "!=" : "=";
            int at = trimmed.indexOf(separator);
            if (at <= 0) {
                throw ValidationException.field(path, "is not a dimension and values: " + trimmed);
            }
            String dimension = Text.trim(trimmed.substring(0, at));
            List<String> values = Text.split(trimmed.substring(at + separator.length()), "|").stream()
                    .map(Text::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
            clauses.add(new SelectorClause(dimension, values, negated));
        }
        return of(schema, clauses);
    }

    /** The written form, which {@link #parse} reads back to an equal selector. */
    public String format() {
        return clauses.stream()
                .map(clause -> clause.dimension() + (clause.negated() ? "!=" : "=") + String.join("|", clause.values()))
                .collect(Collectors.joining(","));
    }

    /**
     * True when the bundle satisfies every clause. A clause a bundle says nothing about does not
     * match: a selector asking for {@code number=pl} is asking for cells that are plural, and a cell
     * with no number at all is not one of them.
     */
    public boolean matches(FeatureBundle bundle) {
        for (SelectorClause clause : clauses) {
            Optional<String> value = bundle.valueOf(clause.dimension());
            if (value.isEmpty()) {
                return false;
            }
            boolean listed = clause.values().contains(value.get());
            if (listed == clause.negated()) {
                return false;
            }
        }
        return true;
    }

    public List<FeatureBundle> select(Collection<FeatureBundle> bundles) {
        return bundles.stream().filter(this::matches).toList();
    }

    /** How many complete bundles of the schema the selector admits. */
    public int cardinality(FeatureSchema schema) {
        int total = 1;
        for (FeatureDimension dimension : schema.dimensions()) {
            Optional<SelectorClause> clause = clauses.stream()
                    .filter(candidate -> candidate.dimension().equals(dimension.key()))
                    .findFirst();
            if (clause.isEmpty()) {
                total *= dimension.values().size();
                continue;
            }
            SelectorClause found = clause.get();
            long allowed = dimension.values().stream()
                    .filter(value -> found.negated() != found.values().contains(value.key()))
                    .count();
            total *= (int) allowed;
        }
        return total;
    }

    public boolean isEmptySelection(FeatureSchema schema) {
        return cardinality(schema) == 0;
    }

    /** Every complete bundle the selector admits, in table order. */
    public List<FeatureBundle> expand(FeatureSchema schema) {
        return select(FeatureBundle.enumerate(schema));
    }

    /**
     * The conjunction of two selectors. Used when an assignment narrows a deck: the result is what
     * both admit, and it can be empty, which callers must check.
     */
    public Selector intersect(FeatureSchema schema, Selector other) {
        Map<String, SelectorClause> byKey = new LinkedHashMap<>();
        List<SelectorClause> all = new ArrayList<>(clauses);
        all.addAll(other.clauses);
        for (SelectorClause clause : all) {
            String key = directionKey(clause);
            SelectorClause existing = byKey.get(key);
            if (existing == null) {
                byKey.put(key, clause);
                continue;
            }
            List<String> merged;
            if (clause.negated()) {
                Set<String> union = new LinkedHashSet<>(existing.values());
                union.addAll(clause.values());
                merged = List.copyOf(union);
            } else {
                merged = existing.values().stream()
                        .filter(clause.values()::contains)
                        .toList();
            }
            byKey.put(key, new SelectorClause(clause.dimension(), merged, clause.negated()));
        }

        List<SelectorClause> merged = List.copyOf(byKey.values());
        boolean empty = merged.stream()
                .anyMatch(clause -> !clause.negated() && clause.values().isEmpty());
        if (empty) {
            String dimension = merged.stream()
                    .filter(clause -> clause.values().isEmpty())
                    .findFirst()
                    .orElseThrow()
                    .dimension();
            return of(schema, List.of(new SelectorClause(dimension, allValuesOf(schema, dimension), true)));
        }
        return of(schema, merged);
    }

    private static List<String> allValuesOf(FeatureSchema schema, String dimensionKey) {
        return schema.dimensionOf(dimensionKey)
                .map(dimension ->
                        dimension.values().stream().map(FeatureValue::key).toList())
                .orElse(List.of());
    }

    /** Reads the selector out in words: {@code case of gen or nom, gender other than neut}. */
    public String describe(FeatureSchema schema) {
        if (clauses.isEmpty()) {
            return "every cell in " + schema.language();
        }
        return clauses.stream()
                .map(clause -> {
                    String joined = String.join(" or ", clause.values());
                    return clause.negated()
                            ? clause.dimension() + " other than " + joined
                            : clause.dimension() + " of " + joined;
                })
                .collect(Collectors.joining(", "));
    }
}
