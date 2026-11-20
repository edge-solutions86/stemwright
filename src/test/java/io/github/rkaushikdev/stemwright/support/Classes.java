package io.github.rkaushikdev.stemwright.support;

import static io.github.rkaushikdev.stemwright.domain.lexicon.PartOfSpeech.NOUN;
import static io.github.rkaushikdev.stemwright.domain.lexicon.PartOfSpeech.VERB;

import io.github.rkaushikdev.stemwright.domain.lexicon.ClassRegistry;
import io.github.rkaushikdev.stemwright.domain.lexicon.InflectionClassInput;
import java.util.List;

/** The class registries the unit tests share. */
public final class Classes {

    private Classes() {}

    /**
     * A small Latin class tree with two levels: three declensions, the second and third with
     * children, and two conjugations, the third with a child. Shared by the lexicon tests.
     */
    public static final ClassRegistry LATIN = ClassRegistry.of(
            "latin",
            List.of(
                    InflectionClassInput.builder("first-declension", NOUN)
                            .label("First declension")
                            .build(),
                    InflectionClassInput.builder("second-declension", NOUN)
                            .label("Second declension")
                            .build(),
                    InflectionClassInput.builder("second-declension.neuter", NOUN)
                            .label("Second declension neuter")
                            .parent("second-declension")
                            .build(),
                    InflectionClassInput.builder("second-declension.r-stem", NOUN)
                            .label("Second declension in r")
                            .parent("second-declension")
                            .note("ager and puer keep the r of the nominative through the paradigm")
                            .build(),
                    InflectionClassInput.builder("third-declension", NOUN)
                            .label("Third declension")
                            .build(),
                    InflectionClassInput.builder("third-declension.i-stem", NOUN)
                            .label("Third declension i stem")
                            .parent("third-declension")
                            .build(),
                    InflectionClassInput.builder("first-conjugation", VERB)
                            .label("First conjugation")
                            .build(),
                    InflectionClassInput.builder("third-conjugation", VERB)
                            .label("Third conjugation")
                            .build(),
                    InflectionClassInput.builder("third-conjugation.io", VERB)
                            .label("Third conjugation in io")
                            .parent("third-conjugation")
                            .build()));
}
