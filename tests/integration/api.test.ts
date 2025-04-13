import { describe, expect, it } from 'vitest';
import { createApplication } from '../../src/app/application.js';
import { recordingBus } from '../../src/app/events.js';
import { fixedClock } from '../../src/app/ports.js';
import { enrol } from '../../src/domain/course/enrolment.js';
import { deck } from '../../src/domain/drill/deck.js';
import { createApi, healthRoutes, routeTable } from '../../src/http/api.js';
import { createRouter } from '../../src/http/router.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { readConfig } from '../../src/bin/serve.js';
import { seededStores } from '../support/app.js';
import { michaelmas } from '../support/course.js';
import { directory, makeRequest } from '../support/http.js';
import { latinNoun } from '../support/profiles.js';

const DAY = parseCivilDate('2024-10-21');

const build = async (options: { isReady?: () => boolean } = {}) => {
  const stores = await seededStores();
  await stores.decks.put(deck(latinNoun, { key: 'all' }));
  await stores.courses.put(michaelmas);
  await stores.courses.putEnrolment(enrol(michaelmas, { learnerId: 'lrn_one' }));

  const app = createApplication(stores, {
    bus: recordingBus(),
    clock: fixedClock(new Date('2024-10-21T09:00:00Z'), DAY),
  });

  const apiOptions = {
    directory,
    version: '1.2.3',
    ...(options.isReady === undefined ? {} : { isReady: options.isReady }),
  };
  return { app, apiOptions, handle: createApi(app, apiOptions) };
};

describe('health and readiness', () => {
  it('answers health with the version', async () => {
    const { handle } = await build();
    const response = await handle(makeRequest('GET', '/health'));
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ status: 'up', version: '1.2.3' });
  });

  it('answers readiness when the check passes', async () => {
    const { handle } = await build({ isReady: () => true });
    expect((await handle(makeRequest('GET', '/ready'))).status).toBe(200);
  });

  it('answers 503 when the readiness check fails', async () => {
    const { handle } = await build({ isReady: () => false });
    const response = await handle(makeRequest('GET', '/ready'));
    expect(response.status).toBe(503);
    expect(response.body).toMatchObject({ status: 'not ready' });
  });

  it('stays up even when it is not ready', async () => {
    const { handle } = await build({ isReady: () => false });
    expect((await handle(makeRequest('GET', '/health'))).status).toBe(200);
  });

  it('needs no token', async () => {
    const { apiOptions } = await build();
    const handle = createRouter(healthRoutes(apiOptions));
    expect((await handle(makeRequest('GET', '/health'))).status).toBe(200);
  });

  it('reports an unknown version when none is configured', async () => {
    const { app } = await build();
    const handle = createApi(app, { directory });
    expect((await handle(makeRequest('GET', '/health'))).body).toMatchObject({
      version: 'unknown',
    });
  });
});

describe('the assembled api', () => {
  it('carries every route family', async () => {
    const { app, apiOptions } = await build();
    const table = routeTable(app, apiOptions);
    expect(table).toContain('GET /health');
    expect(table.some((line) => line.includes('/queue'))).toBe(true);
    expect(table.some((line) => line.includes('/lexemes'))).toBe(true);
    expect(table.some((line) => line.includes('/coverage'))).toBe(true);
  });

  it('turns a thrown domain failure into a response rather than a throw', async () => {
    const { handle } = await build();
    const response = await handle(
      makeRequest('GET', '/learners/lrn_one/languages/latin/decks/all/queue', {
        token: 'tok-student',
      }),
    );
    expect(response.status).toBe(400);
    expect(response.body).toMatchObject({ code: 'VALIDATION' });
  });

  it('answers 403 for a request with no token rather than throwing', async () => {
    const { handle } = await build();
    const response = await handle(makeRequest('GET', '/languages/latin/lexemes'));
    expect(response.status).toBe(403);
  });

  it('answers 404 for a path nothing matches', async () => {
    const { handle } = await build();
    expect((await handle(makeRequest('GET', '/nowhere'))).status).toBe(404);
  });

  it('serves a drill queue end to end', async () => {
    const { handle } = await build();
    const response = await handle(
      makeRequest('GET', '/learners/lrn_one/languages/latin/decks/all/queue', {
        token: 'tok-student',
        query: { course: 'latin-1a' },
      }),
    );
    expect(response.status).toBe(200);
  });
});

describe('readConfig', () => {
  it('defaults the port and the version', () => {
    const config = readConfig({});
    expect(config.port).toBe(8080);
    expect(config.version).toBe('dev');
    expect(config.accounts).toEqual({});
  });

  it('reads the port from the environment', () => {
    expect(readConfig({ STEMWRIGHT_PORT: '9000' }).port).toBe(9000);
  });

  it('refuses a port that is not a port', () => {
    expect(() => readConfig({ STEMWRIGHT_PORT: 'nope' })).toThrow(/not a port number/);
    expect(() => readConfig({ STEMWRIGHT_PORT: '70000' })).toThrow(/not a port number/);
  });

  it('reads the account map', () => {
    const config = readConfig({
      STEMWRIGHT_ACCOUNTS: JSON.stringify({ tok: { id: 'lrn_one', role: 'learner' } }),
    });
    expect(config.accounts['tok']).toEqual({ id: 'lrn_one', role: 'learner' });
  });
});
