package io.github.rkaushikdev.stemwright.domain.lexicon;

import java.util.List;
import java.util.stream.Collectors;

/**
 * What happened to one lexeme between two versions of a lexicon.
 *
 * @param kind whether the lexeme was added, removed or changed
 * @param lexemeId the id of the lexeme
 * @param lemma the lemma, from the later version when there is one
 * @param fields the fields that differ, in field name order; empty for an addition or a removal
 */
public record LexemeChange(ChangeKind kind, String lexemeId, String lemma, List<FieldChange> fields) {

    private static final String NOTHING = "nothing";

    public LexemeChange {
        fields = List.copyOf(fields);
    }

    /** The change as one line for an editor, such as {@code changed puella: class from a to b}. */
    public String describe() {
        return switch (kind) {
            case ADDED -> "added " + lemma;
            case REMOVED -> "removed " + lemma;
            case CHANGED ->
                "changed " + lemma + ": "
                        + fields.stream().map(LexemeChange::describeField).collect(Collectors.joining(", "));
        };
    }

    private static String describeField(FieldChange field) {
        return field.field() + " from " + orNothing(field.before()) + " to " + orNothing(field.after());
    }

    private static String orNothing(String value) {
        return value.isEmpty() ? NOTHING : value;
    }
}
