package io.github.rkaushikdev.stemwright.domain.paradigm;

import io.github.rkaushikdev.stemwright.domain.Grouping;
import io.github.rkaushikdev.stemwright.domain.IdKind;
import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.features.FeatureBundle;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.text.Collation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A cell is what gets drilled: this lexeme in this bundle. It is derived, not stored as an
 * editorial object, so a lexicon edit that adds a slot to a profile grows the cell set of every
 * lexeme in that class at once.
 *
 * <p>The id is derived from the lexeme id and the canonical bundle key, so the same cell keeps the
 * same id across a re expansion. Mastery is keyed on that id, and an allocated id would silently
 * reset every learner's history the first time a profile was reloaded.
 *
 * @param id the derived cell id, see {@link #idFor}
 * @param lexemeId the id of the lexeme
 * @param bundle the canonical bundle key
 * @param profileKey the key of the profile the cell was expanded from
 */
public record Cell(String id, String lexemeId, String bundle, String profileKey) {

    /** {@link #compare} as a comparator: by lexeme id, then by bundle. */
    public static final Comparator<Cell> ORDER = Cell::compare;

    public static String idFor(String lexemeId, String bundle) {
        return Identifiers.derivedId(IdKind.CELL, lexemeId, bundle);
    }

    public static Cell of(String lexemeId, String bundle, String profileKey) {
        return new Cell(idFor(lexemeId, bundle), lexemeId, bundle, profileKey);
    }

    /**
     * Every cell the profile defines for a lexeme, in slot order, skipping the bundles the lexeme's
     * inherent features rule out. A masculine noun has no feminine genitive, and offering one as a
     * drill question is offering a question with no right answer.
     */
    public static List<Cell> expand(FeatureSchema schema, Lexeme entry, ParadigmProfile profile) {
        List<Cell> cells = new ArrayList<>();
        for (String slotKey : profile.slots().keySet()) {
            FeatureBundle bundle = FeatureBundle.parseKey(schema, slotKey);
            if (entry.contradictsInherent(bundle)) {
                continue;
            }
            cells.add(of(entry.id(), slotKey, profile.key()));
        }
        return cells;
    }

    /** The slot bundles {@link #expand} leaves out for the lexeme, in slot order. */
    public static List<String> skippedBundles(FeatureSchema schema, Lexeme entry, ParadigmProfile profile) {
        return profile.slots().keySet().stream()
                .filter(slotKey -> entry.contradictsInherent(FeatureBundle.parseKey(schema, slotKey)))
                .toList();
    }

    /**
     * True when both are the same lexeme in the same bundle. The profile they were expanded from
     * does not enter into it, which is what record equality would get wrong.
     */
    public boolean sameAs(Cell other) {
        return lexemeId.equals(other.lexemeId) && bundle.equals(other.bundle);
    }

    /** Orders by lexeme id, then by bundle, both as a reader sorts text. */
    public static int compare(Cell a, Cell b) {
        int byLexeme = Collation.compare(a.lexemeId, b.lexemeId);
        return byLexeme != 0 ? byLexeme : Collation.compare(a.bundle, b.bundle);
    }

    /** The cells of one lexeme, in the order given. */
    public static List<Cell> cellsOfLexeme(Collection<Cell> cells, String lexemeId) {
        return cells.stream()
                .filter(candidate -> candidate.lexemeId.equals(lexemeId))
                .toList();
    }

    /** The cells in one bundle, in the order given. */
    public static List<Cell> cellsInBundle(Collection<Cell> cells, FeatureBundle bundle) {
        String key = bundle.key();
        return cells.stream().filter(candidate -> candidate.bundle.equals(key)).toList();
    }

    /** The cells by id, in the order given, a later cell winning a repeated id. */
    public static Map<String, Cell> index(Collection<Cell> cells) {
        return Collections.unmodifiableMap(Grouping.indexBy(cells, Cell::id));
    }
}
