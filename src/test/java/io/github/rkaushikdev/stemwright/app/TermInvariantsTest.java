package io.github.rkaushikdev.stemwright.app;

import static io.github.rkaushikdev.stemwright.support.App.seededStores;
import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.app.DomainEvent.AttemptRecorded;
import io.github.rkaushikdev.stemwright.app.services.AnswerRequest;
import io.github.rkaushikdev.stemwright.app.services.DrillSettings;
import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckInput;
import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import io.github.rkaushikdev.stemwright.domain.drill.Queue;
import io.github.rkaushikdev.stemwright.domain.drill.QueueEntry;
import io.github.rkaushikdev.stemwright.domain.drill.ReviewResult;
import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.mastery.CellStrength;
import io.github.rkaushikdev.stemwright.domain.mastery.Ladder;
import io.github.rkaushikdev.stemwright.domain.mastery.LadderInput;
import io.github.rkaushikdev.stemwright.domain.mastery.LeechPolicy;
import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.support.SeededRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A whole term, driven through the application the way a client would.
 *
 * <p>This is the test that would catch a change nobody thought was risky. Every unit test below it
 * holds one module to its contract; this one holds the arrangement of them to the facts that have
 * to be true after real use, and those facts are the ones an operator would notice were broken.
 *
 * <p>Each day the learner answers every cell of the queue wrong, then answers it right with a
 * seeded chance of seven in ten, and closes the session.
 */
class TermInvariantsTest {

    private static final LocalDate START = CivilDates.parseCivilDate("2024-10-07");
    private static final int DAYS = 40;
    private static final int NEW_PER_DAY = 6;
    private static final int LEECH_THRESHOLD = 4;

    private static final Ladder FLAT =
            Ladder.of(LadderInput.builder("flat", 1, 2, 4, 9, 20).spread(0).build());

    private static final DrillSettings SETTINGS = DrillSettings.builder()
            .policy(DrillPolicyPatch.builder()
                    .newPerDay(NEW_PER_DAY)
                    .reviewsPerDay(40)
                    .perLexemeCap(3)
                    .rowSpacing(1)
                    .build())
            .ladder(FLAT)
            .leech(LeechPolicy.builder().threshold(LEECH_THRESHOLD).build())
            .build();

    /**
     * What a term left behind.
     *
     * @param stores the stores the term wrote to
     * @param bus the bus every service published to
     * @param sessionsSeen the session closed at the end of each day that had a queue, in day order
     * @param lastDay the day after the last day of the term
     */
    private record Term(Stores stores, RecordingBus bus, List<String> sessionsSeen, LocalDate lastDay) {

        MasteryLedger ledger() {
            return stores.ledgers().get("lrn_one").orElseThrow();
        }

        List<Attempt> attempts() {
            return stores.attempts().listByLearner("lrn_one");
        }

        List<CellStrength> suspended() {
            return ledger().byCell().values().stream()
                    .filter(CellStrength::suspended)
                    .toList();
        }
    }

    private static AnswerRequest answering(String cellId, String given, LocalDate day) {
        return AnswerRequest.of("lrn_one", "latin", "all", cellId, given).withOn(day);
    }

    private static Term runTerm() {
        Stores stores = seededStores();
        stores.decks().put(Deck.of(LATIN_NOUN, DeckInput.of("all")));

        SteppableClock clock = Clock.steppable(Instant.parse("2024-10-07T09:00:00Z"), START);
        RecordingBus bus = RecordingBus.create();
        Application app = Application.create(stores, ApplicationOptions.of(bus, clock));
        SeededRandom random = new SeededRandom(4021);

        List<String> sessionsSeen = new ArrayList<>();
        LocalDate day = START;

        for (int index = 0; index < DAYS; index += 1) {
            clock.set(Instant.parse(day + "T09:00:00Z"), day);
            Queue queue = app.drill().queue("lrn_one", "latin", "all", day, SETTINGS);

            Optional<String> sessionId = Optional.empty();
            for (QueueEntry entry : queue.entries()) {
                ReviewResult expected = app.drill()
                        .answer(answering(entry.cell().id(), "deliberately wrong", day), SETTINGS)
                        .review();
                sessionId = Optional.of(expected.attempt().sessionId());

                if (random.bool(0.7)) {
                    app.drill().answer(answering(entry.cell().id(), expected.expected(), day), SETTINGS);
                }
            }

            if (sessionId.isPresent()) {
                app.drill().finish("lrn_one", sessionId.get());
                sessionsSeen.add(sessionId.get());
            }
            day = CivilDates.addDays(day, 1);
        }

        return new Term(stores, bus, List.copyOf(sessionsSeen), day);
    }

