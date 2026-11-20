package io.github.rkaushikdev.stemwright.domain.course;

import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import java.util.Objects;
import java.util.Optional;

/**
 * What a learner asks for, as written, before {@link LearnerPreferences#of} checks it.
 *
 * @param learnerId the learner asking
 * @param patch the policy fields they want changed, none when absent
 * @param toleranceLevel the tolerance level they want marking at, which becomes a slug
 * @param ladderKey the interval ladder they want, which becomes a slug
 */
public record PreferencesInput(
        String learnerId,
        Optional<DrillPolicyPatch> patch,
        Optional<String> toleranceLevel,
        Optional<String> ladderKey) {

    public PreferencesInput {
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(patch, "patch");
        Objects.requireNonNull(toleranceLevel, "toleranceLevel");
        Objects.requireNonNull(ladderKey, "ladderKey");
    }

    public static Builder builder(String learnerId) {
        return new Builder(learnerId);
    }

    /** Collects what a learner asks for; anything left unset is not asked for. */
    public static final class Builder {

        private final String learnerId;
        private DrillPolicyPatch patch;
        private String toleranceLevel;
        private String ladderKey;

        private Builder(String learnerId) {
            this.learnerId = Objects.requireNonNull(learnerId, "learnerId");
        }

        public Builder patch(DrillPolicyPatch patch) {
            this.patch = Objects.requireNonNull(patch, "patch");
            return this;
        }

        public Builder toleranceLevel(String toleranceLevel) {
            this.toleranceLevel = Objects.requireNonNull(toleranceLevel, "toleranceLevel");
            return this;
        }

        public Builder ladderKey(String ladderKey) {
            this.ladderKey = Objects.requireNonNull(ladderKey, "ladderKey");
            return this;
        }

        public PreferencesInput build() {
            return new PreferencesInput(
                    learnerId,
                    Optional.ofNullable(patch),
                    Optional.ofNullable(toleranceLevel),
                    Optional.ofNullable(ladderKey));
        }
    }
}
