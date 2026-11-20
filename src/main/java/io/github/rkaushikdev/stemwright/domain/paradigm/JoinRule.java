package io.github.rkaushikdev.stemwright.domain.paradigm;

import java.util.Optional;

/**
 * One rewrite at the seam between a stem and an affix, such as Latin {@code g} before {@code s}
 * becoming {@code x}. Either side may be empty, but not both.
 *
 * @param key the rule key, named in the trace
 * @param stemEnd what the stem must end with, normalised
 * @param affixStart what the affix must start with, normalised
 * @param becomes what the two matched parts are replaced by, normalised
 * @param note an editor's remark on the rule
 */
public record JoinRule(String key, String stemEnd, String affixStart, String becomes, Optional<String> note) {}
