package io.github.rkaushikdev.stemwright.domain.course;

import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import io.github.rkaushikdev.stemwright.domain.mastery.ShieldSet;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * An enrolment as a registry or a request writes it, before {@link Enrolment#of} checks it against
 * the course.
 *
 * @param learnerId the account being enrolled
 * @param role what they are on the course for, a learner when absent
 * @param from the first day they are measured on, the first day of the term when absent
 * @param to the last day they are measured on, the last day of the term when absent
 * @param shields the days they are excused from, none when absent
 * @param policyPatch an instructor's adjustment of the drilling policy, none when absent
 */
public record EnrolmentInput(
        String learnerId,
        Optional<EnrolmentRole> role,
        Optional<LocalDate> from,
        Optional<LocalDate> to,
        Optional<ShieldSet> shields,
        Optional<DrillPolicyPatch> policyPatch) {

    public EnrolmentInput {
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(shields, "shields");
        Objects.requireNonNull(policyPatch, "policyPatch");
    }

    /** A learner enrolled for the whole term, with no shields and no policy patch. */
    public static EnrolmentInput of(String learnerId) {
        return builder(learnerId).build();
    }

    public static Builder builder(String learnerId) {
        return new Builder(learnerId);
    }

    /** Collects the optional parts of an enrolment; any left unset take the defaults above. */
    public static final class Builder {

        private final String learnerId;
        private EnrolmentRole role;
        private LocalDate from;
        private LocalDate to;
        private ShieldSet shields;
        private DrillPolicyPatch policyPatch;

        private Builder(String learnerId) {
            this.learnerId = Objects.requireNonNull(learnerId, "learnerId");
        }

        public Builder role(EnrolmentRole role) {
            this.role = Objects.requireNonNull(role, "role");
            return this;
        }

        public Builder from(LocalDate from) {
            this.from = Objects.requireNonNull(from, "from");
            return this;
        }

        public Builder to(LocalDate to) {
            this.to = Objects.requireNonNull(to, "to");
            return this;
        }

        public Builder shields(ShieldSet shields) {
            this.shields = Objects.requireNonNull(shields, "shields");
            return this;
        }

        public Builder policyPatch(DrillPolicyPatch policyPatch) {
            this.policyPatch = Objects.requireNonNull(policyPatch, "policyPatch");
            return this;
        }

        public EnrolmentInput build() {
            return new EnrolmentInput(
                    learnerId,
                    Optional.ofNullable(role),
                    Optional.ofNullable(from),
                    Optional.ofNullable(to),
                    Optional.ofNullable(shields),
                    Optional.ofNullable(policyPatch));
        }
    }
}
