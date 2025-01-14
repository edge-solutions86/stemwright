import { NotFoundError } from '../../domain/errors.js';
import type { Assignment, AssignmentInput } from '../../domain/course/assignment.js';
import { assignment } from '../../domain/course/assignment.js';
import type { Actor, CourseScope } from '../../domain/course/authz.js';
import { require as requirePermission, requireReadLearner } from '../../domain/course/authz.js';
import type { Course, CourseInput } from '../../domain/course/course.js';
import { course } from '../../domain/course/course.js';
import type { Enrolment, EnrolmentInput, Roster } from '../../domain/course/enrolment.js';
import {
  enrol,
  enrolmentOf,
  gradedLearners,
  roster,
  withdraw,
} from '../../domain/course/enrolment.js';
import type { AssignmentProgress } from '../../domain/course/progress.js';
import { assignmentProgress } from '../../domain/course/progress.js';
import type { Ladder } from '../../domain/mastery/ladder.js';
import { STANDARD_LADDER } from '../../domain/mastery/ladder.js';
import { emptyLedger } from '../../domain/mastery/ledger.js';
import type { CivilDate } from '../../domain/time/civil.js';
import type { EventBus } from '../events.js';
import type { Clock, Stores } from '../ports.js';
import type { DrillService } from './drill-service.js';

/**
 * The course service. Everything it does needs a scope, because every
 * permission it checks is about a relationship to one course rather than about
 * a role, so `scopeOf` is the first call in nearly every method.
 */
export interface CourseService {
  scopeOf(courseKey: string): Promise<CourseScope>;
  get(key: string): Promise<Course>;
  list(): Promise<Course[]>;
  create(actor: Actor, input: CourseInput): Promise<Course>;
  enrol(actor: Actor, courseKey: string, input: EnrolmentInput): Promise<Enrolment>;
  withdrawLearner(actor: Actor, courseKey: string, learnerId: string): Promise<Enrolment>;
  addAssignment(actor: Actor, courseKey: string, input: AssignmentInput): Promise<Assignment>;
  assignments(courseKey: string): Promise<Assignment[]>;
  progressFor(
    actor: Actor,
    courseKey: string,
    assignmentKey: string,
    learnerId: string,
    on?: CivilDate,
    ladder?: Ladder,
  ): Promise<AssignmentProgress>;
  cohortProgress(
    actor: Actor,
    courseKey: string,
    assignmentKey: string,
    on?: CivilDate,
    ladder?: Ladder,
  ): Promise<AssignmentProgress[]>;
}

export function createCourseService(
  stores: Stores,
  bus: EventBus,
  clock: Clock,
  drill: DrillService,
): CourseService {
  const loadCourse = async (key: string): Promise<Course> => {
    const found = await stores.courses.get(key);
    if (found === undefined) {
      throw new NotFoundError('course', key);
    }
    return found;
  };

  const loadRoster = async (entry: Course): Promise<Roster> =>
    roster(entry, await stores.courses.enrolments(entry.key));

  const scopeOf = async (courseKey: string): Promise<CourseScope> => {
    const entry = await loadCourse(courseKey);
    return { course: entry, roster: await loadRoster(entry) };
  };

  const loadAssignment = async (courseKey: string, key: string): Promise<Assignment> => {
    const found = (await stores.courses.assignments(courseKey)).find((row) => row.key === key);
    if (found === undefined) {
      throw new NotFoundError('assignment', `${courseKey}/${key}`);
    }
    return found;
  };

  const progressOf = async (
    scope: CourseScope,
    entry: Assignment,
    enrolment: Enrolment,
    on: CivilDate,
    ladder: Ladder,
  ): Promise<AssignmentProgress> => {
    const context = await drill.context(scope.course.language);
    const deck = await drill.deck(scope.course.language, entry.deckKey);
    const ledger =
      (await stores.ledgers.get(enrolment.learnerId)) ?? emptyLedger(enrolment.learnerId);
    const attempts = await stores.attempts.listByLearner(enrolment.learnerId);
    return assignmentProgress(context, entry, deck, enrolment, ledger, attempts, ladder, on);
  };

  return {
    scopeOf,
    get: loadCourse,
    list: () => stores.courses.list(),

    async create(actor, input) {
      requirePermission(actor, 'edit-course', {
        course: course(input),
        roster: { courseKey: input.key, enrolments: [] },
      });
      const built = course(input);
      await stores.courses.put(built);
      return built;
    },

    async enrol(actor, courseKey, input) {
      const scope = await scopeOf(courseKey);
      requirePermission(actor, 'edit-course', scope);
      const enrolment = enrol(scope.course, input);
      await stores.courses.putEnrolment(enrolment);
      return enrolment;
    },

    async withdrawLearner(actor, courseKey, learnerId) {
      const scope = await scopeOf(courseKey);
      requirePermission(actor, 'edit-course', scope);
      const existing = enrolmentOf(scope.roster, learnerId);
      if (existing === undefined) {
        throw new NotFoundError('enrolment', `${courseKey}/${learnerId}`);
      }
      const gone = withdraw(existing);
      await stores.courses.putEnrolment(gone);
      return gone;
    },

    async addAssignment(actor, courseKey, input) {
      const scope = await scopeOf(courseKey);
      requirePermission(actor, 'edit-course', scope);
      const bundle = await stores.languages.get(scope.course.language);
      if (bundle === undefined) {
        throw new NotFoundError('language', scope.course.language);
      }
      const built = assignment(bundle.schema, scope.course, input);
      await stores.courses.putAssignment(built);
      return built;
    },

    assignments: (courseKey) => stores.courses.assignments(courseKey),

    async progressFor(actor, courseKey, assignmentKey, learnerId, on, ladder) {
      const scope = await scopeOf(courseKey);
      requireReadLearner(actor, learnerId, scope);
      const entry = await loadAssignment(courseKey, assignmentKey);
      const enrolment = enrolmentOf(scope.roster, learnerId);
      if (enrolment === undefined) {
        throw new NotFoundError('enrolment', `${courseKey}/${learnerId}`);
      }
      const day = on ?? clock.today();
      const progress = await progressOf(scope, entry, enrolment, day, ladder ?? STANDARD_LADDER);
      if (progress.met) {
        bus.publish({
          kind: 'assignment.met',
          at: clock.now(),
          actorId: actor.id,
          assignmentKey,
          learnerId,
          on: day,
        });
      }
      return progress;
    },

    async cohortProgress(actor, courseKey, assignmentKey, on, ladder) {
      const scope = await scopeOf(courseKey);
      requirePermission(actor, 'read-cohort', scope);
      const entry = await loadAssignment(courseKey, assignmentKey);
      const day = on ?? clock.today();
      const rows: AssignmentProgress[] = [];
      for (const learnerId of gradedLearners(scope.roster)) {
        const enrolment = enrolmentOf(scope.roster, learnerId)!;
        rows.push(await progressOf(scope, entry, enrolment, day, ladder ?? STANDARD_LADDER));
      }
      return rows;
    },
  };
}
