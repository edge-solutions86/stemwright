import type { AddressInfo } from 'node:net';
import { afterEach, describe, expect, it } from 'vitest';
import { ForbiddenError } from '../../src/domain/errors.js';
import { createRouter, json, route } from '../../src/http/router.js';
import { createServer, isMethod, parseBody } from '../../src/http/server.js';
import type { Server } from 'node:http';

const servers: Server[] = [];

const listen = async (server: Server): Promise<string> => {
  servers.push(server);
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address() as AddressInfo;
  return `http://127.0.0.1:${address.port}`;
};

afterEach(async () => {
  while (servers.length > 0) {
    const server = servers.pop()!;
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }
});

const routes = [
  route('GET', '/ping', () => Promise.resolve(json(200, { pong: true }))),
  route('GET', '/echo/:name', (request) =>
    Promise.resolve(json(200, { name: request.params['name'] ?? null, query: request.query })),
  ),
  route('POST', '/echo', (request) => Promise.resolve(json(200, { body: request.body ?? null }))),
  route('DELETE', '/gone', () => Promise.resolve({ status: 204, body: null })),
  route('GET', '/forbidden', () => Promise.reject(new ForbiddenError('no'))),
  route('GET', '/broken', () => Promise.reject(new TypeError('boom'))),
];

describe('isMethod', () => {
  it('accepts the four methods and nothing else', () => {
    expect(isMethod('GET')).toBe(true);
    expect(isMethod('PATCH')).toBe(false);
  });
});

describe('parseBody', () => {
  it('reads an empty body as nothing', () => {
    expect(parseBody('')).toBeUndefined();
    expect(parseBody('   ')).toBeUndefined();
  });

  it('parses json', () => {
    expect(parseBody('{"a":1}')).toEqual({ a: 1 });
  });

  it('refuses text that is not json', () => {
    expect(() => parseBody('{')).toThrow(/not valid json/);
  });
});

describe('the server', () => {
  const start = (onError?: (error: unknown) => void) =>
    listen(createServer(createRouter(routes), onError === undefined ? {} : { onError }));

  it('answers a get', async () => {
    const base = await start();
    const response = await fetch(`${base}/ping`);
    expect(response.status).toBe(200);
    expect(response.headers.get('content-type')).toContain('application/json');
    expect(await response.json()).toEqual({ pong: true });
  });

  it('passes the path captures and the query through', async () => {
    const base = await start();
    const response = await fetch(`${base}/echo/puella%20mea?on=2025-03-03`);
    expect(await response.json()).toEqual({
      name: 'puella mea',
      query: { on: '2025-03-03' },
    });
  });

  it('reads a json body', async () => {
    const base = await start();
    const response = await fetch(`${base}/echo`, {
      method: 'POST',
      body: JSON.stringify({ a: 1 }),
    });
    expect(await response.json()).toEqual({ body: { a: 1 } });
  });

  it('answers an empty body for 204', async () => {
    const base = await start();
    const response = await fetch(`${base}/gone`, { method: 'DELETE' });
    expect(response.status).toBe(204);
    expect(await response.text()).toBe('');
  });

  it('answers 404 for a path nothing matches', async () => {
    const base = await start();
    const response = await fetch(`${base}/nowhere`);
    expect(response.status).toBe(404);
    expect(await response.json()).toMatchObject({ code: 'NOT_FOUND' });
  });

  it('maps a domain failure to its status', async () => {
    const base = await start();
    const response = await fetch(`${base}/forbidden`);
    expect(response.status).toBe(403);
    expect(await response.json()).toMatchObject({ code: 'FORBIDDEN' });
  });

  it('hides an unexpected failure behind a bare 500', async () => {
    const seen: unknown[] = [];
    const base = await start((error) => seen.push(error));
    const response = await fetch(`${base}/broken`);
    expect(response.status).toBe(500);
    expect(await response.json()).toEqual({
      code: 'INTERNAL',
      message: 'the request could not be completed',
    });
    expect(seen).toHaveLength(1);
  });

  it('refuses a body that is not json', async () => {
    const base = await start();
    const response = await fetch(`${base}/echo`, { method: 'POST', body: '{' });
    expect(response.status).toBe(400);
    expect(await response.json()).toMatchObject({ code: 'VALIDATION' });
  });

  it('refuses a body over the limit', async () => {
    const server = createServer(createRouter(routes), { bodyLimit: 16 });
    const base = await listen(server);
    const response = await fetch(`${base}/echo`, {
      method: 'POST',
      body: JSON.stringify({ padding: 'x'.repeat(200) }),
    }).catch(() => undefined);
    expect(response === undefined || response.status === 400).toBe(true);
  });

  it('reports every request to the log hook', async () => {
    const lines: string[] = [];
    const server = createServer(createRouter(routes), { onRequest: (line) => lines.push(line) });
    const base = await listen(server);
    await fetch(`${base}/ping`);
    expect(lines).toHaveLength(1);
    expect(lines[0]).toContain('GET /ping 200');
  });

  it('answers 405 for the wrong method on a known path', async () => {
    const base = await start();
    const response = await fetch(`${base}/ping`, { method: 'DELETE' });
    expect(response.status).toBe(405);
  });
});
