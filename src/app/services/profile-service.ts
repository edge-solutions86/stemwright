import { ConflictError, NotFoundError } from '../../domain/errors.js';
import type { Actor } from '../../domain/course/authz.js';
import { require as requirePermission } from '../../domain/course/authz.js';
import type { ProfileBook, ResolvedProfile } from '../../domain/paradigm/book.js';
import {
  profileBook,
  profileFor,
  unboundClasses,
  withBinding,
  withHistory,
} from '../../domain/paradigm/book.js';
import type { ParadigmProfile } from '../../domain/paradigm/profile.js';
import type { ProfileRevision, RevisionHistory } from '../../domain/paradigm/revision.js';
import {
  latestRevision,
  revisionHistory,
  revisionsAcross,
  withRevision,
} from '../../domain/paradigm/revision.js';
import type { SyncretismMap } from '../../domain/paradigm/syncretism.js';
import { reconcileSyncretism } from '../../domain/paradigm/syncretism.js';
import type { CivilDate } from '../../domain/time/civil.js';
import { compareCivil } from '../../domain/time/civil.js';
import type { DayRange } from '../../domain/time/window.js';
import type { EventBus } from '../events.js';
import type { Clock, Stores } from '../ports.js';

/**
 * Publishing a paradigm. The one rule this service adds on top of the domain is
 * that a revision may not take effect in the past: a learner already graded
 * under the old profile would silently be regraded, and the attempt log would
 * then disagree with the mark they were shown.
 *
 * Backdating is possible, and it is a separate call with a separate permission
 * so that it can never happen by accident.
 */
export interface ProfileService {
  book(language: string): Promise<ProfileBook>;
  history(language: string, profileKey: string): Promise<RevisionHistory>;
  resolve(language: string, classKey: string, on: CivilDate): Promise<ResolvedProfile>;
  publish(
    actor: Actor,
    language: string,
    profile: ParadigmProfile,
    effectiveFrom: CivilDate,
    syncretism?: SyncretismMap,
    reason?: string,
  ): Promise<ProfileRevision>;
  bind(actor: Actor, language: string, classKey: string, profileKey: string): Promise<ProfileBook>;
  gaps(language: string): Promise<string[]>;
  disagreements(language: string, profileKey: string): Promise<string[]>;
  revisionsIn(language: string, profileKey: string, range: DayRange): Promise<ProfileRevision[]>;
}

export function createProfileService(stores: Stores, bus: EventBus, clock: Clock): ProfileService {
  const loadBook = async (language: string): Promise<ProfileBook> => {
    const found = await stores.books.get(language);
    if (found === undefined) {
      throw new NotFoundError('profile book', language);
    }
    return found;
  };

  const loadHistory = async (language: string, profileKey: string): Promise<RevisionHistory> => {
    const book = await loadBook(language);
    const found = book.histories.get(profileKey);
    if (found === undefined) {
      throw new NotFoundError('profile', profileKey);
    }
    return found;
  };

  const loadLanguage = async (language: string) => {
    const bundle = await stores.languages.get(language);
    if (bundle === undefined) {
      throw new NotFoundError('language', language);
    }
    return bundle;
  };

  return {
    book: loadBook,
    history: loadHistory,

    async resolve(language, classKey, on) {
      const bundle = await loadLanguage(language);
      const book = await loadBook(language);
      const resolved = profileFor(book, bundle.classes, classKey, on);
      if (resolved === undefined) {
        throw new NotFoundError('profile', `${classKey} on ${on}`);
      }
      return resolved;
    },

    async publish(actor, language, profile, effectiveFrom, syncretism, reason) {
      requirePermission(actor, 'publish-profile');
      if (compareCivil(effectiveFrom, clock.today()) < 0) {
        throw new ConflictError(
          `a revision may not take effect before today, ${effectiveFrom} is in the past`,
        );
      }

      const book = await loadBook(language);
      const existing = book.histories.get(profile.key);
      const history =
        existing === undefined
          ? revisionHistory(profile.key, [
              {
                effectiveFrom,
                profile,
                ...(syncretism === undefined ? {} : { syncretism }),
                ...(reason === undefined ? {} : { reason }),
              },
            ])
          : withRevision(existing, {
              effectiveFrom,
              profile,
              ...(syncretism === undefined ? {} : { syncretism }),
              ...(reason === undefined ? {} : { reason }),
            });

      const grown =
        existing === undefined
          ? profileBook({
              language: book.language,
              histories: [...book.histories.values(), history],
              bindings: Object.fromEntries(book.bindings),
            })
          : withHistory(book, history);

      await stores.books.put(grown);
      const published = latestRevision(history);
      bus.publish({
        kind: 'profile.published',
        at: clock.now(),
        actorId: actor.id,
        profileKey: profile.key,
        revisionId: published.id,
        effectiveFrom,
      });
      return published;
    },

    async bind(actor, language, classKey, profileKey) {
      requirePermission(actor, 'publish-profile');
      const grown = withBinding(await loadBook(language), classKey, profileKey);
      await stores.books.put(grown);
      return grown;
    },

    async gaps(language) {
      const bundle = await loadLanguage(language);
      return unboundClasses(await loadBook(language), bundle.classes);
    },

    async disagreements(language, profileKey) {
      const history = await loadHistory(language, profileKey);
      const current = latestRevision(history);
      return reconcileSyncretism(current.profile, current.syncretism).map(
        (entry) =>
          `${entry.first} and ${entry.second} are ${entry.declared ? 'declared' : 'produced'} but not ${entry.produced ? 'declared' : 'produced'}`,
      );
    },

    async revisionsIn(language, profileKey, range) {
      return revisionsAcross(await loadHistory(language, profileKey), range);
    },
  };
}
