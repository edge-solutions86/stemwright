import { createDrillService } from '../../src/app/services/drill-service.js';
import type { CourseScope } from '../../src/domain/course/authz.js';
import { enrol, roster } from '../../src/domain/course/enrolment.js';
import { deck } from '../../src/domain/drill/deck.js';
import { memoryDirectory } from '../../src/http/auth.js';
import type { HttpRequest, Method } from '../../src/http/router.js';
import { seededStores, testHarness } from './app.js';
import { michaelmas } from './course.js';
import { latinNoun } from './profiles.js';

export const directory = memoryDirectory({
  'tok-student': { id: 'lrn_one', role: 'learner' },
  'tok-other': { id: 'lrn_two', role: 'learner' },
  'tok-teacher': { id: 'lrn_teacher', role: 'instructor' },
});

export function makeRequest(
  method: Method,
  path: string,
  options: { token?: string; query?: Record<string, string>; body?: unknown } = {},
): HttpRequest {
  return {
    method,
    path,
    params: {},
    query: options.query ?? {},
    headers: options.token === undefined ? {} : { authorization: `Bearer ${options.token}` },
    body: options.body as HttpRequest['body'],
  };
}

export async function httpHarness() {
  const stores = await seededStores();
  await stores.decks.put(deck(latinNoun, { key: 'all' }));
  await stores.courses.put(michaelmas);
  await stores.courses.putEnrolment(enrol(michaelmas, { learnerId: 'lrn_one' }));

  const { bus, clock } = testHarness();
  const drill = createDrillService(stores, bus, clock);

  const scopeOf = async (courseKey: string): Promise<CourseScope> => {
    const course = await stores.courses.get(courseKey);
    if (course === undefined) {
      throw new Error(`no course ${courseKey}`);
    }
    return { course, roster: roster(course, await stores.courses.enrolments(courseKey)) };
  };

  return { stores, bus, clock, drill, scopeOf };
}
