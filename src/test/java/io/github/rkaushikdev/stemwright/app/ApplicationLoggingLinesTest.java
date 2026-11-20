package io.github.rkaushikdev.stemwright.app;

import static io.github.rkaushikdev.stemwright.support.App.NOW;
import static io.github.rkaushikdev.stemwright.support.App.TODAY;
import static io.github.rkaushikdev.stemwright.support.App.seededStores;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The log lines are output a deployment's operators read and grep, so their text is part of the
 * contract. These pin all three byte for byte, and check that the other kinds write nothing.
 */
class ApplicationLoggingLinesTest {

    private final List<String> lines = new ArrayList<>();
    private Application app;

    @BeforeEach
    void attach() {
        app = Application.create(seededStores(), ApplicationOptions.DEFAULT.withClock(Clock.fixed(NOW, TODAY)));
        app.attachLogging(lines::add);
    }

    @Test
    void writesEachLineByteForByte() {
        app.bus().publish(new DomainEvent.CellSuspended(NOW, "lrn_one", "lrn_one", "cell_aaaaaa", 4));
        app.bus()
                .publish(new DomainEvent.ProfilePublished(
                        NOW, "lrn_teacher", "first-declension", "rev_bbbbbb", TODAY.plusDays(7)));
        app.bus().publish(new DomainEvent.SessionClosed(NOW, "lrn_one", "sess_cccccc", "lrn_one", 12));

        assertThat(lines)
                .containsExactly(
                        "suspended cell_aaaaaa for lrn_one after 4 lapses",
                        "published first-declension revision rev_bbbbbb from 2024-10-28",
                        "session sess_cccccc closed with 12 answers");
    }

    @Test
    void writesNothingForTheOtherKinds() {
        app.bus().publish(new DomainEvent.LexemeAdded(NOW, "lrn_teacher", "lex_dddddd", "latin"));
        app.bus().publish(new DomainEvent.AssignmentMet(NOW, "lrn_teacher", "week-3", "lrn_one", TODAY));

        assertThat(lines).isEmpty();
    }
}
