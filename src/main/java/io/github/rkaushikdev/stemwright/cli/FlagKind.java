package io.github.rkaushikdev.stemwright.cli;

/** What a flag's value is read as. */
public enum FlagKind {
    /** The text as typed. */
    STRING,
    /** A flag that is on when named, or set by {@code --name=true} or {@code --name=false}. */
    BOOLEAN,
    /**
     * A finite number, read leniently: spacing around it is ignored, blank is zero, and a {@code
     * 0x}, {@code 0o} or {@code 0b} prefix reads an integer in that base.
     */
    NUMBER
}
