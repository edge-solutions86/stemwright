package io.github.rkaushikdev.stemwright.http;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * What the assembled api is built with besides the application: who each bearer token belongs to,
 * and three things a deployment may leave out.
 *
 * @param directory who each bearer token belongs to
 * @param version the version the health route reports, {@code unknown} when absent
 * @param onError told of every failure a route throws that the domain did not raise, which the
 *     client only sees as a bare 500; nothing is told when absent
 * @param readiness answers whether the process can serve, which the readiness route reports; the
 *     process always can when absent
 */
public record ApiOptions(
        AccountDirectory directory,
        Optional<String> version,
        Optional<Consumer<? super RuntimeException>> onError,
        Optional<BooleanSupplier> readiness) {

    public ApiOptions {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(onError, "onError");
        Objects.requireNonNull(readiness, "readiness");
    }

    /** Options over the directory that leave everything else out. */
    public static ApiOptions of(AccountDirectory directory) {
        return builder(directory).build();
    }

    public static Builder builder(AccountDirectory directory) {
        return new Builder(directory);
    }

    /** Names the optional parts one at a time; any left unnamed stay absent. */
    public static final class Builder {

        private final AccountDirectory directory;
        private Optional<String> version = Optional.empty();
        private Optional<Consumer<? super RuntimeException>> onError = Optional.empty();
        private Optional<BooleanSupplier> readiness = Optional.empty();

        private Builder(AccountDirectory directory) {
            this.directory = Objects.requireNonNull(directory, "directory");
        }

        public Builder version(String value) {
            version = Optional.of(value);
            return this;
        }

        public Builder onError(Consumer<? super RuntimeException> reporter) {
            onError = Optional.of(reporter);
            return this;
        }

        public Builder readiness(BooleanSupplier check) {
            readiness = Optional.of(check);
            return this;
        }

        public ApiOptions build() {
            return new ApiOptions(directory, version, onError, readiness);
        }
    }
}
