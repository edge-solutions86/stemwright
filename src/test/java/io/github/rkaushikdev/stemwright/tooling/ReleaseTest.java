package io.github.rkaushikdev.stemwright.tooling;

import static io.github.rkaushikdev.stemwright.tooling.Release.binariesIn;
import static io.github.rkaushikdev.stemwright.tooling.Release.changelogMentions;
import static io.github.rkaushikdev.stemwright.tooling.Release.checkAgreement;
import static io.github.rkaushikdev.stemwright.tooling.Release.checkRelease;
import static io.github.rkaushikdev.stemwright.tooling.Release.parsePom;
import static io.github.rkaushikdev.stemwright.tooling.Release.versionFromSource;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.tooling.Release.Binary;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReleaseTest {

    private static final String MAIN = "io.github.rkaushikdev.stemwright.bin.Stemwright";
    private static final String MISSING = "io.github.rkaushikdev.stemwright.bin.Missing";
    private static final Set<String> DECLARED = Set.of(MAIN, "io.github.rkaushikdev.stemwright.bin.Serve");

    private static final String GOOD =
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <version>1.2.3</version>
              <properties>
                <main.class>io.github.rkaushikdev.stemwright.bin.Stemwright</main.class>
              </properties>
              <build>
                <plugins>
                  <plugin>
                    <artifactId>maven-shade-plugin</artifactId>
                    <executions>
                      <execution>
                        <configuration>
                          <shadedArtifactAttached>true</shadedArtifactAttached>
                          <shadedClassifierName>cli</shadedClassifierName>
                          <transformers>
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                              <mainClass>${main.class}</mainClass>
                            </transformer>
                          </transformers>
                        </configuration>
                      </execution>
                    </executions>
                  </plugin>
                </plugins>
              </build>
            </project>
            """;

    @Nested
    class VersionFromSource {

        @Test
        void readsTheExportedConstant() {
            assertThat(versionFromSource("public static final String VERSION = \"1.2.3\";"))
                    .hasValue("1.2.3");
        }

        @Test
        void givesNothingWhenThereIsNone() {
            assertThat(versionFromSource("static final int VERSION = 1;")).isEmpty();
        }
    }

    @Nested
    class ChangelogMentions {

        @Test
        void acceptsASectionForTheVersion() {
            assertThat(changelogMentions("# Changelog\n\n## 1.2.3\n", "1.2.3")).isTrue();
        }

        @Test
        void acceptsAnUnreleasedSection() {
            assertThat(changelogMentions("# Changelog\n\n## Unreleased\n", "1.2.3"))
                    .isTrue();
        }

        @Test
        void refusesAChangelogWithNeither() {
            assertThat(changelogMentions("# Changelog\n\n## 1.0.0\n", "1.2.3")).isFalse();
        }
    }

    @Nested
    class BinariesIn {

        @Test
        void listsTheDeclaredBinaries() {
            assertThat(binariesIn(parsePom(GOOD))).containsExactly(new Binary("cli", Optional.of(MAIN)));
        }

        @Test
        void listsNothingWhenThereAreNone() {
            assertThat(binariesIn(parsePom("<project/>"))).isEmpty();
        }
    }

    @Nested
    class CheckAgreement {

        private static final String CHANGELOG = "## Unreleased\n";

        @Test
        void passesWhenEverythingAgrees() {
            assertThat(checkAgreement(parsePom(GOOD), Optional.of("1.2.3"), CHANGELOG, DECLARED))
                    .isEmpty();
        }

        @Test
        void namesAVersionMismatch() {
            assertThat(checkAgreement(parsePom(GOOD), Optional.of("1.0.0"), CHANGELOG, DECLARED))
                    .containsExactly("pom.xml says 1.2.3 and the cli says 1.0.0");
        }

        @Test
        void namesAMissingVersionConstant() {
            assertThat(checkAgreement(parsePom(GOOD), Optional.empty(), CHANGELOG, DECLARED)
                            .get(0))
                    .contains("VERSION constant");
        }

        @Test
        void namesAChangelogWithNoSection() {
            assertThat(checkAgreement(parsePom(GOOD), Optional.of("1.2.3"), "# Changelog\n", DECLARED)
                            .get(0))
                    .contains("no section for 1.2.3");
        }

        @Test
        void namesACliJarThatRunsAClassNobodyDeclared() {
            String broken =
                    GOOD.replace("<mainClass>${main.class}</mainClass>", "<mainClass>" + MISSING + "</mainClass>");
            assertThat(checkAgreement(parsePom(broken), Optional.of("1.2.3"), CHANGELOG, DECLARED)
                            .get(0))
                    .isEqualTo("the cli jar runs " + MISSING + ", which src/main/java does not declare");
        }

        @Test
        void namesAMainClassNobodyDeclaredAndACliJarNobodyAttached() {
            String broken = GOOD.replace(
                            "<main.class>" + MAIN + "</main.class>", "<main.class>" + MISSING + "</main.class>")
                    .replace("<shadedArtifactAttached>true", "<shadedArtifactAttached>false");
            assertThat(checkAgreement(parsePom(broken), Optional.of("1.2.3"), CHANGELOG, DECLARED))
                    .containsExactly(
                            "the shade plugin does not attach a cli jar",
                            "main.class names " + MISSING + ", which src/main/java does not declare");
        }
    }

    @Nested
    class TheCheckedInReleaseMetadata {

        @Test
        void agreesEverywhere() {
            Release.Result result = checkRelease();
            assertThat(result.problems()).isEmpty();
            assertThat(result.ok()).isTrue();
        }

        @Test
        void namesAVersionThatLooksLikeOne() {
            assertThat(checkRelease().version())
                    .hasValueSatisfying(version -> assertThat(version).matches("\\d+\\.\\d+\\.\\d+"));
        }
    }
}
