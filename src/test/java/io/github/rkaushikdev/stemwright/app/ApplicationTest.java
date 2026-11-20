package io.github.rkaushikdev.stemwright.app;

import static io.github.rkaushikdev.stemwright.support.App.seededStores;
import static io.github.rkaushikdev.stemwright.support.Course.MICHAELMAS;
import static io.github.rkaushikdev.stemwright.support.Profiles.LATIN_NOUN;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.app.DomainEvent.AttemptRecorded;
import io.github.rkaushikdev.stemwright.app.services.AnswerOutcome;
import io.github.rkaushikdev.stemwright.app.services.AnswerRequest;
import io.github.rkaushikdev.stemwright.app.services.DeckPreview;
import io.github.rkaushikdev.stemwright.app.services.DrillSettings;
import io.github.rkaushikdev.stemwright.domain.course.AccountRole;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.domain.course.CourseScope;
import io.github.rkaushikdev.stemwright.domain.course.Roster;
import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckInput;
import io.github.rkaushikdev.stemwright.domain.drill.Queue;
import io.github.rkaushikdev.stemwright.domain.mastery.Ladder;
import io.github.rkaushikdev.stemwright.domain.mastery.LadderInput;
import io.github.rkaushikdev.stemwright.domain.mastery.LeechPolicy;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ApplicationTest {

    private static final LocalDate DAY = d("2024-10-21");
    private static final Actor TEACHER = new Actor("lrn_teacher", AccountRole.INSTRUCTOR);

    private static final Ladder FLAT =
            Ladder.of(LadderInput.builder("flat", 1, 3).spread(0).build());

    private static final DrillSettings ON_THE_FLAT_LADDER =
            DrillSettings.builder().ladder(FLAT).build();

    private static LocalDate d(String value) {
        return CivilDates.parseCivilDate(value);
    }

    /** The seeded stores with a deck that takes the whole language and the Michaelmas course. */
    private record Built(Stores stores, RecordingBus bus, Application app) {}

    private static Built build() {
        Stores stores = seededStores();
        stores.decks().put(Deck.of(LATIN_NOUN, DeckInput.of("all")));
        stores.courses().put(MICHAELMAS);
        RecordingBus bus = RecordingBus.create();
        Clock clock = Clock.fixed(Instant.parse("2024-10-21T09:00:00Z"), DAY);
        return new Built(stores, bus, Application.create(stores, ApplicationOptions.of(bus, clock)));
    }

    private static AnswerOutcome answerFirst(Application app, String given) {
        return answerFirst(app, given, ON_THE_FLAT_LADDER);
    }

    /** Answers the first cell of the learner's queue on the flat ladder, under the settings given. */
    private static AnswerOutcome answerFirst(Application app, String given, DrillSettings settings) {
        Queue queue = app.drill().queue("lrn_one", "latin", "all", DAY, ON_THE_FLAT_LADDER);
        String cellId = queue.entries().get(0).cell().id();
        return app.drill()
                .answer(
                        AnswerRequest.of("lrn_one", "latin", "all", cellId, given)
                                .withOn(DAY),
                        settings);
    }

    @Nested
    class CreateApplication {

        @Test
        void buildsEveryService() {
            Application app = build().app();
            assertThat(app.lexicon()).isNotNull();
            assertThat(app.profiles()).isNotNull();
            assertThat(app.decks()).isNotNull();
            assertThat(app.drill()).isNotNull();
            assertThat(app.courses()).isNotNull();
            assertThat(app.reports()).isNotNull();
        }

        @Test
        void givesEveryServiceTheSameClock() {
            Application app = build().app();
            assertThat(app.clock().today()).isEqualTo(d("2024-10-21"));
            assertThat(app.drill().queue("lrn_one", "latin", "all").on()).isEqualTo(d("2024-10-21"));
        }

        @Test
        void givesEveryServiceTheSameBus() {
            Built built = build();
            answerFirst(built.app(), "x");
            assertThat(built.bus().ofKind(AttemptRecorded.class)).hasSize(1);
        }

        @Test
        void takesASystemClockWhenNoneIsGiven() {
            Stores stores = seededStores();
            assertThat(Application.create(stores).clock().today().toString()).matches("\\d{4}-\\d{2}-\\d{2}");
        }

        @Test
        void letsTheServicesReachOneAnother() {
            Application app = build().app();
            CourseScope scope = new CourseScope(MICHAELMAS, Roster.of(MICHAELMAS, List.of()));
            DeckPreview preview = app.decks().preview("latin", DeckInput.of("draft"), DAY);
            assertThat(preview.membership().cells()).isNotEmpty();
            assertThat(app.decks().create(TEACHER, scope, "latin", DeckInput.of("new")))
                    .isNotNull();
        }
    }

    @Nested
    class AttachLogging {

        @Test
        void writesALineWhenACellIsSuspended() {
            Application app = build().app();
            List<String> lines = new ArrayList<>();
            app.attachLogging(lines::add);
            answerFirst(
                    app,
                    "nope",
                    DrillSettings.builder()
                            .ladder(FLAT)
                            .leech(LeechPolicy.builder().threshold(1).build())
                            .build());
            assertThat(lines).anyMatch(line -> line.startsWith("suspended "));
        }

        @Test
        void writesALineWhenASessionCloses() {
            Application app = build().app();
            List<String> lines = new ArrayList<>();
            app.attachLogging(lines::add);
            AnswerOutcome outcome = answerFirst(app, "x");
            app.drill().finish("lrn_one", outcome.session().id());
            assertThat(lines).anyMatch(line -> line.contains("closed with 1 answers"));
        }

        @Test
        void stopsWritingOnceDetached() {
            Application app = build().app();
            List<String> lines = new ArrayList<>();
            Subscription detach = app.attachLogging(lines::add);
            detach.unsubscribe();
            AnswerOutcome outcome = answerFirst(app, "x");
            app.drill().finish("lrn_one", outcome.session().id());
            assertThat(lines).isEmpty();
        }
    }
}
