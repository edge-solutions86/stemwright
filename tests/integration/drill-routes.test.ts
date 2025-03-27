import { describe, expect, it } from 'vitest';
import { ForbiddenError, ValidationError } from '../../src/domain/errors.js';
import { drillRoutes } from '../../src/http/routes/drill-routes.js';
import { createRouter } from '../../src/http/router.js';
import { directory, httpHarness, makeRequest } from '../support/http.js';

const QUEUE = '/learners/lrn_one/languages/latin/decks/all/queue';
const ANSWER = '/learners/lrn_one/languages/latin/decks/all/answers';

const build = async () => {
  const harness = await httpHarness();
  const handle = createRouter(
    drillRoutes({ directory, drill: harness.drill, scopeOf: harness.scopeOf }),
  );
  return { ...harness, handle };
};

type Handle = Awaited<ReturnType<typeof build>>['handle'];

const firstCellId = async (handle: Handle) => {
  const response = await handle(
    makeRequest('GET', QUEUE, { token: 'tok-student', query: { course: 'latin-1a' } }),
  );
  return (response.body as { cells: { id: string }[] }).cells[0]!.id;
};

const answer = (handle: Handle, cellId: string, given: string) =>
  handle(
    makeRequest('POST', ANSWER, {
      token: 'tok-student',
      query: { course: 'latin-1a' },
      body: { cellId, given },
    }),
  );

describe('the queue route', () => {
  it('answers with the cells to drill', async () => {
    const { handle } = await build();
    const response = await handle(
      makeRequest('GET', QUEUE, { token: 'tok-student', query: { course: 'latin-1a' } }),
    );
    expect(response.status).toBe(200);
    const body = response.body as { cells: unknown[]; on: string };
    expect(body.cells.length).toBeGreaterThan(0);
    expect(body.on).toBe('2024-10-21');
  });

  it('takes the day from the query when it is given', async () => {
    const { handle } = await build();
    const response = await handle(
      makeRequest('GET', QUEUE, {
        token: 'tok-student',
        query: { course: 'latin-1a', on: '2024-11-04' },
      }),
    );
    expect((response.body as { on: string }).on).toBe('2024-11-04');
  });

  it('refuses a day that is not a real date', async () => {
    const { handle } = await build();
    const request = makeRequest('GET', QUEUE, {
      token: 'tok-student',
      query: { course: 'latin-1a', on: '2024-11-31' },
    });
    await expect(handle(request)).rejects.toThrow(ValidationError);
  });

  it('refuses a request with no course to check permissions against', async () => {
    const { handle } = await build();
    const request = makeRequest('GET', QUEUE, { token: 'tok-student' });
    await expect(handle(request)).rejects.toThrow(/course must be given/);
  });

  it('refuses a learner who is not enrolled', async () => {
    const { handle } = await build();
    const request = makeRequest('GET', QUEUE, {
      token: 'tok-other',
      query: { course: 'latin-1a' },
    });
    await expect(handle(request)).rejects.toThrow(ForbiddenError);
  });

  it('refuses a request with no token', async () => {
    const { handle } = await build();
    await expect(handle(makeRequest('GET', QUEUE))).rejects.toThrow(ForbiddenError);
  });
});

describe('the answer route', () => {
  it('marks an answer and reports the verdict', async () => {
    const { handle } = await build();
    const response = await answer(handle, await firstCellId(handle), 'nonsense');
    expect(response.status).toBe(200);
    const body = response.body as { verdict: string; expected: string; sequence: number };
    expect(body.verdict).toBe('wrong');
    expect(body.expected.length).toBeGreaterThan(0);
    expect(body.sequence).toBe(1);
  });

  it('reports a right answer as right', async () => {
    const { handle } = await build();
    const cellId = await firstCellId(handle);
    const first = await answer(handle, cellId, 'x');
    const expected = (first.body as { expected: string }).expected;
    const second = await answer(handle, cellId, expected);
    expect((second.body as { verdict: string }).verdict).toBe('exact');
  });

  it('refuses a body that is not an object', async () => {
    const { handle } = await build();
    const request = makeRequest('POST', ANSWER, {
      token: 'tok-student',
      query: { course: 'latin-1a' },
      body: 'nope',
    });
    await expect(handle(request)).rejects.toThrow(/body is not an object/);
  });

  it('refuses a body with no cell id', async () => {
    const { handle } = await build();
    const request = makeRequest('POST', ANSWER, {
      token: 'tok-student',
      query: { course: 'latin-1a' },
      body: { given: 'x' },
    });
    await expect(handle(request)).rejects.toThrow(/body.cellId is not a string/);
  });
});

describe('the finish route', () => {
  it('closes a session and reports the tally', async () => {
    const { handle } = await build();
    const answered = await answer(handle, await firstCellId(handle), 'x');
    const sessionId = (answered.body as { sessionId: string }).sessionId;

    const response = await handle(
      makeRequest('POST', `/learners/lrn_one/sessions/${sessionId}/finish`, {
        token: 'tok-student',
        query: { course: 'latin-1a' },
      }),
    );
    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({ answers: 1 });
  });
});

describe('routing', () => {
  it('answers 404 for a path nothing matches', async () => {
    const { handle } = await build();
    expect((await handle(makeRequest('GET', '/nowhere'))).status).toBe(404);
  });

  it('answers 405 for the wrong method on a known path', async () => {
    const { handle } = await build();
    expect((await handle(makeRequest('DELETE', QUEUE))).status).toBe(405);
  });
});
