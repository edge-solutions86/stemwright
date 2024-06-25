import { ProblemCollector, NotFoundError } from '../errors.js';
import type { Id } from '../identifiers.js';
import { derivedId } from '../identifiers.js';
import type { CivilDate } from '../time/civil.js';
import { compareCivil } from '../time/civil.js';
import type { DayRange } from '../time/window.js';
import { eachDay } from '../time/window.js';
import type { ParadigmProfile } from './profile.js';
import type { SyncretismMap } from './syncretism.js';
import { NO_SYNCRETISM } from './syncretism.js';

/**
 * Profiles are never edited in place. A change publishes a new revision with a
 * date it takes effect from, and everything that grades an answer resolves the
 * revision in force at the moment of the answer rather than the newest one.
 *
 * That is the whole reason this module exists. A student who typed `puellae` in
 * March was right under the revision that ran in March. Re grading them in
 * October under October's syncretism map would change history, and a term
 * report that did so would disagree with the mark the student was shown.
 */
export interface ProfileRevision {
  readonly id: Id<'revision'>;
  readonly profileKey: string;
  readonly ordinal: number;
  readonly effectiveFrom: CivilDate;
  readonly profile: ParadigmProfile;
  readonly syncretism: SyncretismMap;
  readonly reason: string | undefined;
}

export interface ProfileRevisionInput {
  readonly effectiveFrom: CivilDate;
  readonly profile: ParadigmProfile;
  readonly syncretism?: SyncretismMap;
  readonly reason?: string;
}

export interface RevisionHistory {
  readonly profileKey: string;
  readonly revisions: readonly ProfileRevision[];
}

export function revisionIdFor(profileKey: string, ordinal: number): Id<'revision'> {
  return derivedId('revision', profileKey, String(ordinal));
}

/**
 * Revisions are given in any order and come back sorted. Two revisions may not
 * share a start date: which one was in force would then be undecidable, and a
 * silent pick is exactly the kind of thing that is invisible until a report
 * disagrees with a transcript.
 */
export function revisionHistory(
  profileKey: string,
  inputs: readonly ProfileRevisionInput[],
): RevisionHistory {
  const problems = new ProblemCollector();
  if (inputs.length === 0) {
    problems.add('revisions', 'must hold at least one revision');
  }

  const sorted = [...inputs].sort((a, b) => compareCivil(a.effectiveFrom, b.effectiveFrom));
  const revisions: ProfileRevision[] = [];

  for (const [index, input] of sorted.entries()) {
    const previous = sorted[index - 1];
    if (previous !== undefined && previous.effectiveFrom === input.effectiveFrom) {
      problems.add(
        `revisions.${index}`,
        `starts on ${input.effectiveFrom} and so does the one before it`,
      );
      continue;
    }
    if (input.profile.key !== profileKey) {
      problems.add(
        `revisions.${index}`,
        `carries profile ${input.profile.key} in the history of ${profileKey}`,
      );
      continue;
    }
    const ordinal = revisions.length + 1;
    revisions.push({
      id: revisionIdFor(profileKey, ordinal),
      profileKey,
      ordinal,
      effectiveFrom: input.effectiveFrom,
      profile: input.profile,
      syncretism: input.syncretism ?? NO_SYNCRETISM,
      reason: input.reason,
    });
  }

  problems.throwIfAny(`revision history for ${profileKey} is not usable`);
  return { profileKey, revisions };
}

export function latestRevision(history: RevisionHistory): ProfileRevision {
  const last = history.revisions[history.revisions.length - 1];
  if (last === undefined) {
    throw new NotFoundError('revision', history.profileKey);
  }
  return last;
}

export function firstRevision(history: RevisionHistory): ProfileRevision {
  const first = history.revisions[0];
  if (first === undefined) {
    throw new NotFoundError('revision', history.profileKey);
  }
  return first;
}

/**
 * The revision in force on a day, or nothing if the profile had not been
 * published yet. A caller that treats "nothing" as "use the newest" has
 * reintroduced the bug this module exists to prevent, so the absence is
 * returned rather than papered over.
 */
export function revisionInForce(
  history: RevisionHistory,
  on: CivilDate,
): ProfileRevision | undefined {
  let found: ProfileRevision | undefined;
  for (const revision of history.revisions) {
    if (compareCivil(revision.effectiveFrom, on) <= 0) {
      found = revision;
    } else {
      break;
    }
  }
  return found;
}

export function requireRevisionInForce(history: RevisionHistory, on: CivilDate): ProfileRevision {
  const found = revisionInForce(history, on);
  if (found === undefined) {
    throw new NotFoundError('revision', `${history.profileKey} on ${on}`);
  }
  return found;
}

export function revisionByOrdinal(
  history: RevisionHistory,
  ordinal: number,
): ProfileRevision | undefined {
  return history.revisions.find((revision) => revision.ordinal === ordinal);
}

export function revisionById(history: RevisionHistory, id: string): ProfileRevision | undefined {
  return history.revisions.find((revision) => revision.id === id);
}

/** Every revision that was in force on at least one day of the range, in order. */
export function revisionsAcross(history: RevisionHistory, range: DayRange): ProfileRevision[] {
  const seen = new Map<string, ProfileRevision>();
  for (const day of eachDay(range)) {
    const revision = revisionInForce(history, day);
    if (revision !== undefined && !seen.has(revision.id)) {
      seen.set(revision.id, revision);
    }
  }
  return [...seen.values()].sort((a, b) => a.ordinal - b.ordinal);
}

export function spansARevisionChange(history: RevisionHistory, range: DayRange): boolean {
  return revisionsAcross(history, range).length > 1;
}

/** The day a revision stopped being in force, or nothing if it still is. */
export function retiredOn(history: RevisionHistory, ordinal: number): CivilDate | undefined {
  const next = revisionByOrdinal(history, ordinal + 1);
  return next?.effectiveFrom;
}

export function withRevision(
  history: RevisionHistory,
  input: ProfileRevisionInput,
): RevisionHistory {
  const kept: ProfileRevisionInput[] = history.revisions.map((revision) => ({
    effectiveFrom: revision.effectiveFrom,
    profile: revision.profile,
    syncretism: revision.syncretism,
    ...(revision.reason === undefined ? {} : { reason: revision.reason }),
  }));
  return revisionHistory(history.profileKey, [...kept, input]);
}
