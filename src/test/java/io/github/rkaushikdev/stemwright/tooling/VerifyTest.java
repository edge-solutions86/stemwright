package io.github.rkaushikdev.stemwright.tooling;

import static io.github.rkaushikdev.stemwright.tooling.Verify.STAGES;
import static io.github.rkaushikdev.stemwright.tooling.Verify.planFor;
import static io.github.rkaushikdev.stemwright.tooling.Verify.stageNamed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.tooling.Verify.Stage;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VerifyTest {

    @Nested
    class TheVerifyPlan {

        @Test
        void declaresTheFiveStages() {
            assertThat(STAGES).extracting(Stage::name).containsExactly("format", "lint", "types", "build", "test");
        }

        @Test
        void runsTheFastestChecksFirst() {
            List<String> names = STAGES.stream().map(Stage::name).toList();
            assertThat(names.indexOf("format")).isLessThan(names.indexOf("test"));
            assertThat(names.indexOf("lint")).isLessThan(names.indexOf("types"));
        }

        @Test
        void givesEveryStageACommandAndArguments() {
            for (Stage stage : STAGES) {
                assertThat(stage.command()).isNotBlank();
                assertThat(stage.args()).isNotEmpty();
            }
        }
    }

    @Nested
    class StageNamed {

        @Test
        void findsAStageAndReportsAMiss() {
            assertThat(stageNamed("lint").map(Stage::name)).hasValue("lint");
            assertThat(stageNamed("nowhere")).isEmpty();
        }
    }

    @Nested
    class PlanFor {

        @Test
        void runsEverythingWhenNothingIsNamed() {
            assertThat(planFor(List.of())).hasSize(STAGES.size());
        }

        @Test
        void runsOnlyTheStagesNamedInTheOrderGiven() {
            assertThat(planFor(List.of("test", "lint"))).extracting(Stage::name).containsExactly("test", "lint");
        }

        @Test
        void refusesAStageNobodyDeclaredAndListsTheRealOnes() {
            assertThatThrownBy(() -> planFor(List.of("nowhere")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("there is no stage named nowhere");
            assertThatThrownBy(() -> planFor(List.of("nowhere")))
                    .hasMessageContaining("format, lint, types, build, test");
        }
    }
}
