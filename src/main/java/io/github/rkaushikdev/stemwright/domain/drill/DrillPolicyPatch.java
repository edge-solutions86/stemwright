package io.github.rkaushikdev.stemwright.domain.drill;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One layer of a {@link DrillPolicy}: the fields it overrides, and nothing for the ones it leaves to
 * the layer below. A patch is never checked on its own, only once it has been applied, because a
 * partial override can only be judged against what it is overriding.
 *
 * @param newPerDay cells never seen before that may be introduced in one day
 * @param reviewsPerDay cells due for review that may be asked in one day
 * @param perLexemeCap how many cells of one lexeme may appear in a single session
 * @param rowSpacing how many other questions must separate two cells of the same paradigm row
 * @param buryTwins whether answering a cell buries its syncretic twins for the rest of the day
 * @param leechThreshold lapses before a cell is suspended as a leech
 * @param ladderKey which interval ladder the strength model steps along
 * @param restDays weekdays with no drilling at all, as iso numbers where monday is one
 */
public record DrillPolicyPatch(
        Optional<Integer> newPerDay,
        Optional<Integer> reviewsPerDay,
        Optional<Integer> perLexemeCap,
        Optional<Integer> rowSpacing,
        Optional<Boolean> buryTwins,
        Optional<Integer> leechThreshold,
        Optional<String> ladderKey,
        Optional<List<Integer>> restDays) {

    /** A patch that overrides nothing. */
    public static final DrillPolicyPatch EMPTY = builder().build();

    public DrillPolicyPatch {
        Objects.requireNonNull(newPerDay, "newPerDay");
        Objects.requireNonNull(reviewsPerDay, "reviewsPerDay");
        Objects.requireNonNull(perLexemeCap, "perLexemeCap");
        Objects.requireNonNull(rowSpacing, "rowSpacing");
        Objects.requireNonNull(buryTwins, "buryTwins");
        Objects.requireNonNull(leechThreshold, "leechThreshold");
        Objects.requireNonNull(ladderKey, "ladderKey");
        restDays = restDays.map(List::copyOf);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * This patch with another laid over it: a field the later patch sets wins, and a field it leaves
     * out keeps this patch's value. It is how two layers of overrides combine before either is
     * applied.
     */
    public DrillPolicyPatch mergedWith(DrillPolicyPatch later) {
        return new DrillPolicyPatch(
                later.newPerDay.or(() -> newPerDay),
                later.reviewsPerDay.or(() -> reviewsPerDay),
                later.perLexemeCap.or(() -> perLexemeCap),
                later.rowSpacing.or(() -> rowSpacing),
                later.buryTwins.or(() -> buryTwins),
                later.leechThreshold.or(() -> leechThreshold),
                later.ladderKey.or(() -> ladderKey),
                later.restDays.or(() -> restDays));
    }

    /** Builds a {@link DrillPolicyPatch}, leaving out whatever is not set. */
    public static final class Builder {

        private Optional<Integer> newPerDay = Optional.empty();
        private Optional<Integer> reviewsPerDay = Optional.empty();
        private Optional<Integer> perLexemeCap = Optional.empty();
        private Optional<Integer> rowSpacing = Optional.empty();
        private Optional<Boolean> buryTwins = Optional.empty();
        private Optional<Integer> leechThreshold = Optional.empty();
        private Optional<String> ladderKey = Optional.empty();
        private Optional<List<Integer>> restDays = Optional.empty();

        private Builder() {}

        public Builder newPerDay(int value) {
            newPerDay = Optional.of(value);
            return this;
        }

        public Builder reviewsPerDay(int value) {
            reviewsPerDay = Optional.of(value);
            return this;
        }

        public Builder perLexemeCap(int value) {
            perLexemeCap = Optional.of(value);
            return this;
        }

        public Builder rowSpacing(int value) {
            rowSpacing = Optional.of(value);
            return this;
        }

        public Builder buryTwins(boolean value) {
            buryTwins = Optional.of(value);
            return this;
        }

        public Builder leechThreshold(int value) {
            leechThreshold = Optional.of(value);
            return this;
        }

        /** The ladder key as written; it is made a slug when the patch is applied. */
        public Builder ladderKey(String value) {
            ladderKey = Optional.of(value);
            return this;
        }

        /** The rest days as written, in any order and with repeats; applying sorts and dedupes them. */
        public Builder restDays(List<Integer> value) {
            restDays = Optional.of(value);
            return this;
        }

        public DrillPolicyPatch build() {
            return new DrillPolicyPatch(
                    newPerDay, reviewsPerDay, perLexemeCap, rowSpacing, buryTwins, leechThreshold, ladderKey, restDays);
        }
    }
}
