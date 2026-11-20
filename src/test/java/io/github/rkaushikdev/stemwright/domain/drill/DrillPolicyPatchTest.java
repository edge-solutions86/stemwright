package io.github.rkaushikdev.stemwright.domain.drill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Java only. The original spells patch layering as an object spread; these hold {@link
 * DrillPolicyPatch#mergedWith} to the same meaning.
 */
class DrillPolicyPatchTest {

    @Test
    void letsALaterPatchWinWhereBothSetAField() {
        DrillPolicyPatch earlier = DrillPolicyPatch.builder().newPerDay(4).build();
        DrillPolicyPatch later = DrillPolicyPatch.builder().newPerDay(9).build();
        assertThat(earlier.mergedWith(later).newPerDay()).contains(9);
    }

    @Test
    void keepsWhatTheEarlierPatchSetAndTheLaterLeftOut() {
        DrillPolicyPatch earlier = DrillPolicyPatch.builder().newPerDay(4).build();
        DrillPolicyPatch later = DrillPolicyPatch.builder().reviewsPerDay(20).build();
        assertThat(earlier.mergedWith(later))
                .isEqualTo(DrillPolicyPatch.builder()
                        .newPerDay(4)
                        .reviewsPerDay(20)
                        .build());
    }

    @Test
    void overridesNothingWhenEmpty() {
        DrillPolicyPatch patch = DrillPolicyPatch.builder()
                .buryTwins(false)
                .ladderKey("slow-start")
                .build();
        assertThat(patch.mergedWith(DrillPolicyPatch.EMPTY)).isEqualTo(patch);
        assertThat(DrillPolicyPatch.EMPTY.mergedWith(patch)).isEqualTo(patch);
    }

    @Test
    void holdsItsRestDaysAgainstALaterChangeToTheListItWasGiven() {
        List<Integer> days = new ArrayList<>(List.of(6, 7));
        DrillPolicyPatch patch = DrillPolicyPatch.builder().restDays(days).build();
        days.add(1);
        assertThat(patch.restDays()).contains(List.of(6, 7));
    }
}
