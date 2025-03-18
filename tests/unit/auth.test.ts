import { describe, expect, it } from 'vitest';
import { ForbiddenError } from '../../src/domain/errors.js';
import {
  actorOf,
  bearerToken,
  memoryDirectory,
  requireRole,
  withActor,
} from '../../src/http/auth.js';
import type { AuthedRequest } from '../../src/http/auth.js';
import { json } from '../../src/http/router.js';
import type { HttpRequest } from '../../src/http/router.js';

const directory = memoryDirectory({
  'tok-student': { id: 'lrn_one', role: 'learner' },
  'tok-teacher': { id: 'lrn_teacher', role: 'instructor' },
  'tok-admin': { id: 'lrn_admin', role: 'admin' },
});

const request = (headers: Record<string, string> = {}): HttpRequest => ({
  method: 'GET',
  path: '/decks',
  params: {},
  query: {},
  headers,
  body: undefined,
});

const echoActor = (incoming: AuthedRequest) =>
  Promise.resolve(json(200, { id: actorOf(incoming).id, role: incoming.actor.role }));

describe('bearerToken', () => {
  it('reads a token out of the header', () => {
    expect(bearerToken({ authorization: 'Bearer tok-student' })).toBe('tok-student');
  });

  it('ignores the case of the scheme and of the header name', () => {
    expect(bearerToken({ authorization: 'bearer tok' })).toBe('tok');
    expect(bearerToken({ Authorization: 'BEARER tok' })).toBe('tok');
  });

  it('gives nothing for a missing or malformed header', () => {
    expect(bearerToken({})).toBeUndefined();
    expect(bearerToken({ authorization: 'tok' })).toBeUndefined();
    expect(bearerToken({ authorization: 'Basic abc' })).toBeUndefined();
    expect(bearerToken({ authorization: 'Bearer ' })).toBeUndefined();
  });
});

describe('memoryDirectory', () => {
  it('resolves a token it holds', async () => {
    expect(await directory.resolve('tok-teacher')).toEqual({
      id: 'lrn_teacher',
      role: 'instructor',
    });
  });

  it('gives nothing for a token it does not', async () => {
    expect(await directory.resolve('tok-nowhere')).toBeUndefined();
  });

  it('gives nothing for an account with a role it does not recognise', async () => {
    const broken = memoryDirectory({ tok: { id: 'lrn_one', role: 'tutor' as 'learner' } });
    expect(await broken.resolve('tok')).toBeUndefined();
  });
});

describe('withActor', () => {
  const handler = withActor(directory, echoActor);

  it('attaches the actor the token names', async () => {
    const response = await handler(request({ authorization: 'Bearer tok-student' }));
    expect(response.body).toEqual({ id: 'lrn_one', role: 'learner' });
  });

  it('refuses a request with no token', async () => {
    await expect(handler(request())).rejects.toThrow(ForbiddenError);
    await expect(handler(request())).rejects.toThrow(/no bearer token was presented/);
  });

  it('refuses a token nobody holds', async () => {
    await expect(handler(request({ authorization: 'Bearer tok-nowhere' }))).rejects.toThrow(
      /does not name an account/,
    );
  });

  it('leaves the rest of the request alone', async () => {
    let seen: AuthedRequest | undefined;
    const capture = withActor(directory, (incoming) => {
      seen = incoming;
      return Promise.resolve(json(200, null));
    });
    await capture({ ...request({ authorization: 'Bearer tok-admin' }), path: '/reports' });
    expect(seen?.path).toBe('/reports');
  });
});

describe('requireRole', () => {
  it('lets a permitted role through', async () => {
    const handler = withActor(directory, requireRole(['instructor', 'admin'], echoActor));
    expect((await handler(request({ authorization: 'Bearer tok-teacher' }))).status).toBe(200);
  });

  it('refuses a role that is not listed', async () => {
    const handler = withActor(directory, requireRole(['instructor'], echoActor));
    const answer = handler(request({ authorization: 'Bearer tok-student' }));
    await expect(answer).rejects.toThrow(/lrn_one may not reach/);
  });

  it('refuses everybody when the list is empty', async () => {
    const handler = withActor(directory, requireRole([], echoActor));
    const answer = handler(request({ authorization: 'Bearer tok-admin' }));
    await expect(answer).rejects.toThrow(ForbiddenError);
  });
});
