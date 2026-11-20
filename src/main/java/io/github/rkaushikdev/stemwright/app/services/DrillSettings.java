package io.github.rkaushikdev.stemwright.app.services;

import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicy;
import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import io.github.rkaushikdev.stemwright.domain.mastery.Ladder;
import io.github.rkaushikdev.stemwright.domain.mastery.LeechPolicy;
import java.util.Objects;

/**
 * How a learner is drilled, as far as the caller decides it: the adjustment to the default drill
 * policy that builds the queue, the ladder an answer moves a cell along, and the leech policy.
 *
 * @param policy the adjustment to {@link DrillPolicy#DEFAULT}, {@link DrillPolicyPatch#EMPTY} for
 *     none
 * @param ladder the interval ladder, {@link Ladder#STANDARD} unless the caller says otherwise
 * @param leech the leech policy, {@link LeechPolicy#DEFAULT} unless the caller says otherwise
 */
public record DrillSettings(DrillPolicyPatch policy, Ladder ladder, LeechPolicy leech) {

    /** The default policy, the standard ladder and the default leech policy. */
    public static final DrillSettings DEFAULT =
            new DrillSettings(DrillPolicyPatch.EMPTY, Ladder.STANDARD, LeechPolicy.DEFAULT);

    public DrillSettings {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(ladder, "ladder");
        Objects.requireNonNull(leech, "leech");
    }

    /** A builder starting from {@link #DEFAULT}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Collects the settings a caller changes; any left alone keep their {@link #DEFAULT} value. */
    public static final class Builder {

        private DrillPolicyPatch policy = DEFAULT.policy;
        private Ladder ladder = DEFAULT.ladder;
        private LeechPolicy leech = DEFAULT.leech;

        private Builder() {}

        public Builder policy(DrillPolicyPatch policy) {
            this.policy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public Builder ladder(Ladder ladder) {
            this.ladder = Objects.requireNonNull(ladder, "ladder");
            return this;
        }

        public Builder leech(LeechPolicy leech) {
            this.leech = Objects.requireNonNull(leech, "leech");
            return this;
        }

        public DrillSettings build() {
            return new DrillSettings(policy, ladder, leech);
        }
    }
}
