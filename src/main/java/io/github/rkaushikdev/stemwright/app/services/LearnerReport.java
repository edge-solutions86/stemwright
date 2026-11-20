package io.github.rkaushikdev.stemwright.app.services;

import io.github.rkaushikdev.stemwright.domain.mastery.LedgerCounts;
import io.github.rkaushikdev.stemwright.domain.report.CoverageRow;
import io.github.rkaushikdev.stemwright.domain.report.ForecastDay;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Where one learner stands on one deck, read from their ledger: how the deck's cells are spread
 * over the ladder, coverage down the bundles and down the lexemes, the bundles that need the most
 * work, and the reviews already scheduled over the range.
 *
 * @param learnerId the learner reported on
 * @param deckKey the deck reported on
 * @param on the day the counts and the coverage are read on, the first day of the range
 * @param counts how the deck's cells stand in the ledger on that day
 * @param byBundle one coverage row per bundle, in bundle key order as a reader sorts them
 * @param byLexeme one coverage row per lexeme, in lexeme id order as a reader sorts them
 * @param weakest at most five of the bundle rows, those that need the most work, the weakest first
 * @param forecast one row for every day of the range, counting the reviews that fall due on it
 */
public record LearnerReport(
        String learnerId,
        String deckKey,
        LocalDate on,
        LedgerCounts counts,
        List<CoverageRow> byBundle,
        List<CoverageRow> byLexeme,
        List<CoverageRow> weakest,
        List<ForecastDay> forecast) {

    public LearnerReport {
        Objects.requireNonNull(learnerId, "learnerId");
        Objects.requireNonNull(deckKey, "deckKey");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(counts, "counts");
        byBundle = List.copyOf(byBundle);
        byLexeme = List.copyOf(byLexeme);
        weakest = List.copyOf(weakest);
        forecast = List.copyOf(forecast);
    }
}
