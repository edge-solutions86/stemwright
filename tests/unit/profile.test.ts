import { describe, expect, it } from 'vitest';
import { featureBundle, parseBundleKey } from '../../src/domain/features/bundle.js';
import {
  holesIn,
  isTotal,
  literalSlots,
  paradigmProfile,
  readBundle,
  requireSlot,
  slotBundles,
  slotCount,
  slotFor,
  stemRolesUsed,
  withSlot,
} from '../../src/domain/paradigm/profile.js';
import { problemMessages } from '../support/problems.js';
import { firstDeclension, latinNoun, secondNeuter } from '../support/profiles.js';

describe('readBundle', () => {
  it('reads the dotted notation', () => {
    expect(readBundle(latinNoun, 'gen.pl', 'x')).toEqual(
      featureBundle(latinNoun, { case: 'gen', number: 'pl' }),
    );
  });

  it('reads a canonical key', () => {
    expect(readBundle(latinNoun, 'case=gen;number=pl', 'x')).toEqual(
      parseBundleKey(latinNoun, 'case=gen;number=pl'),
    );
  });
});

describe('paradigmProfile', () => {
  it('keys every slot by its canonical bundle', () => {
    expect(slotCount(firstDeclension)).toBe(10);
    expect(slotBundles(firstDeclension)[0]).toBe('case=nom;number=sg');
  });

  it('defaults the stem role to base', () => {
    expect(stemRolesUsed(firstDeclension)).toEqual(['base']);
  });

  it('defaults the label to the key', () => {
    expect(secondNeuter.label).toBe('second-neuter');
    expect(firstDeclension.label).toBe('First declension');
  });

  it('takes the language from the schema', () => {
    expect(firstDeclension.language).toBe('latin');
  });

  it('refuses an empty slot list', () => {
    expect(
      problemMessages(() => paradigmProfile(latinNoun, { key: 'x', classKey: 'y', slots: [] })),
    ).toEqual(['slots must hold at least one slot']);
  });

  it('refuses a slot that leaves a required dimension open', () => {
    expect(
      problemMessages(() =>
        paradigmProfile(latinNoun, {
          key: 'x',
          classKey: 'y',
          slots: [{ bundle: 'gen', affix: 'ae' }],
        }),
      ),
    ).toEqual(['slots.0 does not fix every required dimension: gen']);
  });

  it('refuses the same bundle twice however it was spelled', () => {
    expect(
      problemMessages(() =>
        paradigmProfile(latinNoun, {
          key: 'x',
          classKey: 'y',
          slots: [
            { bundle: 'gen.sg', affix: 'ae' },
            { bundle: 'case=gen;number=sg', affix: 'ai' },
          ],
        }),
      ),
    ).toEqual(['slots.1 repeats the bundle case=gen;number=sg']);
  });

  it('refuses a slot with neither an affix nor a literal', () => {
    expect(
      problemMessages(() =>
        paradigmProfile(latinNoun, { key: 'x', classKey: 'y', slots: [{ bundle: 'gen.sg' }] }),
      ),
    ).toEqual(['slots.0 must give either an affix or a literal form']);
  });

  it('accepts an empty affix, which is a bare stem form', () => {
    const profile = paradigmProfile(latinNoun, {
      key: 'x',
      classKey: 'y',
      slots: [{ bundle: 'nom.sg', affix: '' }],
    });
    const bundle = featureBundle(latinNoun, { case: 'nom', number: 'sg' });
    expect(requireSlot(profile, bundle).affix).toBe('');
  });
});

describe('slotFor', () => {
  it('finds the slot for a bundle', () => {
    const bundle = featureBundle(latinNoun, { case: 'gen', number: 'pl' });
    expect(slotFor(firstDeclension, bundle)?.affix).toBe('arum');
  });

  it('gives nothing for a bundle the profile does not cover', () => {
    const partial = featureBundle(latinNoun, { case: 'gen' });
    expect(slotFor(firstDeclension, partial)).toBeUndefined();
    expect(() => requireSlot(firstDeclension, partial)).toThrow(/has no slot in profile/);
  });
});

describe('literal slots', () => {
  const withLiteral = paradigmProfile(latinNoun, {
    key: 'vis',
    classKey: 'third-declension',
    slots: [
      { bundle: 'nom.sg', literal: 'vis', note: 'the paradigm is defective in the singular' },
      { bundle: 'acc.sg', literal: 'vim' },
      { bundle: 'abl.sg', literal: 'vi' },
      { bundle: 'nom.pl', affix: 'es' },
      { bundle: 'gen.pl', affix: 'ium' },
      { bundle: 'dat.pl', affix: 'ibus' },
      { bundle: 'acc.pl', affix: 'is' },
      { bundle: 'abl.pl', affix: 'ibus' },
    ],
  });

  it('lists the slots that state a form outright', () => {
    expect(literalSlots(withLiteral).map((slot) => slot.literal)).toEqual(['vis', 'vim', 'vi']);
  });

  it('keeps a note on the slot that carries one', () => {
    expect(literalSlots(withLiteral)[0]?.note).toBe('the paradigm is defective in the singular');
  });

  it('finds the holes the defective paradigm leaves', () => {
    expect(holesIn(latinNoun, withLiteral)).toEqual(['case=gen;number=sg', 'case=dat;number=sg']);
    expect(isTotal(latinNoun, withLiteral)).toBe(false);
  });
});

describe('coverage', () => {
  it('calls a full profile total', () => {
    expect(isTotal(latinNoun, firstDeclension)).toBe(true);
    expect(holesIn(latinNoun, firstDeclension)).toEqual([]);
  });
});

describe('withSlot', () => {
  const genSg = featureBundle(latinNoun, { case: 'gen', number: 'sg' });

  it('replaces a slot the profile already had', () => {
    const grown = withSlot(latinNoun, secondNeuter, { bundle: 'gen.sg', affix: 'ii' });
    expect(slotCount(grown)).toBe(10);
    expect(slotFor(grown, genSg)?.affix).toBe('ii');
  });

  it('leaves the original profile alone', () => {
    withSlot(latinNoun, secondNeuter, { bundle: 'gen.sg', affix: 'ii' });
    expect(slotFor(secondNeuter, genSg)?.affix).toBe('i');
  });
});
