package io.github.rkaushikdev.stemwright.domain.drill;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import io.github.rkaushikdev.stemwright.domain.mastery.AttemptInput;
import io.github.rkaushikdev.stemwright.domain.ortho.MatchVerdict;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResumePlanTest {

    private static final LocalDate TODAY = d("2025-03-03");

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    private static Session withAnswers(Session session, int count) {
        return withAnswers(session, count, MatchVerdict.EXACT);
    }

    /** The session with {@code count} answers to distinct cells, each marked with the verdict. */
    private static Session withAnswers(Session session, int count, MatchVerdict verdict) {
        Session grown = session;
        for (int index = 0; index < count; index += 1) {
            grown = grown.recordAttempt(
                    Attempt.of(AttemptInput.builder()
                            .learnerId(session.learnerId())
                            .sessionId(session.id())
                            .cellId("cell_" + index)
                            .deckKey(session.deckKey())
                            .on(session.on())
                            .sequence(index + 1)
                            .given(verdict == MatchVerdict.EXACT ? "x" : "y")
                            .expected("x")
                            .verdict(verdict)
                            .toleranceLevel("standard")
                            .revisionId("rev_a")
                            .build()),
                    BurialSet.NONE);
        }
        return grown;
    }

    @Nested
    class Of {

        @Test
        void opensANewSessionWhenThereIsNone() {
            ResumePlan plan = ResumePlan.of(Optional.empty(), TODAY);
            assertThat(plan.action()).isEqualTo(ResumeAction.OPEN);
            assertThat(plan.reason()).isEqualTo("there is no session open");
        }

        @Test
        void resumesASessionFromToday() {
            Session session = withAnswers(Session.open("lrn_one", "all", TODAY), 3);
            ResumePlan plan = ResumePlan.of(session, TODAY);
            assertThat(plan.shouldResume()).isTrue();
            assertThat(plan.answered()).isEqualTo(3);
            assertThat(plan.staleFor()).isEqualTo(0);
        }

        @Test
        void closesASessionFromAPreviousDay() {
            Session session = withAnswers(Session.open("lrn_one", "all", d("2025-03-01")), 2);
            ResumePlan plan = ResumePlan.of(session, TODAY);
            assertThat(plan.needsClosing()).isTrue();
            assertThat(plan.staleFor()).isEqualTo(2);
            assertThat(plan.reason()).contains("buried twins no longer apply");
        }

        @Test
        void closesASessionDatedAfterToday() {
            Session session = Session.open("lrn_one", "all", d("2025-03-05"));
            ResumePlan plan = ResumePlan.of(session, TODAY);
            assertThat(plan.needsClosing()).isTrue();
            assertThat(plan.reason()).contains("after today");
        }

        @Test
        void opensANewOneWhenTheSessionItWasGivenIsAlreadyClosed() {
            Session session =
                    withAnswers(Session.open("lrn_one", "all", TODAY), 1).close();
            assertThat(ResumePlan.of(session, TODAY).action()).isEqualTo(ResumeAction.OPEN);
        }

        @Test
        void reportsTheCellsLeftUnresolved() {
            Session session = withAnswers(Session.open("lrn_one", "all", TODAY), 2, MatchVerdict.WRONG);
            assertThat(ResumePlan.of(session, TODAY).unresolved()).hasSize(2);
        }

        @Test
        void reportsNothingUnresolvedForACleanSession() {
            Session session = withAnswers(Session.open("lrn_one", "all", TODAY), 2);
            assertThat(ResumePlan.of(session, TODAY).unresolved()).isEmpty();
        }
    }

    @Nested
    class PickResumable {

        @Test
        void takesTheSessionForTodayAndThisDeck() {
            Session wanted = Session.open("lrn_one", "all", TODAY);
            Session other = Session.open("lrn_one", "core", TODAY);
            Session yesterday = Session.open("lrn_one", "all", d("2025-03-02"));
            assertThat(ResumePlan.pickResumable(List.of(other, yesterday, wanted), "all", TODAY)
                            .map(Session::id))
                    .contains(wanted.id());
        }

        @Test
        void takesTheOneWithTheMostAnswersWhenThereAreTwo() {
            Session thin = withAnswers(Session.open("lrn_one", "all", TODAY, 1), 1);
            Session thick = withAnswers(Session.open("lrn_one", "all", TODAY, 2), 4);
            assertThat(ResumePlan.pickResumable(List.of(thin, thick), "all", TODAY)
                            .map(Session::id))
                    .contains(thick.id());
        }

        @Test
        void ignoresAClosedSession() {
            Session closed = Session.open("lrn_one", "all", TODAY).close();
            assertThat(ResumePlan.pickResumable(List.of(closed), "all", TODAY)).isEmpty();
        }

        @Test
        void givesNothingWhenThereIsNothingToResume() {
            assertThat(ResumePlan.pickResumable(List.of(), "all", TODAY)).isEmpty();
        }
    }

    @Nested
    class StaleSessions {

        @Test
        void namesEveryOpenSessionNotDatedToday() {
            Session today = Session.open("lrn_one", "all", TODAY);
            Session old = Session.open("lrn_one", "core", d("2025-02-28"));
            Session closed = Session.open("lrn_one", "all", d("2025-02-27")).close();
            List<Session> stale = ResumePlan.staleSessions(List.of(today, old, closed), TODAY);
            assertThat(stale.stream().map(Session::deckKey).toList()).containsExactly("core");
        }

        @Test
        void namesNoneWhenEverythingIsFromToday() {
            assertThat(ResumePlan.staleSessions(List.of(Session.open("lrn_one", "all", TODAY)), TODAY))
                    .isEmpty();
        }
    }

    @Nested
    class Describe {

        @Test
        void readsTheReasonOut() {
            Session session = withAnswers(Session.open("lrn_one", "all", TODAY), 2);
            assertThat(ResumePlan.of(session, TODAY).describe())
                    .isEqualTo("resuming a session of 2 answers from today");
        }

        @Test
        void countsTheUnresolvedCells() {
            Session session = withAnswers(Session.open("lrn_one", "all", TODAY), 2, MatchVerdict.WRONG);
            assertThat(ResumePlan.of(session, TODAY).describe()).contains("2 cells left unresolved");
        }
    }
}
