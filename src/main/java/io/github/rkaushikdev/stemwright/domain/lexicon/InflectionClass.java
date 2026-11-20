package io.github.rkaushikdev.stemwright.domain.lexicon;

import java.util.Optional;

/**
 * An inflection class is the answer to "which paradigm does this word follow". Classes form a tree:
 * {@code o-stem} has {@code o-stem.contract} under it, and a contract noun is an o stem in every
 * respect the child does not override. {@link ClassRegistry} holds the tree and checks it.
 *
 * @param key the slug that names the class, such as {@code second-declension.neuter}
 * @param label the printed name, the key itself when none was given
 * @param partOfSpeech what every word of the class is, shared with the parent
 * @param parent the key of the class this one refines, empty for a root
 * @param note an editor's remark on the class
 */
public record InflectionClass(
        String key, String label, PartOfSpeech partOfSpeech, Optional<String> parent, Optional<String> note) {}
