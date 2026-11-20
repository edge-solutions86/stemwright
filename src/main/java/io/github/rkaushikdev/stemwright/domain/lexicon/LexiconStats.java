package io.github.rkaushikdev.stemwright.domain.lexicon;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The counts a lexicon health report opens with.
 *
 * @param total how many lexemes the lexicon holds
 * @param byPartOfSpeech how many lexemes of each part of speech, keyed by the {@link
 *     PartOfSpeech#key()}, or {@code unknown} for a lexeme whose class the registry lacks, in the
 *     order each was first met
 * @param classesUsed how many distinct classes the lexemes follow
 * @param untagged how many lexemes carry no tag
 * @param withoutGloss how many lexemes have no gloss
 */
public record LexiconStats(
        int total, Map<String, Integer> byPartOfSpeech, int classesUsed, int untagged, int withoutGloss) {

    public LexiconStats {
        byPartOfSpeech = Collections.unmodifiableMap(new LinkedHashMap<>(byPartOfSpeech));
    }
}
