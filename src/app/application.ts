import { createCourseService } from './services/course-service.js';
import type { CourseService } from './services/course-service.js';
import { createDeckService } from './services/deck-service.js';
import type { DeckService } from './services/deck-service.js';
import { createDrillService } from './services/drill-service.js';
import type { DrillService } from './services/drill-service.js';
import { createLexiconService } from './services/lexicon-service.js';
import type { LexiconService } from './services/lexicon-service.js';
import { createProfileService } from './services/profile-service.js';
import type { ProfileService } from './services/profile-service.js';
import { createReportService } from './services/report-service.js';
import type { ReportService } from './services/report-service.js';
import { createEventBus } from './events.js';
import type { EventBus } from './events.js';
import { systemClock } from './ports.js';
import type { Clock, Stores } from './ports.js';

/**
 * One place that knows how the services fit together. Wiring is worth having in
 * a file of its own because the order is not arbitrary: the drill service is
 * built first and handed to the two services that need to resolve a context
 * through it, and every one of them takes the same clock and the same bus.
 *
 * Sharing the clock is the part that matters. Two clocks in one process is two
 * ideas of what day it is, and a queue built on one and marked against the
 * other is a bug nobody would find from the symptom.
 */
export interface Application {
  readonly stores: Stores;
  readonly bus: EventBus;
  readonly clock: Clock;
  readonly lexicon: LexiconService;
  readonly profiles: ProfileService;
  readonly decks: DeckService;
  readonly drill: DrillService;
  readonly courses: CourseService;
  readonly reports: ReportService;
}

export interface ApplicationOptions {
  readonly bus?: EventBus;
  readonly clock?: Clock;
}

export function createApplication(stores: Stores, options: ApplicationOptions = {}): Application {
  const bus = options.bus ?? createEventBus();
  const clock = options.clock ?? systemClock();

  const drill = createDrillService(stores, bus, clock);

  return {
    stores,
    bus,
    clock,
    drill,
    lexicon: createLexiconService(stores, bus, clock),
    profiles: createProfileService(stores, bus, clock),
    decks: createDeckService(stores, clock, drill),
    courses: createCourseService(stores, bus, clock, drill),
    reports: createReportService(stores, clock, drill),
  };
}

/**
 * Subscribes the handful of listeners a running deployment wants. It is
 * separate from the wiring so a test can build an application without a
 * console full of log lines, and so the list of things that happen on an event
 * is readable in one place.
 */
export function attachLogging(app: Application, write: (line: string) => void): () => void {
  const subscriptions = [
    app.bus.on('cell.suspended', (event) => {
      write(`suspended ${event.cellId} for ${event.learnerId} after ${event.lapses} lapses`);
    }),
    app.bus.on('profile.published', (event) => {
      write(
        `published ${event.profileKey} revision ${event.revisionId} from ${event.effectiveFrom}`,
      );
    }),
    app.bus.on('session.closed', (event) => {
      write(`session ${event.sessionId} closed with ${event.answers} answers`);
    }),
  ];

  return () => {
    for (const subscription of subscriptions) {
      subscription.unsubscribe();
    }
  };
}
