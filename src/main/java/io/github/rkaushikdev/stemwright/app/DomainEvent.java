package io.github.rkaushikdev.stemwright.app;

import io.github.rkaushikdev.stemwright.domain.mastery.Attempt;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Something that happened, published on an {@link EventBus} after the write it reports has landed
 * rather than instead of one.
 *
 * <p>Nothing in the domain depends on the bus: a subscriber that throws must not be able to undo a
 * review that was already recorded, so publication is best effort and failures are collected rather
 * than propagated.
 *
 * <p>There is one record per {@link EventKind}, and the interface is sealed over them, so a {@code
 * switch} over an event is checked for exhaustiveness and a subscriber to one kind receives that
 * record rather than something it has to cast. Every event says when it happened and which account
 * caused it.
 */
public sealed interface DomainEvent
        permits DomainEvent.LexemeAdded,
                DomainEvent.ProfilePublished,
                DomainEvent.AttemptRecorded,
                DomainEvent.CellSuspended,
                DomainEvent.SessionClosed,
                DomainEvent.AssignmentMet {

    /** When the write the event reports landed. */
    Instant at();

    /** The account the write was made by or on behalf of. */
    String actorId();

    /** Which of the six kinds this is, the same for every event of one record type. */
    EventKind kind();

    /** Reads the event out in one line, such as {@code lrn_teacher added lex_aaaaaa to latin}. */
    String describe();

    /**
     * A lexeme was added to a language's lexicon.
     *
     * @param at when it was added
     * @param actorId the editor who added it
     * @param lexemeId the id of the new lexeme
     * @param language the key of the language it was added to
     */
    record LexemeAdded(Instant at, String actorId, String lexemeId, String language) implements DomainEvent {

        public LexemeAdded {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(lexemeId, "lexemeId");
            Objects.requireNonNull(language, "language");
        }

        @Override
        public EventKind kind() {
            return EventKind.LEXEME_ADDED;
        }

        @Override
        public String describe() {
            return actorId + " added " + lexemeId + " to " + language;
        }
    }

    /**
     * A revision of a paradigm profile was published.
     *
     * @param at when it was published
     * @param actorId the editor who published it
     * @param profileKey the key of the profile
     * @param revisionId the id of the revision now in the profile's history
     * @param effectiveFrom the first study day the revision is in force
     */
    record ProfilePublished(Instant at, String actorId, String profileKey, String revisionId, LocalDate effectiveFrom)
            implements DomainEvent {

        public ProfilePublished {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(profileKey, "profileKey");
            Objects.requireNonNull(revisionId, "revisionId");
            Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        }

        @Override
        public EventKind kind() {
            return EventKind.PROFILE_PUBLISHED;
        }

        @Override
        public String describe() {
            return actorId + " published " + profileKey + " from " + effectiveFrom;
        }
    }

    /**
     * A learner's answer was marked and recorded.
     *
     * @param at when it was recorded
     * @param actorId the learner who answered
     * @param attempt the attempt as it was recorded
     */
    record AttemptRecorded(Instant at, String actorId, Attempt attempt) implements DomainEvent {

        public AttemptRecorded {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(attempt, "attempt");
        }

        @Override
        public EventKind kind() {
            return EventKind.ATTEMPT_RECORDED;
        }

        /** Names the learner, the cell and the verdict as written, such as {@code exact}. */
        @Override
        public String describe() {
            return attempt.learnerId() + " answered " + attempt.cellId() + " "
                    + attempt.verdict().key();
        }
    }

    /**
     * A cell lapsed often enough to be suspended as a leech.
     *
     * @param at when it was suspended
     * @param actorId the account whose answer suspended it
     * @param learnerId the learner it was suspended for
     * @param cellId the cell
     * @param lapses how many lapses it had when it was suspended
     */
    record CellSuspended(Instant at, String actorId, String learnerId, String cellId, int lapses)
            implements DomainEvent {

        public CellSuspended {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(learnerId, "learnerId");
            Objects.requireNonNull(cellId, "cellId");
        }

        @Override
        public EventKind kind() {
            return EventKind.CELL_SUSPENDED;
        }

        @Override
        public String describe() {
            return cellId + " was suspended for " + learnerId + " after " + lapses + " lapses";
        }
    }

    /**
     * A drill session was closed.
     *
     * @param at when it was closed
     * @param actorId the account that closed it
     * @param sessionId the session
     * @param learnerId the learner whose session it was
     * @param answers how many answers the session holds
     */
    record SessionClosed(Instant at, String actorId, String sessionId, String learnerId, int answers)
            implements DomainEvent {

        public SessionClosed {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(learnerId, "learnerId");
        }

        @Override
        public EventKind kind() {
            return EventKind.SESSION_CLOSED;
        }

        @Override
        public String describe() {
            return learnerId + " closed a session of " + answers + " answers";
        }
    }

    /**
     * A learner's progress was found to meet an assignment's target.
     *
     * @param at when it was found
     * @param actorId the account that asked for the progress
     * @param assignmentKey the key of the assignment
     * @param learnerId the learner who met it
     * @param on the study day the progress was measured on
     */
    record AssignmentMet(Instant at, String actorId, String assignmentKey, String learnerId, LocalDate on)
            implements DomainEvent {

        public AssignmentMet {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(assignmentKey, "assignmentKey");
            Objects.requireNonNull(learnerId, "learnerId");
            Objects.requireNonNull(on, "on");
        }

        @Override
        public EventKind kind() {
            return EventKind.ASSIGNMENT_MET;
        }

        @Override
        public String describe() {
            return learnerId + " met " + assignmentKey + " on " + on;
        }
    }
}
