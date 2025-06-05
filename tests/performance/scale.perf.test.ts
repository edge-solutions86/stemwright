import { describe, expect, it } from 'vitest';
import { deck } from '../../src/domain/drill/deck.js';
import { resolveMembership } from '../../src/domain/drill/membership.js';
import { drillPolicy } from '../../src/domain/drill/policy.js';
import { assembleQueue } from '../../src/domain/drill/queue.js';
import { buildLexicon } from '../../src/domain/lexicon/lexicon.js';
import { lexeme } from '../../src/domain/lexicon/lexeme.js';
import { stemSet } from '../../src/domain/lexicon/stem.js';
import { STANDARD_LADDER } from '../../src/domain/mastery/ladder.js';
import { buildLedger } from '../../src/domain/mastery/ledger.js';
import { newStrength, recordCorrect } from '../../src/domain/mastery/strength.js';
import { coverageByBundle } from '../../src/domain/report/coverage.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { latinBook } from '../support/book.js';
import { latinClasses } from '../support/classes.js';
import { latinNoun } from '../support/profiles.js';

/**
 * Timing checks, kept out of `npm test` and run as their own job.
 *
 * The numbers here are ceilings rather than targets, and they are loose on
 * purpose: a shared runner under load is several times slower than a laptop,
 * and a timing test tight enough to be interesting is a timing test that fails
 * for reasons that have nothing to do with the change under review.
 *
 * What they are actually for is catching a change of complexity. Queue assembly
 * over four thousand cells taking two seconds is a different algorithm from one
 * taking forty milliseconds, and that difference survives any amount of runner
 * noise.
 */
const DAY = parseCivilDate('2024-10-21');
const LEXEME_COUNT = 400;

const context = { schema: latinNoun, classes: latinClasses };

function bigLexicon() {
  const entries = Array.from({ length: LEXEME_COUNT }, (_, index) =>
    lexeme(context, {
      lemma: `lemma${String(index).padStart(4, '0')}`,
      classKey: index % 2 === 0 ? 'first-declension' : 'second-declension.neuter',
      stems: stemSet([{ role: 'base', form: `stem${index}` }]),
      glosses: [`gloss ${index}`],
      tags: index % 3 === 0 ? ['core'] : ['extra'],
    }),
  );
  return buildLexicon('latin', entries);
}

function elapsed(body: () => void): number {
  const started = performance.now();
  body();
  return performance.now() - started;
}

describe('scale', () => {
  const lexicon = bigLexicon();
  const drillContext = { schema: latinNoun, classes: latinClasses, book: latinBook, lexicon };
  const everything = deck(latinNoun, { key: 'all' });

  it('expands four thousand cells from four hundred lexemes', () => {
    const membership = resolveMembership(drillContext, everything, DAY);
    expect(membership.cells.length).toBe(LEXEME_COUNT * 10);
  });

  it('resolves membership in well under a second', () => {
    const ms = elapsed(() => {
      resolveMembership(drillContext, everything, DAY);
    });
    expect(ms).toBeLessThan(1500);
  });

  it('assembles a queue over the whole set in well under a second', () => {
    const membership = resolveMembership(drillContext, everything, DAY);
    const ledger = buildLedger(
      'lrn_one',
      membership.cells
        .slice(0, 500)
        .map((cell) => recordCorrect(newStrength(cell.id), STANDARD_LADDER, DAY)),
    );
    const ms = elapsed(() => {
      assembleQueue(drillContext, membership, drillPolicy(), ledger, DAY);
    });
    expect(ms).toBeLessThan(1500);
  });

  it('reports coverage over the whole set in well under a second', () => {
    const membership = resolveMembership(drillContext, everything, DAY);
    const ledger = buildLedger('lrn_one', []);
    const ms = elapsed(() => {
      coverageByBundle(membership.cells, ledger, STANDARD_LADDER, DAY);
    });
    expect(ms).toBeLessThan(1500);
  });

  it('builds the lexicon indexes in well under a second', () => {
    const entries = [...lexicon.byId.values()];
    const ms = elapsed(() => {
      buildLexicon('latin', entries);
    });
    expect(ms).toBeLessThan(1000);
  });

  it('keeps queue assembly close to linear in the number of cells', () => {
    const membership = resolveMembership(drillContext, everything, DAY);
    const ledger = buildLedger('lrn_one', []);
    const half = { ...membership, cells: membership.cells.slice(0, membership.cells.length / 2) };

    const wholeMs = elapsed(() => {
      assembleQueue(drillContext, membership, drillPolicy(), ledger, DAY);
    });
    const halfMs = elapsed(() => {
      assembleQueue(drillContext, half, drillPolicy(), ledger, DAY);
    });

    // A quadratic assembler would be four times slower on twice the input. Ten
    // is loose enough for a cold runner and far under what a regression costs.
    expect(wholeMs).toBeLessThan(Math.max(50, halfMs * 10));
  });
});
