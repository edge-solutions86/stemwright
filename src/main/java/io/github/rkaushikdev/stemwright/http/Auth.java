package io.github.rkaushikdev.stemwright.http;

import io.github.rkaushikdev.stemwright.domain.course.AccountRole;
import io.github.rkaushikdev.stemwright.domain.course.Actor;
import io.github.rkaushikdev.stemwright.domain.errors.ForbiddenException;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Working out who is asking, from the bearer token a request carries and an {@link
 * AccountDirectory}.
 *
 * <p>A token that resolves to nothing is a 403 rather than a 401, because every route here needs
 * an actor and telling an unauthenticated caller which routes exist is a small leak nobody needs.
 */
public final class Auth {

    private Auth() {}

    /**
     * Reads a bearer token out of the {@code authorization} header, or the {@code Authorization} one
     * when there is no lower case header. The scheme is matched without regard to case because
     * clients disagree about it, and the token is only trimmed of spacing at its ends, so a token
     * with spaces inside it is a token this build will simply fail to resolve.
     *
     * @return the token, or nothing for a missing header, one with no space, another scheme, or an
     *     empty token
     */
    public static Optional<String> bearerToken(Map<String, String> headers) {
        return Optional.ofNullable(headers.get("authorization"))
                .or(() -> Optional.ofNullable(headers.get("Authorization")))
                .flatMap(Auth::tokenIn);
    }

    private static Optional<String> tokenIn(String header) {
        int space = header.indexOf(' ');
        if (space < 0 || !Text.lower(header.substring(0, space)).equals("bearer")) {
            return Optional.empty();
        }
        String token = Text.trim(header.substring(space + 1));
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    /**
     * Wraps a handler so it is only reached with the actor the request's bearer token names.
     *
     * @return a handler that throws {@link ForbiddenException} for a request with no bearer token,
     *     or one whose token the directory does not resolve
     */
    public static Handler withActor(AccountDirectory directory, AuthedHandler handler) {
        return request -> {
            String token = bearerToken(request.headers())
                    .orElseThrow(() -> new ForbiddenException("no bearer token was presented"));
            Actor actor = directory
                    .resolve(token)
                    .orElseThrow(() -> new ForbiddenException("the token presented does not name an account"));
            return handler.handle(new AuthedRequest(request, actor));
        };
    }

    /**
     * A handler that only an actor of one of the given roles may reach, and that nobody may reach
     * when no role is given. It is a coarse check on top of the fine ones the services do, and it
     * exists so a route that should never be reachable by a learner cannot become reachable by a
     * service level bug.
     *
     * @return a handler that throws {@link ForbiddenException}, naming the actor and the path, for
     *     an actor of any other role
     */
    public static AuthedHandler requireRole(Collection<AccountRole> roles, AuthedHandler handler) {
        Set<AccountRole> allowed = Set.copyOf(roles);
        return request -> {
            if (!allowed.contains(request.actor().role())) {
                throw new ForbiddenException(request.actor().id() + " may not reach " + request.path());
            }
            return handler.handle(request);
        };
    }
}
