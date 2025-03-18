import { ForbiddenError } from '../domain/errors.js';
import type { AccountRole, Actor } from '../domain/course/authz.js';
import { isAccountRole } from '../domain/course/authz.js';
import type { Handler, HttpRequest, HttpResponse } from './router.js';

/**
 * Working out who is asking. The directory is a port: this build reads a map,
 * a deployment reads whatever the institution already has, and neither the
 * routes nor the services can tell the difference.
 *
 * A token that resolves to nothing is a 403 rather than a 401, because every
 * route here needs an actor and telling an unauthenticated caller which routes
 * exist is a small leak nobody needs.
 */
export interface AccountDirectory {
  resolve(token: string): Promise<Actor | undefined>;
}

export interface AuthedRequest extends HttpRequest {
  readonly actor: Actor;
}

export type AuthedHandler = (request: AuthedRequest) => Promise<HttpResponse>;

export function memoryDirectory(
  accounts: Readonly<Record<string, { id: string; role: AccountRole }>>,
): AccountDirectory {
  return {
    resolve(token) {
      const found = accounts[token];
      if (found === undefined || !isAccountRole(found.role)) {
        return Promise.resolve(undefined);
      }
      return Promise.resolve({ id: found.id, role: found.role });
    },
  };
}

/**
 * Reads a bearer token out of the authorisation header. The scheme is matched
 * without regard to case because clients disagree about it, and the token is
 * not trimmed beyond the single separating space, because a token with spaces
 * in it is a token this build will simply fail to resolve.
 */
export function bearerToken(headers: Readonly<Record<string, string>>): string | undefined {
  const header = headers['authorization'] ?? headers['Authorization'];
  if (header === undefined) {
    return undefined;
  }
  const space = header.indexOf(' ');
  if (space < 0) {
    return undefined;
  }
  if (header.slice(0, space).toLowerCase() !== 'bearer') {
    return undefined;
  }
  const token = header.slice(space + 1).trim();
  return token === '' ? undefined : token;
}

export function withActor(directory: AccountDirectory, handler: AuthedHandler): Handler {
  return async (request) => {
    const token = bearerToken(request.headers);
    if (token === undefined) {
      throw new ForbiddenError('no bearer token was presented');
    }
    const actor = await directory.resolve(token);
    if (actor === undefined) {
      throw new ForbiddenError('the token presented does not name an account');
    }
    return handler({ ...request, actor });
  };
}

/**
 * A handler that only an actor of one of the given roles may reach. It is a
 * coarse check on top of the fine ones the services do, and it exists so a
 * route that should never be reachable by a learner cannot become reachable by
 * a service level bug.
 */
export function requireRole(roles: readonly AccountRole[], handler: AuthedHandler): AuthedHandler {
  return (request) => {
    if (!roles.includes(request.actor.role)) {
      throw new ForbiddenError(`${request.actor.id} may not reach ${request.path}`);
    }
    return handler(request);
  };
}

export function actorOf(request: AuthedRequest): Actor {
  return request.actor;
}
