package io.github.rkaushikdev.stemwright.domain.report;

import java.util.List;

/** How the reports cut a ranked list down to the rows a reader is shown. */
final class Ranking {

    private Ranking() {}

    /**
     * The first rows of a ranking. A limit past the end keeps every row, and a negative limit keeps
     * every row but that many from the far end, which is how the reports have always read a limit.
     */
    static <T> List<T> head(List<T> ranked, int limit) {
        int end = limit < 0 ? Math.max(ranked.size() + limit, 0) : Math.min(limit, ranked.size());
        return List.copyOf(ranked.subList(0, end));
    }
}
