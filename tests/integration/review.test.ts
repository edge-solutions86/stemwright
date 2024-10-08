import { describe, expect, it } from 'vitest';
import { NOTHING_BURIED, isBuried } from '../../src/domain/drill/burial.js';
import { deck } from '../../src/domain/drill/deck.js';
import { resolveMembership } from '../../src/domain/drill/membership.js';
import { expectedFormFor, reviewAnswer } from '../../src/domain/drill/review.js';
import { creditRule } from '../../src/domain/mastery/credit.js';
import { emptyLedger, strengthFor } from '../../src/domain/mastery/ledger.js';
import { leechPolicy } from '../../src/domain/mastery/leech.js';
import { NotFoundError } from '../../src/domain/errors.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { drillContext, puella } from '../support/drill.js';
import { latinNoun } from '../support/profiles.js';
import { reviewSettings } from '../support/review.js';

const d = (value: string) => parseCivilDate(value);
const TERM = d('2024-10-01');

const membership = resolveMembership(drillContext, deck(latinNoun, { key: 'all' }), TERM);
const cellOf = (bundle: string, lexemeId = puella.id) =>
  membership.cells.find((cell) => cell.lexemeId === lexemeId && cell.bundle === bundle)!;

const genSg = cellOf('case=gen;number=sg');
const accSg = cellOf('case=acc;number=sg');

const ask = (
  given: string,
  cell = genSg,
  on = TERM,
  settings = reviewSettings,
  ledger = emptyLedger('lrn_one'),
  buried = NOTHING_BURIED,
) =>
  reviewAnswer(drillContext, settings, ledger, buried, {
    cell,
    given,
    on,
    learnerId: 'lrn_one',
    sessionId: 'sess_aaaaaa',
    sequence: 1,
    deckKey: 'all',
  });

describe('expectedFormFor', () => {
  it('realises the form from the revision in force', () => {
    expect(expectedFormFor(drillContext, reviewSettings, genSg, TERM).form).toBe('puellae');
  });

  it('refuses a day before the profile was published', () => {
    expect(() => expectedFormFor(drillContext, reviewSettings, genSg, d('2024-01-01'))).toThrow(
      NotFoundError,
    );
  });
});

describe('reviewAnswer', () => {
  it('marks a right answer and schedules it', () => {
    const result = ask('puellae');
    expect(result.match.verdict).toBe('exact');
    expect(result.expected).toBe('puellae');
    expect(strengthFor(result.ledger, genSg.id).dueOn).toBe('2024-10-02');
    expect(result.attempt.verdict).toBe('exact');
  });

  it('marks a wrong answer as a lapse', () => {
    const result = ask('puellarum');
    expect(result.match.verdict).toBe('wrong');
    expect(strengthFor(result.ledger, genSg.id).lapses).toBe(1);
  });

  it('holds a near miss at the same rung', () => {
    const result = ask('puellai');
    expect(result.match.verdict).toBe('near');
    expect(strengthFor(result.ledger, genSg.id).lapses).toBe(0);
    expect(strengthFor(result.ledger, genSg.id).correct).toBe(0);
  });

  it('copies the revision it graded against onto the attempt', () => {
    const result = ask('puellae');
    expect(result.attempt.revisionId).toBe(result.revisionId);
    expect(result.attempt.expected).toBe('puellae');
  });

  it('records the tolerance level it resolved', () => {
    const result = ask('puellae');
    expect(result.attempt.toleranceLevel).toBe('standard');
  });
});

describe('tolerance in the review', () => {
  it('takes a capital in its stride at the standard level', () => {
    expect(ask('Puellae').match.verdict).toBe('tolerated');
  });

  it('marks the same answer near at the exact level', () => {
    const result = reviewAnswer(
      drillContext,
      reviewSettings,
      emptyLedger('lrn_one'),
      NOTHING_BURIED,
      {
        cell: genSg,
        given: 'Puellae',
        on: TERM,
        learnerId: 'lrn_one',
        sessionId: 'sess_aaaaaa',
        sequence: 1,
        deckKey: 'all',
        deckTolerance: 'exact',
      },
    );
    expect(result.match.verdict).toBe('near');
  });

  it('lets a cell override tighten what the deck loosened', () => {
    const result = reviewAnswer(
      drillContext,
      reviewSettings,
      emptyLedger('lrn_one'),
      NOTHING_BURIED,
      {
        cell: genSg,
        given: 'Puellae',
        on: TERM,
        learnerId: 'lrn_one',
        sessionId: 'sess_aaaaaa',
        sequence: 1,
        deckKey: 'all',
        deckTolerance: 'standard',
        cellTolerance: 'exact',
      },
    );
    expect(result.attempt.toleranceLevel).toBe('exact');
  });
});

