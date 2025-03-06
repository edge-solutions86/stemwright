import { describe, expect, it } from 'vitest';
import type { HttpRequest, Method } from '../../src/http/router.js';
import {
  createRouter,
  json,
  matchPath,
  methodsFor,
  noContent,
  parseQuery,
  route,
  splitPath,
} from '../../src/http/router.js';

const request = (method: Method, path: string): HttpRequest => ({
  method,
  path,
  params: {},
  query: {},
  headers: {},
  body: undefined,
});

const ok = (body: string) => () => Promise.resolve(json(200, { body }));

describe('splitPath', () => {
  it('drops empty segments however many slashes there are', () => {
    expect(splitPath('/a/b/')).toEqual(['a', 'b']);
    expect(splitPath('//a//b')).toEqual(['a', 'b']);
    expect(splitPath('/')).toEqual([]);
  });
});

describe('matchPath', () => {
  it('matches a literal path', () => {
    expect(matchPath('/decks', '/decks')).toEqual({});
    expect(matchPath('/decks', '/lexemes')).toBeUndefined();
  });

  it('captures a segment', () => {
    expect(matchPath('/decks/:key', '/decks/first-year')).toEqual({ key: 'first-year' });
  });

  it('captures several segments', () => {
    expect(matchPath('/learners/:learnerId/decks/:key', '/learners/lrn_one/decks/all')).toEqual({
      learnerId: 'lrn_one',
      key: 'all',
    });
  });

  it('refuses a path with the wrong number of segments', () => {
    expect(matchPath('/decks/:key', '/decks')).toBeUndefined();
    expect(matchPath('/decks/:key', '/decks/a/b')).toBeUndefined();
  });

  it('decodes a captured segment', () => {
    expect(matchPath('/lexemes/:lemma', '/lexemes/puella%20mea')).toEqual({
      lemma: 'puella mea',
    });
  });

  it('ignores trailing slashes on either side', () => {
    expect(matchPath('/decks/', '/decks')).toEqual({});
  });
});

describe('parseQuery', () => {
  it('reads pairs', () => {
    expect(parseQuery('?on=2025-03-03&deck=all')).toEqual({ on: '2025-03-03', deck: 'all' });
  });

  it('works with or without the leading question mark', () => {
    expect(parseQuery('on=2025-03-03')).toEqual({ on: '2025-03-03' });
  });

  it('reads a key with no value as an empty string', () => {
    expect(parseQuery('?verbose')).toEqual({ verbose: '' });
  });

  it('decodes percent escapes and plus signs', () => {
    expect(parseQuery('?q=puella+mea&r=a%26b')).toEqual({ q: 'puella mea', r: 'a&b' });
  });

  it('reads an empty query as nothing', () => {
    expect(parseQuery('')).toEqual({});
    expect(parseQuery('?')).toEqual({});
  });
});

describe('createRouter', () => {
  const routes = [
    route('GET', '/decks', ok('list')),
    route('POST', '/decks', ok('create')),
    route('GET', '/decks/:key', ok('one')),
  ];
  const handle = createRouter(routes);

  it('dispatches on method and path', async () => {
    expect((await handle(request('GET', '/decks'))).body).toEqual({ body: 'list' });
    expect((await handle(request('POST', '/decks'))).body).toEqual({ body: 'create' });
  });

  it('passes the captures to the handler', async () => {
    let seen: HttpRequest | undefined;
    const single = createRouter([
      route('GET', '/decks/:key', (incoming) => {
        seen = incoming;
        return Promise.resolve(json(200, null));
      }),
    ]);
    await single(request('GET', '/decks/first-year'));
    expect(seen?.params).toEqual({ key: 'first-year' });
  });

  it('answers 404 for a path nothing matches', async () => {
    const response = await handle(request('GET', '/nowhere'));
    expect(response.status).toBe(404);
    expect(response.body).toMatchObject({ code: 'NOT_FOUND' });
  });

  it('answers 405 when the path matches under another method', async () => {
    const response = await handle(request('DELETE', '/decks'));
    expect(response.status).toBe(405);
    expect(response.body).toMatchObject({ code: 'METHOD_NOT_ALLOWED' });
  });

  it('takes the first matching route when two patterns overlap', async () => {
    const overlapping = createRouter([
      route('GET', '/decks/mine', ok('literal')),
      route('GET', '/decks/:key', ok('capture')),
    ]);
    expect((await overlapping(request('GET', '/decks/mine'))).body).toEqual({ body: 'literal' });
  });

  it('takes an override for the two failure handlers', async () => {
    const custom = createRouter(routes, {
      notFound: () => Promise.resolve(json(410, { gone: true })),
      methodNotAllowed: () => Promise.resolve(json(418, { teapot: true })),
    });
    expect((await custom(request('GET', '/nowhere'))).status).toBe(410);
    expect((await custom(request('DELETE', '/decks'))).status).toBe(418);
  });
});

describe('response helpers', () => {
  it('builds a json response', () => {
    expect(json(201, { id: 'a' })).toEqual({ status: 201, body: { id: 'a' } });
  });

  it('builds an empty response', () => {
    expect(noContent()).toEqual({ status: 204, body: null });
  });
});

describe('methodsFor', () => {
  const routes = [
    route('GET', '/decks', ok('list')),
    route('POST', '/decks', ok('create')),
    route('GET', '/decks/:key', ok('one')),
  ];

  it('names every method a path accepts', () => {
    expect(methodsFor(routes, '/decks')).toEqual(['GET', 'POST']);
    expect(methodsFor(routes, '/decks/all')).toEqual(['GET']);
  });

  it('names none for a path nothing matches', () => {
    expect(methodsFor(routes, '/nowhere')).toEqual([]);
  });
});
