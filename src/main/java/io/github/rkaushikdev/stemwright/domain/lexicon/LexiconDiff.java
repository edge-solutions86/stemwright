package io.github.rkaushikdev.stemwright.domain.lexicon;

import io.github.rkaushikdev.stemwright.domain.Grouping;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Comparing two versions of a lexicon, which is what an editor wants before accepting an import and
 * what a reviewer wants before approving one.
 *
 * <p>A change is reported at the field that changed rather than as a whole record, because the
 * interesting question is never "did this lexeme change" but "did the class change", which moves
 * every learner's schedule for it, or "did a gloss change", which moves nothing.
 */
public final class LexiconDiff {

    /** The fields whose change moves a learner's schedule rather than only their reading. */
    public static final List<String> SCHEDULE_FIELDS = List.of("class", "inherent");

    private LexiconDiff() {}

    /**
     * Every lexeme added or changed, in {@link Lexeme#ORDER} of the later version, then every lexeme
     * removed, in {@link Lexeme#ORDER} of the earlier one. Lexemes are matched by id, so a lemma
     * edit that keeps the id is a change and one that moves it is a removal and an addition.
     */
    public static List<LexemeChange> between(Collection<Lexeme> before, Collection<Lexeme> after) {
        Map<String, Lexeme> left = Grouping.indexBy(before, Lexeme::id);
        Map<String, Lexeme> right = Grouping.indexBy(after, Lexeme::id);
        List<LexemeChange> changes = new ArrayList<>();

        for (Lexeme entry : after.stream().sorted(Lexeme.ORDER).toList()) {
            Lexeme previous = left.get(entry.id());
            if (previous == null) {
                changes.add(new LexemeChange(ChangeKind.ADDED, entry.id(), entry.lemma(), List.of()));
                continue;
            }
            List<FieldChange> fields = changesBetween(previous, entry);
            if (!fields.isEmpty()) {
                changes.add(new LexemeChange(ChangeKind.CHANGED, entry.id(), entry.lemma(), fields));
            }
        }

        for (Lexeme entry : before.stream().sorted(Lexeme.ORDER).toList()) {
            if (!right.containsKey(entry.id())) {
                changes.add(new LexemeChange(ChangeKind.REMOVED, entry.id(), entry.lemma(), List.of()));
            }
        }

        return changes;
    }

    private static Map<String, String> fieldsOf(Lexeme entry) {
        Map<String, String> fields = new HashMap<>();
        fields.put("lemma", entry.lemma());
        fields.put("class", entry.classKey());
        fields.put("inherent", entry.inherentKey());
        fields.put("glosses", String.join("; ", entry.glosses()));
        fields.put("tags", String.join("; ", entry.tags().stream().sorted().toList()));
        fields.put("note", entry.note().orElse(""));
        for (Stem stem : entry.stems().stems()) {
            fields.putIfAbsent("stem." + stem.role(), stem.form());
        }
        return fields;
    }

    private static List<FieldChange> changesBetween(Lexeme before, Lexeme after) {
        Map<String, String> left = fieldsOf(before);
        Map<String, String> right = fieldsOf(after);
        Set<String> names = new TreeSet<>(left.keySet());
        names.addAll(right.keySet());

        List<FieldChange> changes = new ArrayList<>();
        for (String field : names) {
            String was = left.getOrDefault(field, "");
            String now = right.getOrDefault(field, "");
            if (!was.equals(now)) {
                changes.add(new FieldChange(field, was, now));
            }
        }
        return changes;
    }

    public static List<LexemeChange> ofKind(Collection<LexemeChange> changes, ChangeKind kind) {
        return changes.stream().filter(change -> change.kind() == kind).toList();
    }

    /**
     * The changes that move a learner's schedule: every addition and removal, and every change that
     * touches one of the {@link #SCHEDULE_FIELDS}.
     */
    public static List<LexemeChange> schedulingChanges(Collection<LexemeChange> changes) {
        return changes.stream()
                .filter(change -> change.kind() != ChangeKind.CHANGED
                        || change.fields().stream().anyMatch(field -> SCHEDULE_FIELDS.contains(field.field())))
                .toList();
    }

    /** One line counting each kind of change and how many of them move a schedule. */
    public static String summarise(Collection<LexemeChange> changes) {
        int added = ofKind(changes, ChangeKind.ADDED).size();
        int removed = ofKind(changes, ChangeKind.REMOVED).size();
        int changed = ofKind(changes, ChangeKind.CHANGED).size();
        if (added + removed + changed == 0) {
            return "nothing changed";
        }
        return added + " added, " + removed + " removed, " + changed + " changed, "
                + schedulingChanges(changes).size() + " of which move a schedule";
    }
}
