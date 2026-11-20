package io.github.rkaushikdev.stemwright.domain.paradigm;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.features.FeatureBundle;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The form a lexeme takes in one slot, with everything needed to say why: the stem and the affix
 * it was built from and the join rules that fired, or the fact that the profile stated it outright.
 *
 * @param bundle the canonical bundle key
 * @param form the realised form, which is the expected answer
 * @param stemRole the role of the stem used, which may be the base stem a missing role fell back to
 * @param stem the stem form used, empty for a literal
 * @param affix the affix attached, empty for a literal
 * @param fromLiteral whether the profile stated the form outright
 * @param steps the join rules that fired
 */
public record RealisedCell(
        String bundle,
        String form,
        String stemRole,
        String stem,
        String affix,
        boolean fromLiteral,
        List<JoinStep> steps) {

    public RealisedCell {
        steps = List.copyOf(steps);
    }

    /**
     * The sentence an instructor is shown when a learner asks why the expected answer is what it
     * is, such as {@code reg plus sis gives rexis in latin after g-before-s}.
     *
     * @throws ValidationException when the bundle is not the one this cell was realised for
     */
    public String explain(FeatureSchema schema, FeatureBundle bundle) {
        if (!bundle.key().equals(this.bundle)) {
            throw ValidationException.field("bundle", "does not match the realised cell");
        }
        if (fromLiteral) {
            return form + " is stated outright by the profile";
        }
        String seam = stem + " plus " + (affix.isEmpty() ? "no ending" : affix);
        if (steps.isEmpty()) {
            return seam + " gives " + form + " in " + schema.language();
        }
        String applied = steps.stream().map(JoinStep::rule).collect(Collectors.joining(", "));
        return seam + " gives " + form + " in " + schema.language() + " after " + applied;
    }
}
