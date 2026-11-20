package io.github.rkaushikdev.stemwright.cli;

/** What a flag's value is read as. */
public enum FlagKind {
    /** The text as typed. */
    STRING,
    /** A flag that is on when named, or set by {@code --name=true} or {@code --name=false}. */
    BOOLEAN,
    /** A finite number, read as JavaScript's {@code Number} reads one. */
    NUMBER
}
