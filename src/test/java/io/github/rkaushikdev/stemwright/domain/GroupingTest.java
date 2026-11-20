package io.github.rkaushikdev.stemwright.domain;

import static io.github.rkaushikdev.stemwright.domain.Grouping.countBy;
import static io.github.rkaushikdev.stemwright.domain.Grouping.distinct;
import static io.github.rkaushikdev.stemwright.domain.Grouping.groupBy;
import static io.github.rkaushikdev.stemwright.domain.Grouping.groupSorted;
import static io.github.rkaushikdev.stemwright.domain.Grouping.indexBy;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.text.Collation;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GroupingTest {

    private record Row(String id, String kind, int weight) {}

    private static final List<Row> ROWS =
            List.of(new Row("a", "noun", 3), new Row("b", "verb", 1), new Row("c", "noun", 2), new Row("d", "noun", 2));

    private static List<String> ids(List<Row> rows) {
        return rows.stream().map(Row::id).toList();
    }

    @Nested
    class GroupBy {

        @Test
        void bucketsByTheKeyItIsGiven() {
            Map<String, List<Row>> grouped = groupBy(ROWS, Row::kind);
            assertThat(grouped.keySet()).containsExactly("noun", "verb");
            assertThat(grouped.get("noun")).hasSize(3);
        }

        @Test
        void keepsTheOrderItemsArrivedIn() {
            Map<String, List<Row>> grouped = groupBy(ROWS, Row::kind);
            assertThat(ids(grouped.get("noun"))).containsExactly("a", "c", "d");
        }

        @Test
        void givesAnEmptyMapForAnEmptyInput() {
            assertThat(groupBy(List.<Row>of(), row -> "x")).isEmpty();
        }

        @Test
        void takesAnyIterableNotOnlyAList() {
            Map<String, List<Row>> grouped = groupBy(new LinkedHashSet<>(ROWS), Row::kind);
            assertThat(grouped).hasSize(2);
        }
    }

    @Nested
    class GroupSorted {

        @Test
        void sortsEachBucketByTheComparator() {
            Comparator<Row> byWeightThenId =
                    Comparator.comparingInt(Row::weight).thenComparing(Row::id, Collation.ORDER);
            Map<String, List<Row>> grouped = groupSorted(ROWS, Row::kind, byWeightThenId);
            assertThat(ids(grouped.get("noun"))).containsExactly("c", "d", "a");
        }

        @Test
        void leavesASingleItemBucketAlone() {
            Map<String, List<Row>> grouped = groupSorted(ROWS, Row::kind, Comparator.comparingInt(Row::weight));
            assertThat(ids(grouped.get("verb"))).containsExactly("b");
        }
    }

    @Nested
    class CountBy {

        @Test
        void countsTheMembersOfEachBucket() {
            Map<String, Integer> counts = countBy(ROWS, Row::kind);
            assertThat(counts).containsEntry("noun", 3).containsEntry("verb", 1);
            assertThat(counts).doesNotContainKey("adjective");
        }

        @Test
        void countsNothingForAnEmptyInput() {
            assertThat(countBy(List.<Row>of(), row -> "x")).isEmpty();
        }
    }

    @Nested
    class Distinct {

        @Test
        void keepsTheFirstOfEachKey() {
            assertThat(ids(distinct(ROWS, row -> String.valueOf(row.weight())))).containsExactly("a", "b", "c");
        }

        @Test
        void keepsEverythingWhenEveryKeyDiffers() {
            assertThat(distinct(ROWS, Row::id)).hasSize(4);
        }
    }

    @Nested
    class IndexBy {

        @Test
        void keysEachItemByTheFunction() {
            Map<String, Row> index = indexBy(ROWS, Row::id);
            assertThat(index).hasSize(4);
            assertThat(index.get("c").weight()).isEqualTo(2);
        }

        @Test
        void letsALaterItemWinARepeatedKey() {
            Map<String, Row> index = indexBy(ROWS, Row::kind);
            assertThat(index.get("noun").id()).isEqualTo("d");
        }
    }
}
