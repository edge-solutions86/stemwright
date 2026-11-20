package io.github.rkaushikdev.stemwright.bin;

import io.github.rkaushikdev.stemwright.http.AccountDirectory.Account;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What the server entry point reads from its environment.
 *
 * @param port the port to listen on, from 0 to 65535, 0 asking the system for a free one
 * @param version the version the health route reports
 * @param accounts the accounts by bearer token, unmodifiable and in the order they were given
 */
public record ServeConfig(int port, String version, Map<String, Account> accounts) {

    public ServeConfig {
        Objects.requireNonNull(version, "version");
        accounts = Collections.unmodifiableMap(new LinkedHashMap<>(accounts));
    }
}
