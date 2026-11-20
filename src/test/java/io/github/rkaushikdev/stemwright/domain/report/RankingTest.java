package io.github.rkaushikdev.stemwright.domain.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RankingTest {

    private static final List<String> RANKED = List.of("a", "b", "c", "d");

    @Test
    void keepsTheFirstRowsUpToTheLimit() {
        assertThat(Ranking.head(RANKED, 2)).containsExactly("a", "b");
        assertThat(Ranking.head(RANKED, 0)).isEmpty();
    }

    @Test
    void keepsEveryRowForALimitPastTheEnd() {
        assertThat(Ranking.head(RANKED, 99)).containsExactly("a", "b", "c", "d");
    }

    @Test
    void dropsThatManyRowsFromTheFarEndForANegativeLimit() {
        assertThat(Ranking.head(RANKED, -1)).containsExactly("a", "b", "c");
        assertThat(Ranking.head(RANKED, -9)).isEmpty();
    }
}
