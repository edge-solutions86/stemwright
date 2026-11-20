package io.github.rkaushikdev.stemwright.domain.paradigm;

import java.util.Optional;

/**
 * A join rule as an author writes it, before {@link RuleSet#of} checks it.
 *
 * @param key the rule key, which becomes a slug
 * @param stemEnd what the stem must end with
 * @param affixStart what the affix must start with
 * @param becomes what the two matched parts are replaced by
 * @param note an editor's remark on the rule
 */
public record JoinRuleInput(String key, String stemEnd, String affixStart, String becomes, Optional<String> note) {

    /** A rule with no note. */
    public static JoinRuleInput of(String key, String stemEnd, String affixStart, String becomes) {
        return new JoinRuleInput(key, stemEnd, affixStart, becomes, Optional.empty());
    }

    /** A copy carrying the note. */
    public JoinRuleInput withNote(String note) {
        return new JoinRuleInput(key, stemEnd, affixStart, becomes, Optional.of(note));
    }
}