describe('twins', () => {
  it('buries the answered cell and its twins', () => {
    const result = ask('puellae');
    expect(isBuried(result.buried, genSg.id)).toBe(true);
    expect(isBuried(result.buried, cellOf('case=dat;number=sg').id)).toBe(true);
    expect(isBuried(result.buried, accSg.id)).toBe(false);
  });

  it('leaves burial alone when the settings switch it off', () => {
    const result = ask('puellae', genSg, TERM, { ...reviewSettings, buryTwins: false });
    expect(result.buried).toBe(NOTHING_BURIED);
  });

  it('passes credit to twins that have been introduced', () => {
    const first = ask('puellae', cellOf('case=dat;number=sg'));
    const second = reviewAnswer(drillContext, reviewSettings, first.ledger, NOTHING_BURIED, {
      cell: genSg,
      given: 'puellae',
      on: TERM,
      learnerId: 'lrn_one',
      sessionId: 'sess_aaaaaa',
      sequence: 2,
      deckKey: 'all',
    });
    expect(second.twinsCredited).toBe(1);
  });

  it('passes no credit to twins nobody has met', () => {
    expect(ask('puellae').twinsCredited).toBe(0);
  });

  it('passes no credit at all after a wrong answer', () => {
    const first = ask('puellae', cellOf('case=dat;number=sg'));
    const second = reviewAnswer(drillContext, reviewSettings, first.ledger, NOTHING_BURIED, {
      cell: genSg,
      given: 'nonsense',
      on: TERM,
      learnerId: 'lrn_one',
      sessionId: 'sess_aaaaaa',
      sequence: 2,
      deckKey: 'all',
    });
    expect(second.twinsCredited).toBe(0);
  });

  it('passes no credit when the rule shares nothing', () => {
    const first = ask('puellae', cellOf('case=dat;number=sg'));
    const settings = { ...reviewSettings, credit: creditRule({ share: 0 }) };
    const second = reviewAnswer(drillContext, settings, first.ledger, NOTHING_BURIED, {
      cell: genSg,
      given: 'puellae',
      on: TERM,
      learnerId: 'lrn_one',
      sessionId: 'sess_aaaaaa',
      sequence: 2,
      deckKey: 'all',
    });
    expect(second.twinsCredited).toBe(0);
  });

  it('buries a different set once a later revision is in force', () => {
    const bellumNom = membership.cells.find(
      (cell) => cell.bundle === 'case=nom;number=sg' && cell.lexemeId !== puella.id,
    )!;
    const early = ask('bellum', bellumNom, TERM);
    const late = ask('bellum', bellumNom, d('2025-03-01'));
    expect(early.buried.buried.size).toBe(1);
    expect(late.buried.buried.size).toBe(2);
  });
});

describe('leeches', () => {
  it('suspends a cell that lapses past the threshold', () => {
    let ledger = emptyLedger('lrn_one');
    let fired = false;
    for (let step = 1; step <= 3; step += 1) {
      const result = reviewAnswer(drillContext, reviewSettings, ledger, NOTHING_BURIED, {
        cell: genSg,
        given: 'nonsense',
        on: TERM,
        learnerId: 'lrn_one',
        sessionId: 'sess_aaaaaa',
        sequence: step,
        deckKey: 'all',
      });
      ledger = result.ledger;
      fired = result.leechFired;
    }
    expect(fired).toBe(true);
    expect(strengthFor(ledger, genSg.id).suspended).toBe(true);
  });

  it('does not fire below the threshold', () => {
    expect(ask('nonsense').leechFired).toBe(false);
  });

  it('leaves the schedule alone under the tag action', () => {
    const settings = {
      ...reviewSettings,
      leech: leechPolicy({ threshold: 1, action: 'tag' as const }),
    };
    const result = ask('nonsense', genSg, TERM, settings);
    expect(result.leechFired).toBe(true);
    expect(strengthFor(result.ledger, genSg.id).suspended).toBe(false);
  });
});
