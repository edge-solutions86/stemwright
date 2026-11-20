package io.github.rkaushikdev.stemwright.app;

import io.github.rkaushikdev.stemwright.domain.paradigm.ProfileBook;
import java.util.Optional;

/** The profile book of each language, keyed by language key. */
public interface BookStore {

    Optional<ProfileBook> get(String language);

    /** Stores the book under its language, in place of the book stored there before. */
    void put(ProfileBook book);
}