    @Nested
    class ATermOfDrilling {

        @Test
        void recordsAnAttemptForEveryAnswerAndNoMore() {
            Term term = runTerm();
            List<Attempt> attempts = term.attempts();
            assertThat(attempts.size()).isGreaterThan(100);
            assertThat(term.bus().ofKind(AttemptRecorded.class)).hasSize(attempts.size());
        }

        @Test
        void numbersTheAttemptsOfEachSessionFromOneWithNoGaps() {
            Term term = runTerm();
            assertThat(term.sessionsSeen()).isNotEmpty();
            for (String sessionId : term.sessionsSeen()) {
                List<Attempt> attempts = term.stores().attempts().listBySession(sessionId);
                assertThat(attempts)
                        .extracting(Attempt::sequence)
                        .containsExactlyElementsOf(IntStream.rangeClosed(1, attempts.size())
                                .boxed()
                                .toList());
            }
        }

        @Test
        void closesEverySessionItOpened() {
            Term term = runTerm();
            assertThat(term.stores().sessions().listOpen("lrn_one")).isEmpty();
            assertThat(term.sessionsSeen().size()).isGreaterThan(10);
        }

        @Test
        void leavesEverySeenCellWithADueDateAndARungOnTheLadder() {
            Term term = runTerm();
            MasteryLedger ledger = term.ledger();
            assertThat(ledger.byCell()).isNotEmpty();
            for (CellStrength strength : ledger.byCell().values()) {
                if (strength.seen() == 0) {
                    continue;
                }
                assertThat(strength.dueOn()).isPresent();
                assertThat(strength.rung()).isGreaterThanOrEqualTo(0);
                assertThat(strength.rung()).isLessThan(FLAT.rungs().size());
            }
        }

        @Test
        void neverRecordsMoreCorrectAnswersThanSightingsOnAnyCell() {
            Term term = runTerm();
            MasteryLedger ledger = term.ledger();
            assertThat(ledger.byCell()).isNotEmpty();
            for (CellStrength strength : ledger.byCell().values()) {
                assertThat(strength.correct()).isLessThanOrEqualTo(strength.seen());
            }
        }

        @Test
        void agreesWithTheAttemptLogAboutHowManyTimesEachCellWasSeen() {
            Term term = runTerm();
            MasteryLedger ledger = term.ledger();

            Map<String, Integer> counted = new LinkedHashMap<>();
            for (Attempt entry : term.attempts()) {
                counted.merge(entry.cellId(), 1, Integer::sum);
            }
            assertThat(counted).isNotEmpty();
            counted.forEach((cellId, count) ->
                    assertThat(ledger.strengthFor(cellId).seen()).as(cellId).isEqualTo(count));
        }

        @Test
        void suspendsACellOnlyAfterItHasLapsedPastTheThreshold() {
            Term term = runTerm();
            List<CellStrength> suspended = term.suspended();
            assertThat(suspended).isNotEmpty();
            for (CellStrength strength : suspended) {
                assertThat(strength.lapses()).isGreaterThanOrEqualTo(LEECH_THRESHOLD);
            }
        }

        @Test
        void leavesNoSuspendedCellInTheDueSetOnTheDayAfterTheTerm() {
            Term term = runTerm();
            List<CellStrength> suspended = term.suspended();
            assertThat(suspended).isNotEmpty();
            for (CellStrength strength : suspended) {
                assertThat(strength.isDue(term.lastDay())).isFalse();
            }
        }

        @Test
        void introducesNewCellsAtTheBudgetAndNoFaster() {
            Term term = runTerm();
            Map<String, LocalDate> firstSeen = new LinkedHashMap<>();
            for (Attempt entry : term.attempts()) {
                firstSeen.putIfAbsent(entry.cellId(), entry.on());
            }
            Map<LocalDate, Integer> perDay = new LinkedHashMap<>();
            for (LocalDate day : firstSeen.values()) {
                perDay.merge(day, 1, Integer::sum);
            }
            assertThat(perDay).isNotEmpty();
            for (int count : perDay.values()) {
                assertThat(count).isLessThanOrEqualTo(NEW_PER_DAY);
            }
        }
    }
}
