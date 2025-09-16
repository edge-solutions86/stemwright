import { describe, expect, it } from 'vitest';
import { ForbiddenError, ValidationError } from '../../src/domain/errors.js';
import { createRouter } from '../../src/http/router.js';
import { MAX_BATCH, bulkRoutes, readBatch } from '../../src/http/routes/bulk-routes.js';
import { drillRoutes } from '../../src/http/routes/drill-routes.js';
import { directory, httpHarness, makeRequest } from '../support/http.js';

const QUEUE = '/learners/lrn_one/languages/latin/decks/all/queue';
const BULK = '/learners/lrn_one/languages/latin/decks/all/answers/bulk';

const build = async () => {
  const harness = await httpHarness();
  const deps = { directory, drill: harness.drill, scopeOf: harness.scopeOf };
  return { ...harness, handle: createRouter([...drillRoutes(deps), ...bulkRoutes(deps)]) };
};

type Handle = Awaited<ReturnType<typeof build>>['handle'];

const cellIds = async (handle: Handle, count: number) => {
  const response = await handle(
    makeRequest('GET', QUEUE, { token: 'tok-student', query: { course: 'latin-1a' } }),
  );
  return (response.body as { cells: { id: string }[] }).cells.slice(0, count).map((c) => c.id);
};

const post = (handle: Handle, answers: unknown, token = 'tok-student') =>
  handle(makeRequest('POST', BULK, { token, query: { course: 'latin-1a' }, body: { answers } }));

describe('readBatch', () => {
  it('reads a list of answers', () => {
    expect(readBatch({ answers: [{ cellId: 'cell_a', given: 'x' }] })).toEqual([
      { cellId: 'cell_a', given: 'x' },
    ]);
  });

  it('refuses a body that is not an object', () => {
    expect(() => readBatch('nope')).toThrow(/body is not an object/);
  });

  it('refuses a missing or empty list', () => {
    expect(() => readBatch({})).toThrow(/body.answers is not an array/);
    expect(() => readBatch({ answers: [] })).toThrow(/at least one answer/);
  });

  it('refuses a list over the cap', () => {
    const many = Array.from({ length: MAX_BATCH + 1 }, () => ({ cellId: 'a', given: 'b' }));
    expect(() => readBatch({ answers: many })).toThrow(/at most 100 answers/);
  });

  it('names the bad row', () => {
    expect(() => readBatch({ answers: [{ cellId: 'a' }] })).toThrow(
      /body.answers.0.given is not a string/,
    );
    expect(() => readBatch({ answers: ['nope'] })).toThrow(ValidationError);
  });
});

describe('the bulk route', () => {
  it('answers every cell and reports 200 when all landed', async () => {
    const { handle } = await build();
    const ids = await cellIds(handle, 3);
    const response = await post(
      handle,
      ids.map((cellId) => ({ cellId, given: 'nonsense' })),
    );

    expect(response.status).toBe(200);
    const body = response.body as { accepted: number; refused: number; results: unknown[] };
    expect(body.accepted).toBe(3);
    expect(body.refused).toBe(0);
    expect(body.results).toHaveLength(3);
  });

  it('keeps going past a bad cell and reports 207', async () => {
    const { handle } = await build();
    const ids = await cellIds(handle, 2);
    const response = await post(handle, [
      { cellId: ids[0]!, given: 'x' },
      { cellId: 'cell_zzzzzz', given: 'x' },
      { cellId: ids[1]!, given: 'x' },
    ]);

    expect(response.status).toBe(207);
    const body = response.body as { accepted: number; refused: number; results: { ok: boolean }[] };
    expect(body.accepted).toBe(2);
    expect(body.refused).toBe(1);
    expect(body.results.map((row) => row.ok)).toEqual([true, false, true]);
  });

  it('reports the verdict and the expected form of each answer', async () => {
    const { handle } = await build();
    const ids = await cellIds(handle, 1);
    const response = await post(handle, [{ cellId: ids[0]!, given: 'nonsense' }]);
    const results = (response.body as { results: { verdict: string; expected: string }[] }).results;
    expect(results[0]?.verdict).toBe('wrong');
    expect(results[0]?.expected.length).toBeGreaterThan(0);
  });

  it('names the cell that could not be answered', async () => {
    const { handle } = await build();
    const response = await post(handle, [{ cellId: 'cell_zzzzzz', given: 'x' }]);
    const results = (response.body as { results: { cellId: string; message: string }[] }).results;
    expect(results[0]?.cellId).toBe('cell_zzzzzz');
    expect(results[0]?.message).toContain('was not found');
  });

  it('records every accepted answer in one session', async () => {
    const { handle, stores } = await build();
    const ids = await cellIds(handle, 3);
    await post(
      handle,
      ids.map((cellId) => ({ cellId, given: 'x' })),
    );
    const attempts = await stores.attempts.listByLearner('lrn_one');
    expect(attempts).toHaveLength(3);
    expect(new Set(attempts.map((entry) => entry.sessionId)).size).toBe(1);
  });

  it('refuses a learner who is not enrolled', async () => {
    const { handle } = await build();
    const ids = await cellIds(handle, 1);
    const answers = ids.map((cellId) => ({ cellId, given: 'x' }));
    await expect(post(handle, answers, 'tok-other')).rejects.toThrow(ForbiddenError);
  });

  it('refuses a request with no course', async () => {
    const { handle } = await build();
    const request = makeRequest('POST', BULK, {
      token: 'tok-student',
      body: { answers: [{ cellId: 'a', given: 'b' }] },
    });
    await expect(handle(request)).rejects.toThrow(/course must be given/);
  });
});
