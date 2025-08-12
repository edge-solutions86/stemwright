import { describe, expect, it } from 'vitest';
import { createApplication } from '../../src/app/application.js';
import { recordingBus } from '../../src/app/events.js';
import { fixedClock } from '../../src/app/ports.js';
import { roster } from '../../src/domain/course/enrolment.js';
import type { CourseScope } from '../../src/domain/course/authz.js';
import { enrol } from '../../src/domain/course/enrolment.js';
import { deck } from '../../src/domain/drill/deck.js';
import { ForbiddenError, ValidationError } from '../../src/domain/errors.js';
import { createRouter } from '../../src/http/router.js';
import { rangeFrom } from '../../src/http/query.js';
import { reportRoutes } from '../../src/http/routes/report-routes.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { seededStores } from '../support/app.js';
import { michaelmas } from '../support/course.js';
import { directory, makeRequest } from '../support/http.js';
import { latinNoun } from '../support/profiles.js';

const d = (value: string) => parseCivilDate(value);
const DAY = d('2024-10-21');
const COVERAGE = '/learners/lrn_one/languages/latin/decks/all/coverage';
const ACTIVITY = '/learners/lrn_one/activity';
const window = { course: 'latin-1a', from: '2024-10-21', to: '2024-10-27' };

const build = async () => {
  const stores = await seededStores();
  await stores.decks.put(deck(latinNoun, { key: 'all' }));
  await stores.courses.put(michaelmas);
  await stores.courses.putEnrolment(enrol(michaelmas, { learnerId: 'lrn_one' }));

  const app = createApplication(stores, {
    bus: recordingBus(),
    clock: fixedClock(new Date('2024-10-21T09:00:00Z'), DAY),
  });

  const scopeOf = async (courseKey: string): Promise<CourseScope> => {
    const course = await stores.courses.get(courseKey);
    if (course === undefined) {
      throw new Error('no course');
    }
    return { course, roster: roster(course, await stores.courses.enrolments(courseKey)) };
  };

  return {
    app,
    handle: createRouter(reportRoutes({ directory, reports: app.reports, scopeOf })),
  };
};

describe('rangeFrom', () => {
  it('reads both ends', () => {
    expect(rangeFrom({ from: '2024-10-21', to: '2024-10-27' })).toEqual({
      from: '2024-10-21',
      to: '2024-10-27',
    });
  });

  it('refuses a window with only one end', () => {
    expect(() => rangeFrom({ from: '2024-10-21' })).toThrow(/needs both a from and a to day/);
    expect(() => rangeFrom({})).toThrow(ValidationError);
  });

  it('refuses a window that ends before it starts', () => {
    expect(() => rangeFrom({ from: '2024-10-27', to: '2024-10-21' })).toThrow(ValidationError);
  });
});

describe('the coverage route', () => {
  it('reports the counts and the weakest rows', async () => {
    const { handle } = await build();
    const response = await handle(
      makeRequest('GET', COVERAGE, { token: 'tok-student', query: window }),
    );
    expect(response.status).toBe(200);
    const body = response.body as {
      counts: { known: number; untouched: number };
      weakest: unknown[];
      forecast: unknown[];
    };
    expect(body.counts.known).toBe(30);
    expect(body.counts.untouched).toBe(30);
    expect(body.weakest.length).toBeGreaterThan(0);
    expect(body.forecast).toHaveLength(7);
  });

  it('refuses a request with no window', async () => {
    const { handle } = await build();
    const request = makeRequest('GET', COVERAGE, {
      token: 'tok-student',
      query: { course: 'latin-1a' },
    });
    await expect(handle(request)).rejects.toThrow(ValidationError);
  });

  it('refuses a request with no course', async () => {
    const { handle } = await build();
    const request = makeRequest('GET', COVERAGE, {
      token: 'tok-student',
      query: { from: '2024-10-21', to: '2024-10-27' },
    });
    await expect(handle(request)).rejects.toThrow(/course must be given/);
  });

  it('refuses a learner reading somebody else', async () => {
    const { handle } = await build();
    const request = makeRequest('GET', COVERAGE, { token: 'tok-other', query: window });
    await expect(handle(request)).rejects.toThrow(ForbiddenError);
  });

  it('lets the instructor read a learner', async () => {
    const { handle } = await build();
    const response = await handle(
      makeRequest('GET', COVERAGE, { token: 'tok-teacher', query: window }),
    );
    expect(response.status).toBe(200);
  });
});

describe('the activity route', () => {
  it('reports a row for every day of the window', async () => {
    const { handle } = await build();
    const response = await handle(
      makeRequest('GET', ACTIVITY, { token: 'tok-student', query: window }),
    );
    const body = response.body as { days: unknown[]; hardest: unknown[] };
    expect(body.days).toHaveLength(7);
    expect(body.hardest).toEqual([]);
  });

  it('counts an answer on the day it happened', async () => {
    const { app, handle } = await build();
    const queue = await app.drill.queue('lrn_one', 'latin', 'all', DAY);
    await app.drill.answer({
      learnerId: 'lrn_one',
      language: 'latin',
      deckKey: 'all',
      cellId: queue.entries[0]!.cell.id,
      given: 'nonsense',
      on: DAY,
    });

    const response = await handle(
      makeRequest('GET', ACTIVITY, { token: 'tok-student', query: window }),
    );
    const body = response.body as { days: { answers: number }[]; hardest: unknown[] };
    expect(body.days[0]?.answers).toBe(1);
    expect(body.hardest).toHaveLength(1);
  });

  it('refuses a learner reading somebody else', async () => {
    const { handle } = await build();
    const request = makeRequest('GET', ACTIVITY, { token: 'tok-other', query: window });
    await expect(handle(request)).rejects.toThrow(ForbiddenError);
  });
});
