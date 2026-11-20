package io.github.rkaushikdev.stemwright.cli;

import java.util.Objects;

/** A flag's value once it has been read as its {@link FlagKind}. */
public sealed interface FlagValue permits FlagValue.StringValue, FlagValue.NumberValue, FlagValue.BooleanValue {

    /** The value of a {@link FlagKind#STRING} flag. */
    record StringValue(String value) implements FlagValue {

        public StringValue {
            Objects.requireNonNull(value, "value");
        }
    }

    /** The value of a {@link FlagKind#NUMBER} flag, always finite. */
    record NumberValue(double value) implements FlagValue {}

    /** The value of a {@link FlagKind#BOOLEAN} flag. */
    record BooleanValue(boolean value) implements FlagValue {}
}
