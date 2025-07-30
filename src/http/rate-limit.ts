import { PolicyError } from '../domain/errors.js';
import type { Handler, HttpRequest } from './router.js';

/**
 * A fixed window limiter, kept in memory.
 *
 * It is deliberately the simplest thing that works for one process. A sliding
 * window would be fairer at the boundary and a shared store would survive a
 * restart, and neither is worth having until this runs on more than one box.
 * What it is here for is stopping a client in a retry loop from filling a
 * journal, not stopping an attacker.
 *
 * The key is the actor when there is one and the address otherwise, so one
 * learner behind a shared campus address cannot lock out the rest of the class.
 */
export interface RateLimit {
  readonly windowMs: number;
  readonly max: number;
}

export const DEFAULT_LIMIT: RateLimit = { windowMs: 60_000, max: 120 };

export interface LimitState {
  readonly key: string;
  readonly count: number;
  readonly windowStart: number;
  readonly remaining: number;
}

export interface Limiter {
  check(key: string, now: number): LimitState;
  reset(): void;
  keys(): string[];
}

export function createLimiter(limit: RateLimit = DEFAULT_LIMIT): Limiter {
  const windows = new Map<string, { count: number; start: number }>();

  return {
    check(key, now) {
      const existing = windows.get(key);
      if (existing === undefined || now - existing.start >= limit.windowMs) {
        windows.set(key, { count: 1, start: now });
        return { key, count: 1, windowStart: now, remaining: limit.max - 1 };
      }
      existing.count += 1;
      return {
        key,
        count: existing.count,
        windowStart: existing.start,
        remaining: Math.max(0, limit.max - existing.count),
      };
    },
    reset() {
      windows.clear();
    },
    keys: () => [...windows.keys()].sort(),
  };
}

export function keyFor(request: HttpRequest): string {
  const authorization = request.headers['authorization'] ?? request.headers['Authorization'];
  if (authorization !== undefined && authorization !== '') {
    return `token:${authorization}`;
  }
  return `address:${request.headers['x-forwarded-for'] ?? 'unknown'}`;
}

export function isOver(state: LimitState, limit: RateLimit): boolean {
  return state.count > limit.max;
}

/**
 * Wraps a handler. Going over the limit is a policy failure rather than a
 * validation one, because the request was well formed and a rule refused it,
 * and the http layer already maps that to a status of its own.
 */
export function withRateLimit(
  handler: Handler,
  limiter: Limiter,
  limit: RateLimit = DEFAULT_LIMIT,
  now: () => number = () => Date.now(),
): Handler {
  return (request) => {
    const state = limiter.check(keyFor(request), now());
    if (isOver(state, limit)) {
      const allowed = String(limit.max);
      const window = String(limit.windowMs);
      // Rejects rather than throwing, so a caller that only awaits still sees
      // it. A synchronous throw out of an async handler escapes the await and
      // lands outside whatever was catching.
      return Promise.reject(
        new PolicyError(
          'rate.limit',
          `too many requests, ${allowed} are allowed every ${window} milliseconds`,
        ),
      );
    }
    return handler(request);
  };
}

export function describeLimit(limit: RateLimit): string {
  const seconds = Math.round(limit.windowMs / 1000);
  return `${limit.max} requests every ${seconds} seconds`;
}
