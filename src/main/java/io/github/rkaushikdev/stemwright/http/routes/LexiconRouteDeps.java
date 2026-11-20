package io.github.rkaushikdev.stemwright.http.routes;

import io.github.rkaushikdev.stemwright.app.services.LexiconService;
import io.github.rkaushikdev.stemwright.http.AccountDirectory;
import java.util.Objects;

/**
 * What the lexicon routes are built with.
 *
 * @param directory who each bearer token belongs to
 * @param lexicon the service that reads and edits the lexicon
 */
public record LexiconRouteDeps(AccountDirectory directory, LexiconService lexicon) {

    public LexiconRouteDeps {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(lexicon, "lexicon");
    }
}
