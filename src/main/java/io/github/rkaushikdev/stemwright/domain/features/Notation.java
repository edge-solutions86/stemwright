package io.github.rkaushikdev.stemwright.domain.features;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The short notation a teacher writes on a board: {@code gen.pl}, {@code aor.subj.mid.2sg}.
 *
 * <p>It is what a deck definition, a cli argument and an import file all use, so it has to round
 * trip and it has to refuse anything ambiguous rather than guess. A token is looked up across every
 * dimension. If exactly one dimension admits it the token is unqualified and fine. If two do, the
 * writer has to say which, as {@code case:nom}. This is not a nicety: Greek {@code pres} is a tense
 * and {@code pass} is a voice, but {@code perf} is a tense in one schema and an aspect in another,
 * and a silent pick would put the cell in the wrong row of the table.
 */
public final class Notation {

    /** How a bundle's values are printed. */
    public enum BundleStyle {
        /** The table header form, joined by dots: {@code gen.pl}. */
        ABBREVIATION,
        /** The value keys, joined by dots: {@code gen.pl}. */
        KEY,
        /** The labels, joined by spaces: {@code Gen Pl}. */
        LABEL
    }

    private Notation() {}

    /** {@link #formatBundle(FeatureSchema, FeatureBundle, BundleStyle)} in abbreviations. */
    public static String formatBundle(FeatureSchema schema, FeatureBundle bundle) {
        return formatBundle(schema, bundle, BundleStyle.ABBREVIATION);
    }

    public static String formatBundle(FeatureSchema schema, FeatureBundle bundle, BundleStyle style) {
        List<String> parts = new ArrayList<>();
        for (FeaturePair pair : bundle.pairs()) {
            FeatureValue value = schema.dimensionOf(pair.dimension())
                    .flatMap(dimension -> dimension.value(pair.value()))
                    .orElseThrow(() -> ValidationException.field(
                            "bundle", "holds " + pair.dimension() + "=" + pair.value() + " which the schema lost"));
            parts.add(
                    switch (style) {
                        case ABBREVIATION -> value.abbreviation();
                        case KEY -> value.key();
                        case LABEL -> value.label();
                    });
        }
        return String.join(style == BundleStyle.LABEL ? " " : ".", parts);
    }

    private record TokenMatch(String dimension, String value) {}

    private static List<TokenMatch> matchesForToken(FeatureSchema schema, String token) {
        List<TokenMatch> matches = new ArrayList<>();
        for (FeatureDimension dimension : schema.dimensions()) {
            for (FeatureValue value : dimension.values()) {
                if (value.key().equals(token) || value.abbreviation().equals(token)) {
                    matches.add(new TokenMatch(dimension.key(), value.key()));
                }
            }
        }
        return matches;
    }

    private static TokenMatch resolveQualified(FeatureSchema schema, String token, String path) {
        int colon = token.indexOf(':');
        String dimensionKey = token.substring(0, colon);
        String valueToken = token.substring(colon + 1);
        FeatureDimension dimension = schema.dimensionOf(dimensionKey)
                .orElseThrow(() ->
                        ValidationException.field(path, "names a dimension the schema does not have: " + dimensionKey));
        Optional<FeatureValue> value = dimension.values().stream()
                .filter(candidate -> candidate.key().equals(valueToken)
                        || candidate.abbreviation().equals(valueToken))
                .findFirst();
        if (value.isEmpty()) {
            throw ValidationException.field(path, dimensionKey + " does not admit " + valueToken);
        }
        return new TokenMatch(dimension.key(), value.get().key());
    }

    private static TokenMatch resolveBare(FeatureSchema schema, String token, String path) {
        List<TokenMatch> matches = matchesForToken(schema, token);
        if (matches.isEmpty()) {
            throw ValidationException.field(path, "is not a feature value in " + schema.language() + ": " + token);
        }
        if (matches.size() > 1) {
            String named = matches.stream().map(TokenMatch::dimension).collect(Collectors.joining(", "));
            throw ValidationException.field(
                    path, "is ambiguous between " + named + ", write it as one of them followed by a colon: " + token);
        }
        return matches.get(0);
    }

    /** {@link #parseBundleNotation(FeatureSchema, String, String)} reporting under {@code bundle}. */
    public static FeatureBundle parseBundleNotation(FeatureSchema schema, String notation) {
        return parseBundleNotation(schema, notation, "bundle");
    }

    /** Reads a dotted notation, case insensitively, refusing an ambiguous bare token. */
    public static FeatureBundle parseBundleNotation(FeatureSchema schema, String notation, String path) {
        String cleaned = Text.lower(Normalise.normaliseInput(notation));
        if (cleaned.isEmpty()) {
            return FeatureBundle.of(schema, Map.of());
        }

        Map<String, String> input = new LinkedHashMap<>();
        for (String token : Text.split(cleaned, ".")) {
            if (token.isEmpty()) {
                throw ValidationException.field(path, "has an empty token: " + notation);
            }
            TokenMatch match =
                    token.contains(":") ? resolveQualified(schema, token, path) : resolveBare(schema, token, path);
            if (input.containsKey(match.dimension())) {
                throw ValidationException.field(path, "fixes " + match.dimension() + " twice");
            }
            input.put(match.dimension(), match.value());
        }
        return FeatureBundle.of(schema, input);
    }

    /** True when the schema can express a token in more than one dimension. */
    public static boolean isAmbiguousToken(FeatureSchema schema, String token) {
        return matchesForToken(schema, token).size() > 1;
    }

    /** Every token, key or abbreviation, that more than one value admits, sorted. */
    public static List<String> ambiguousTokens(FeatureSchema schema) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (FeatureDimension dimension : schema.dimensions()) {
            for (FeatureValue value : dimension.values()) {
                for (String token : new LinkedHashSet<>(List.of(value.key(), value.abbreviation()))) {
                    counts.merge(token, 1, Integer::sum);
                }
            }
        }
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /**
     * Prints a notation that will parse back to the same bundle even when some of its tokens are
     * ambiguous, by qualifying exactly those tokens and no others.
     */
    public static String formatUnambiguous(FeatureSchema schema, FeatureBundle bundle) {
        Set<String> ambiguous = Set.copyOf(ambiguousTokens(schema));
        List<String> parts = new ArrayList<>();
        for (FeaturePair pair : bundle.pairs()) {
            FeatureValue value = schema.dimensionOf(pair.dimension())
                    .flatMap(dimension -> dimension.value(pair.value()))
                    .orElseThrow(() ->
                            ValidationException.field("bundle", "holds a value the schema lost: " + pair.value()));
            String token = value.abbreviation();
            parts.add(ambiguous.contains(token) ? pair.dimension() + ":" + token : token);
        }
        return String.join(".", parts);
    }

    /** Reads a bundle out in words, in schema order: {@code tense aor, mood subj}. */
    public static String describeBundle(FeatureSchema schema, FeatureBundle bundle) {
        List<String> described = new ArrayList<>();
        for (FeatureDimension dimension : schema.dimensions()) {
            bundle.valueOf(dimension.key()).ifPresent(value -> {
                String label = dimension.value(value).map(FeatureValue::label).orElse(value);
                described.add(Text.lower(dimension.label()) + " " + Text.lower(label));
            });
        }
        return described.isEmpty() ? "no features fixed" : String.join(", ", described);
    }
}
