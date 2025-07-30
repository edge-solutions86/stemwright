import { describe, expect, it } from 'vitest';
import { PolicyError } from '../../src/domain/errors.js';
import {
  DEFAULT_LIMIT,
  createLimiter,
  describeLimit,
  isOver,
  keyFor,
  withRateLimit,
} from '../../src/http/rate-limit.js';
import { json } from '../../src/http/router.js';
import type { HttpRequest } from '../../src/http/router.js';

const request = (headers: Record<string, string> = {}): HttpRequest => ({
  method: 'GET',
  path: '/decks',
  params: {},
  query: {},
  headers,
  body: undefined,
});

const limit = { windowMs: 1000, max: 3 };

describe('keyFor', () => {
  it('keys on the token when there is one', () => {
    expect(keyFor(request({ authorization: 'Bearer tok' }))).toBe('token:Bearer tok');
  });

  it('keys on the forwarded address when there is no token', () => {
    expect(keyFor(request({ 'x-forwarded-for': '10.0.0.1' }))).toBe('address:10.0.0.1');
  });

  it('falls back to an unknown address', () => {
    expect(keyFor(request())).toBe('address:unknown');
  });

  it('keys two learners behind one address separately', () => {
    const first = keyFor(request({ authorization: 'Bearer a', 'x-forwarded-for': '10.0.0.1' }));
    const second = keyFor(request({ authorization: 'Bearer b', 'x-forwarded-for': '10.0.0.1' }));
    expect(first).not.toBe(second);
  });
});

describe('the limiter', () => {
  it('counts up within a window', () => {
    const limiter = createLimiter(limit);
    expect(limiter.check('a', 0).count).toBe(1);
    expect(limiter.check('a', 100).count).toBe(2);
    expect(limiter.check('a', 200).count).toBe(3);
    expect(limiter.check('a', 300).remaining).toBe(0);
  });

  it('starts a new window once the old one has passed', () => {
    const limiter = createLimiter(limit);
    limiter.check('a', 0);
    limiter.check('a', 100);
    expect(limiter.check('a', 1000).count).toBe(1);
  });

  it('counts each key separately', () => {
    const limiter = createLimiter(limit);
    limiter.check('a', 0);
    expect(limiter.check('b', 0).count).toBe(1);
    expect(limiter.keys()).toEqual(['a', 'b']);
  });

  it('never reports a negative remaining', () => {
    const limiter = createLimiter(limit);
    for (let index = 0; index < 9; index += 1) {
      limiter.check('a', index);
    }
    expect(limiter.check('a', 10).remaining).toBe(0);
  });

  it('forgets everything when it is reset', () => {
    const limiter = createLimiter(limit);
    limiter.check('a', 0);
    limiter.reset();
    expect(limiter.keys()).toEqual([]);
  });
});

describe('isOver', () => {
  it('is false up to the limit and true past it', () => {
    expect(isOver({ key: 'a', count: 3, windowStart: 0, remaining: 0 }, limit)).toBe(false);
    expect(isOver({ key: 'a', count: 4, windowStart: 0, remaining: 0 }, limit)).toBe(true);
  });
});

describe('withRateLimit', () => {
  const ok = () => Promise.resolve(json(200, { ok: true }));

  it('lets requests through up to the limit', async () => {
    let now = 0;
    const handler = withRateLimit(ok, createLimiter(limit), limit, () => now);
    for (let index = 0; index < 3; index += 1) {
      now = index;
      expect((await handler(request())).status).toBe(200);
    }
  });

  it('refuses the request past the limit', async () => {
    let now = 0;
    const handler = withRateLimit(ok, createLimiter(limit), limit, () => now);
    for (let index = 0; index < 3; index += 1) {
      now = index;
      await handler(request());
    }
    now = 4;
    await expect(handler(request())).rejects.toThrow(PolicyError);
    await expect(handler(request())).rejects.toThrow(/too many requests/);
  });

  it('lets them through again in the next window', async () => {
    let now = 0;
    const handler = withRateLimit(ok, createLimiter(limit), limit, () => now);
    for (let index = 0; index < 4; index += 1) {
      now = index;
      await handler(request()).catch(() => undefined);
    }
    now = 2000;
    expect((await handler(request())).status).toBe(200);
  });

  it('counts two tokens separately', async () => {
    let now = 0;
    const handler = withRateLimit(ok, createLimiter(limit), limit, () => now);
    for (let index = 0; index < 3; index += 1) {
      now = index;
      await handler(request({ authorization: 'Bearer a' }));
    }
    now = 4;
    expect((await handler(request({ authorization: 'Bearer b' }))).status).toBe(200);
  });
});

describe('describeLimit', () => {
  it('reads the limit out in seconds', () => {
    expect(describeLimit(DEFAULT_LIMIT)).toBe('120 requests every 60 seconds');
  });
});
