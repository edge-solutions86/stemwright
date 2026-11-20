package io.github.rkaushikdev.stemwright.domain.errors;

/**
 * The code every stemwright failure carries across a module boundary.
 *
 * <p>The code is part of the contract: the http layer maps it to a status, the cli maps it to an
 * exit code, and the journal stores it verbatim when an import run records why a row was rejected.
 * Renaming a code is a breaking change, so codes are screaming snake case and never reused for a
 * new meaning.
 */
public enum ErrorCode {
    VALIDATION,
    NOT_FOUND,
    CONFLICT,
    POLICY,
    FORBIDDEN,
    INTEGRITY,
    UNSUPPORTED
}
