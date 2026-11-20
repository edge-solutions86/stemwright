package io.github.rkaushikdev.stemwright.support;

import io.github.rkaushikdev.stemwright.domain.ortho.OrthoProfile;
import io.github.rkaushikdev.stemwright.domain.ortho.OrthoProfileInput;
import io.github.rkaushikdev.stemwright.domain.ortho.OrthoProfileInput.Level;
import java.util.List;

/** The orthography profiles the tests share. */
public final class Ortho {

    private Ortho() {}

    /** Four levels, chosen so one pair is comparable and one pair is not. */
    public static final OrthoProfile GREEK = OrthoProfile.of(OrthoProfileInput.of(
            "greek",
            "standard",
            Level.of("exact", "Exact", List.of()),
            Level.of("standard", "Standard", List.of("lowercase", "final-forms")),
            Level.of("lenient", "Lenient", List.of("lowercase", "final-forms", "marks")),
            Level.of("unaccented", "Unaccented", List.of("marks", "punctuation"))));

    public static final OrthoProfile LATIN = OrthoProfile.of(OrthoProfileInput.of(
            "latin",
            "standard",
            Level.of("exact", List.of()),
            Level.of("standard", List.of("lowercase", "length-marks"))));
}
