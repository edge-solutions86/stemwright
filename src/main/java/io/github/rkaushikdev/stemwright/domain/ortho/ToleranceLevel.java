package io.github.rkaushikdev.stemwright.domain.ortho;

import java.util.List;

/**
 * How forgiving the marking is, by name.
 *
 * <p>Tolerance is named rather than numeric because the names are what appear on a syllabus. A first
 * year deck runs at {@code lenient} and the prose composition paper runs at {@code exact}, and both
 * mean something specific about accents that a number would not carry.
 *
 * @param key the slug a deck, a cell or an assignment names the level by
 * @param label the name the level is printed under
 * @param folds the steps the level forgives, in {@link Folding#ORDER}
 */
public record ToleranceLevel(String key, String label, List<FoldStep> folds) {

    public ToleranceLevel {
        folds = List.copyOf(folds);
    }

    /**
     * A level is stricter than another when its fold list is a proper subset. This is a partial order
     * on purpose: two levels that fold different things are not comparable, and pretending otherwise
     * would let an override loosen marking while claiming to tighten it.
     */
    public boolean isStricterThan(ToleranceLevel other) {
        if (folds.size() >= other.folds.size()) {
            return false;
        }
        return other.folds.containsAll(folds);
    }

    /** True for the same level, and for two levels of which one is stricter than the other. */
    public boolean isComparableTo(ToleranceLevel other) {
        return key.equals(other.key) || isStricterThan(other) || other.isStricterThan(this);
    }
}
