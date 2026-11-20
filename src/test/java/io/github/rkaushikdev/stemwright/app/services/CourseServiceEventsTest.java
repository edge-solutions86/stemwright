package io.github.rkaushikdev.stemwright.app.services;

import static io.github.rkaushikdev.stemwright.support.App.NOW;
import static io.github.rkaushikdev.stemwright.support.App.seededStores;
import static io.github.rkaushikdev.stemwright.support.App.testHarness;
import static io.github.rkaushikdev.stemwright.support.Course.MICHAELMAS;
import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.app.DomainEvent.AssignmentMet;
import io.github.rkaushikdev.stemwright.app.RecordingBus;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.course.AccountRole;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.domain.course.AssignmentInput;
import io.github.rkaushikdev.stemwright.domain.course.AssignmentProgress;
import io.github.rkaushikdev.stemwright.domain.course.EnrolmentInput;
import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckInput;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import io.github.rkaushikdev.stemwright.support.App.Harness;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reading one learner's progress announces an assignment met when it meets the target, and reading
 * the cohort announces nothing. To reach a met target at all, this sets a one-day practice target
 * and drills once.
 */
class CourseServiceEventsTest {

    private static final LocalDate DAY = CivilDates.parseCivilDate("2024-10-21");
    private static final Actor TEACHER = new Actor("lrn_teacher", AccountRole.INSTRUCTOR);

    private RecordingBus bus;
    private DrillService drill;
    private CourseService service;

    @BeforeEach
    void build() {
        Stores stores = seededStores();
        stores.decks().put(Deck.of(LATIN_NOUN, DeckInput.of("all")));
        stores.courses().put(MICHAELMAS);
        Harness harness = testHarness();
        bus = harness.bus();
        drill = new DrillService(stores, bus, harness.clock());
        service = new CourseService(stores, bus, harness.clock(), drill);

        service.enrol(TEACHER, "latin-1a", EnrolmentInput.of("lrn_one"));
        service.enrol(TEACHER, "latin-1a", EnrolmentInput.of("lrn_two"));
        service.addAssignment(
                TEACHER,
                "latin-1a",
                AssignmentInput.builder("practise", "all", DAY, DAY.plusDays(6))
                        .practiceDays(1)
                        .build());
    }

    private void drillOnceAs(String learnerId) {
        String cellId = drill.queue(learnerId, "latin", "all", DAY)
                .entries()
                .get(0)
                .cell()
                .id();
        drill.answer(AnswerRequest.of(learnerId, "latin", "all", cellId, "x").withOn(DAY));
        bus.clear();
    }

    @Test
    void announcesAMetAssignmentForTheActorWhoAsked() {
        drillOnceAs("lrn_one");

        AssignmentProgress progress = service.progressFor(TEACHER, "latin-1a", "practise", "lrn_one", DAY);

        assertThat(progress.met()).isTrue();
        assertThat(bus.ofKind(AssignmentMet.class))
                .containsExactly(new AssignmentMet(NOW, "lrn_teacher", "practise", "lrn_one", DAY));
    }

    @Test
    void announcesNothingForProgressShortOfTheTarget() {
        AssignmentProgress progress = service.progressFor(TEACHER, "latin-1a", "practise", "lrn_one", DAY);

        assertThat(progress.met()).isFalse();
        assertThat(bus.events()).isEmpty();
    }

    @Test
    void announcesNothingWhenTheCohortIsRead() {
        drillOnceAs("lrn_one");

        List<AssignmentProgress> rows = service.cohortProgress(TEACHER, "latin-1a", "practise", DAY);

        assertThat(rows).extracting(AssignmentProgress::met).containsExactly(true, false);
        assertThat(bus.events()).isEmpty();
    }
}
