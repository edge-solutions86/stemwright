import type { Json } from '../infra/codec.js';

/**
 * A router small enough to read in one sitting. It exists because the http
 * surface here is a dozen routes over a domain that does all the thinking, and
 * a framework would be more code than the thing it wraps.
 *
 * Patterns are segments, and a segment beginning with a colon captures. There
 * is no wildcard and no optional segment: both are ways of writing two routes
 * as one, and two routes read better.
 */
export type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';

export interface HttpRequest {
  readonly method: Method;
  readonly path: string;
  readonly params: Readonly<Record<string, string>>;
  readonly query: Readonly<Record<string, string>>;
  readonly headers: Readonly<Record<string, string>>;
  readonly body: Json | undefined;
}

export interface HttpResponse {
  readonly status: number;
  readonly body: Json;
  readonly headers?: Readonly<Record<string, string>>;
}

export type Handler = (request: HttpRequest) => Promise<HttpResponse>;

export interface Route {
  readonly method: Method;
  readonly pattern: string;
  readonly handler: Handler;
}

export function route(method: Method, pattern: string, handler: Handler): Route {
  return { method, pattern, handler };
}

export function splitPath(path: string): string[] {
  return path.split('/').filter((segment) => segment !== '');
}

/**
 * Matches a path against a pattern and returns the captures, or nothing. A
 * capture is decoded, because a lemma in a path arrives percent encoded and
 * every caller would otherwise decode it again slightly differently.
 */
export function matchPath(pattern: string, path: string): Record<string, string> | undefined {
  const wanted = splitPath(pattern);
  const given = splitPath(path);
  if (wanted.length !== given.length) {
    return undefined;
  }

  const params: Record<string, string> = {};
  for (const [index, segment] of wanted.entries()) {
    const actual = given[index]!;
    if (segment.startsWith(':')) {
      params[segment.slice(1)] = decodeURIComponent(actual);
      continue;
    }
    if (segment !== actual) {
      return undefined;
    }
  }
  return params;
}

export function parseQuery(search: string): Record<string, string> {
  const query: Record<string, string> = {};
  const trimmed = search.startsWith('?') ? search.slice(1) : search;
  if (trimmed === '') {
    return query;
  }
  for (const part of trimmed.split('&')) {
    if (part === '') {
      continue;
    }
    const at = part.indexOf('=');
    const key = at < 0 ? part : part.slice(0, at);
    const value = at < 0 ? '' : part.slice(at + 1);
    query[decodeURIComponent(key)] = decodeURIComponent(value.replace(/\+/g, ' '));
  }
  return query;
}

export interface RouterOptions {
  readonly notFound?: Handler;
  readonly methodNotAllowed?: Handler;
}

const defaultNotFound: Handler = (request) =>
  Promise.resolve({
    status: 404,
    body: { code: 'NOT_FOUND', message: `no route for ${request.method} ${request.path}` },
  });

const defaultMethodNotAllowed: Handler = (request) =>
  Promise.resolve({
    status: 405,
    body: { code: 'METHOD_NOT_ALLOWED', message: `${request.method} is not allowed here` },
  });

/**
 * Routes are tried in order, and the first match wins. A path that matches a
 * pattern under a different method produces 405 rather than 404, because the
 * two mean different things to a client and collapsing them hides a typo in a
 * verb behind a missing endpoint.
 */
export function createRouter(routes: readonly Route[], options: RouterOptions = {}): Handler {
  const notFound = options.notFound ?? defaultNotFound;
  const methodNotAllowed = options.methodNotAllowed ?? defaultMethodNotAllowed;

  return async (request) => {
    let pathMatched = false;

    for (const candidate of routes) {
      const params = matchPath(candidate.pattern, request.path);
      if (params === undefined) {
        continue;
      }
      pathMatched = true;
      if (candidate.method !== request.method) {
        continue;
      }
      return candidate.handler({ ...request, params });
    }

    return pathMatched ? methodNotAllowed(request) : notFound(request);
  };
}

export function json(status: number, body: Json): HttpResponse {
  return { status, body };
}

export function noContent(): HttpResponse {
  return { status: 204, body: null };
}

export function methodsFor(routes: readonly Route[], path: string): Method[] {
  return routes
    .filter((candidate) => matchPath(candidate.pattern, path) !== undefined)
    .map((candidate) => candidate.method);
}
