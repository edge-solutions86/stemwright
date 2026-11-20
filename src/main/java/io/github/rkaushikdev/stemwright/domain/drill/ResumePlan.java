package io.github.rkaushikdev.stemwright.domain.drill;

import io.github.rkaushikdev.stemwright.domain.text.Collation;
import io.github.rkaushikdev.stemwright.domain.time.CivilDates;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What to do with a session that is already open when a learner comes back.
 *
 * <p>The answer depends on the day, because burial is a session scoped fact. A session left open
 * from yesterday is still carrying yesterday's buried twins, and resuming into it would hide cells
 * that are due today for a reason that stopped applying at midnight.
 *
 * <p>A session on the same day is resumed even if hours have passed, because the learner closing a
 * laptop for lunch has not stopped being in the middle of the same sitting, and the burial is still
 * doing the job it was meant to.
 *
 * @param action what to do
 * @param reason why, as the learner is told it
 * @param staleFor how many days old the open session is, negative for one dated after today
 * @param answered how many answers the open session holds
 * @param unresolved the cells the open session left wrong, in natural string order
 */
public record ResumePlan(ResumeAction action, String reason, int staleFor, int answered, List<String> unresolved) {

    /** The most answers first, then the session id as a reader sorts it. */
    private static final Comparator<Session> MOST_WORKED_IN =
            Comparator.comparingInt(Session::length).reversed().thenComparing(Session::id, Collation.ORDER);

    public ResumePlan {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(reason, "reason");
        unresolved = List.copyOf(unresolved);
    }

    /** The plan for the open session, or for opening a new one when there is none. */
    public static ResumePlan of(Optional<Session> session, LocalDate on) {
        return session.map(open -> of(open, on))
                .orElseGet(() -> new ResumePlan(ResumeAction.OPEN, "there is no session open", 0, 0, List.of()));
    }

    /**
     * The plan for the session: resume it when it is open and from today, close it when it is open
     * and from any other day, and open a new one when it is already closed.
     */
    public static ResumePlan of(Session session, LocalDate on) {
        int answered = session.length();
        List<String> unresolved = session.unresolvedCells();

        if (session.closed()) {
            return new ResumePlan(ResumeAction.OPEN, "the open session was already closed", 0, answered, unresolved);
        }

        int staleFor = CivilDates.daysBetween(session.on(), on);
        if (staleFor == 0) {
            return new ResumePlan(
                    ResumeAction.RESUME,
                    "resuming a session of " + answered + " answers from today",
                    staleFor,
                    answered,
                    unresolved);
        }
        if (staleFor < 0) {
            return new ResumePlan(
                    ResumeAction.CLOSE_AND_OPEN,
                    "the open session is dated " + session.on() + ", which is after today",
                    staleFor,
                    answered,
                    unresolved);
        }
        return new ResumePlan(
                ResumeAction.CLOSE_AND_OPEN,
                "the open session is " + staleFor + " days old and its buried twins no longer apply",
                staleFor,
                answered,
                unresolved);
    }

    public boolean shouldResume() {
        return action == ResumeAction.RESUME;
    }

    public boolean needsClosing() {
        return action == ResumeAction.CLOSE_AND_OPEN;
    }

    /**
     * Picks the session to resume out of several open ones. The one for today and the deck wins, and
     * among several for today the one with the most answers, because that is the one the learner was
     * actually working in.
     */
    public static Optional<Session> pickResumable(List<Session> sessions, String deckKey, LocalDate on) {
        return sessions.stream()
                .filter(session -> !session.closed()
                        && session.deckKey().equals(deckKey)
                        && session.on().equals(on))
                .min(MOST_WORKED_IN);
    }

    /** Every open session not dated on the day, in the order given. */
    public static List<Session> staleSessions(List<Session> sessions, LocalDate on) {
        return sessions.stream()
                .filter(session -> !session.closed() && CivilDates.daysBetween(session.on(), on) != 0)
                .toList();
    }

    /** The reason, with the count of unresolved cells when there are any. */
    public String describe() {
        if (unresolved.isEmpty()) {
            return reason;
        }
        return reason + ", " + unresolved.size() + " cells left unresolved";
    }
}
