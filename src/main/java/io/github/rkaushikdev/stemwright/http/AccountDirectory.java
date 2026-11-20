package io.github.rkaushikdev.stemwright.http;

import io.github.rkaushikdev.stemwright.domain.course.AccountRole;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Who a bearer token belongs to. The directory is a port: this build reads a map, a deployment reads
 * whatever the institution already has, and neither the routes nor the services can tell the
 * difference.
 */
@FunctionalInterface
public interface AccountDirectory {

    /** The actor the token names, or nothing when it names no account this desk can act for. */
    Optional<Actor> resolve(String token);

    /**
     * A directory over a fixed map of tokens to accounts. An account whose role is not one of the
     * three {@link AccountRole}s resolves to nothing, as an unknown token does, rather than to an
     * actor nobody can say the permissions of.
     */
    static AccountDirectory inMemory(Map<String, Account> accounts) {
        Map<String, Account> held = Map.copyOf(accounts);
        return token -> Optional.ofNullable(held.get(token))
                .flatMap(account -> AccountRole.fromKey(account.role()).map(role -> new Actor(account.id(), role)));
    }

    /**
     * An account as a directory holds it.
     *
     * @param id the account id
     * @param role the account role as the directory writes it, such as {@code instructor}
     */
    record Account(String id, String role) {

        public Account {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(role, "role");
        }
    }
}
