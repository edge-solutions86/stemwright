package io.github.rkaushikdev.stemwright.app;

import static io.github.rkaushikdev.stemwright.support.Problems.problemMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import io.github.rkaushikdev.stemwright.support.TestMaps;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConfigTest {

    private static JsonObject.Builder object() {
        return Json.object();
    }

    private static Config read(JsonObject.Builder raw) {
        return Config.read(raw.build());
    }

    @Nested
    class Read {

        @Test
        void takesTheDefaultsForAnEmptyObject() {
            assertThat(read(object())).isEqualTo(Config.DEFAULT);
        }

        @Test
        void readsEveryFieldItRecognises() {
            Config config = read(object().put("dataDir", "/var/stemwright")
                    .put("port", 9000)
                    .put("version", "1.2.3")
                    .put("defaultLanguage", "Greek")
                    .put("snapshotEvery", 100)
                    .put("bodyLimit", 2000));
            assertThat(config.dataDir()).isEqualTo("/var/stemwright");
            assertThat(config.port()).isEqualTo(9000);
            assertThat(config.version()).isEqualTo("1.2.3");
            assertThat(config.defaultLanguage()).isEqualTo("greek");
            assertThat(config.snapshotEvery()).isEqualTo(100);
            assertThat(config.bodyLimit()).isEqualTo(2000);
        }

        @Test
        void refusesSomethingThatIsNotAnObject() {
            assertThatThrownBy(() -> Config.read(Json.of("nope"))).hasMessageContaining("config is not an object");
        }

        @Test
        void refusesAPortThatIsNotOne() {
            assertThat(problemMessages(() -> read(object().put("port", 70000))))
                    .containsExactly("port is not a port number: 70000");
            assertThat(problemMessages(() -> read(object().put("port", 1.5)))).hasSize(1);
        }

        @Test
        void refusesASnapshotIntervalBelowOne() {
            assertThat(problemMessages(() -> read(object().put("snapshotEvery", 0))))
                    .containsExactly("snapshotEvery must be a whole number above zero: 0");
        }

        @Test
        void collectsSeveralProblemsAtOnce() {
            assertThat(problemMessages(() -> read(object().put("port", -1).put("bodyLimit", 0))))
                    .hasSize(2);
        }
    }

    @Nested
    class ThePolicyBlock {

        private static Config withPolicy(Json policy) {
            return read(object().put("policy", policy));
        }

        @Test
        void readsAPatch() {
            assertThat(withPolicy(object().put("newPerDay", 4)
                                    .put("buryTwins", false)
                                    .build())
                            .policy())
                    .isEqualTo(DrillPolicyPatch.builder()
                            .newPerDay(4)
                            .buryTwins(false)
                            .build());
        }

        @Test
        void readsAnAbsentPolicyAsAnEmptyPatch() {
            assertThat(read(object()).policy()).isEqualTo(DrillPolicyPatch.EMPTY);
        }

        @Test
        void refusesAPolicyThatIsNotAnObject() {
            assertThat(problemMessages(() -> withPolicy(Json.of("nope")))).containsExactly("policy is not an object");
        }

        @Test
        void refusesAFieldOfTheWrongType() {
            assertThat(problemMessages(
                            () -> withPolicy(object().put("newPerDay", "lots").build())))
                    .containsExactly("policy.newPerDay is not a number");
            assertThat(problemMessages(
                            () -> withPolicy(object().put("buryTwins", "yes").build())))
                    .containsExactly("policy.buryTwins is not a boolean");
        }

        @Test
        void refusesAPatchThePolicyRulesReject() {
            assertThat(problemMessages(
                            () -> withPolicy(object().put("newPerDay", -1).build())))
                    .hasSize(1);
        }

        @Test
        void ignoresAFieldItDoesNotRecognise() {
            assertThat(withPolicy(object().put("nonsense", 4).build()).policy()).isEqualTo(DrillPolicyPatch.EMPTY);
        }
    }

    @Nested
    class Parse {

        @Test
        void readsAnEmptyFileAsTheDefaults() {
            assertThat(Config.parse("")).isEqualTo(Config.DEFAULT);
            assertThat(Config.parse("   ")).isEqualTo(Config.DEFAULT);
        }

        @Test
        void readsAJsonFile() {
            assertThat(Config.parse(Json.stringify(object().put("port", 9000).build()))
                            .port())
                    .isEqualTo(9000);
        }

        @Test
        void refusesTextThatIsNotJson() {
            assertThatThrownBy(() -> Config.parse("{")).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> Config.parse("{")).hasMessageContaining("not valid json");
        }
    }

    @Nested
    class FromEnvironment {

        @Test
        void takesTheDefaultsForAnEmptyEnvironment() {
            assertThat(Config.fromEnvironment(Map.of())).isEqualTo(Config.DEFAULT);
        }

        @Test
        void readsTheVariablesItRecognises() {
            Config config = Config.fromEnvironment(TestMaps.ordered(
                    "STEMWRIGHT_DATA_DIR", "/data",
                    "STEMWRIGHT_PORT", "9000",
                    "STEMWRIGHT_VERSION", "2.0.0",
                    "STEMWRIGHT_LANGUAGE", "greek"));
            assertThat(config.dataDir()).isEqualTo("/data");
            assertThat(config.port()).isEqualTo(9000);
            assertThat(config.version()).isEqualTo("2.0.0");
            assertThat(config.defaultLanguage()).isEqualTo("greek");
        }

        @Test
        void refusesAPortThatIsNotANumber() {
            assertThatThrownBy(() -> Config.fromEnvironment(Map.of("STEMWRIGHT_PORT", "nope")))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void ignoresAVariableItDoesNotRecognise() {
            assertThat(Config.fromEnvironment(Map.of("STEMWRIGHT_NONSENSE", "x")))
                    .isEqualTo(Config.DEFAULT);
        }
    }

    @Nested
    class Describe {

        @Test
        void readsTheSettingsOut() {
            List<String> lines = Config.DEFAULT.describe();
            assertThat(lines.get(0)).isEqualTo("data directory .stemwright-data");
            assertThat(lines).hasSize(5);
        }
    }
}
