package io.github.rkaushikdev.stemwright.domain.mastery;

/**
 * What happens to a leech. It is a teaching decision rather than a technical one, which is why
 * there are three actions and none of them is the obvious default.
 */
public enum LeechAction {
    /** Takes the cell out of the queue. Right for a word nobody needs yet. */
    SUSPEND,
    /** Sends the cell back to the bottom rung. Right when the learner met it too early. */
    DEMOTE,
    /**
     * Reports the cell and changes nothing about its schedule. Right when an instructor should look
     * at whether the paradigm itself is wrong.
     */
    TAG
}
