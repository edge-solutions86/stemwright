package io.github.rkaushikdev.stemwright.app;

import java.util.List;
import java.util.Optional;

/** The configured languages, one {@link LanguageBundle} per language key. */
public interface LanguageStore {

    Optional<LanguageBundle> get(String language);

    /** Every configured language key, in natural string order. */
    List<String> list();

    /** Stores the bundle under its language key, in place of any bundle stored there before. */
    void put(LanguageBundle bundle);
}
