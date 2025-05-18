import { describe, expect, it } from 'vitest';
import { ValidationError } from '../../src/domain/errors.js';
import { slotBundles, slotCount, slotFor } from '../../src/domain/paradigm/profile.js';
import { twinsOf } from '../../src/domain/paradigm/syncretism.js';
import { featureBundle } from '../../src/domain/features/bundle.js';
import { parseParadigmFile, writeParadigmFile } from '../../src/io/paradigm-file.js';
import { problemMessages } from '../support/problems.js';
import { latinNoun } from '../support/profiles.js';

const parse = (text: string) => parseParadigmFile(latinNoun, text);

const source = [
  '# the first declension',
  'profile first-declension',
  'class   first-declension',
  'label   First declension',
  '',
  'nom.sg  -a',
  'gen.sg  -ae',
  'dat.sg  -ae',
  'acc.sg  -am',
  'abl.sg  -a',
  'nom.pl  -ae',
  'gen.pl  -arum',
  'dat.pl  -is',
  'acc.pl  -as',
  'abl.pl  -is',
  '',
  'syncretism ae: gen.sg dat.sg nom.pl',
  '',
].join('\n');

describe('parseParadigmFile', () => {
  it('reads the header', () => {
    const file = parse(source);
    expect(file.profile.key).toBe('first-declension');
    expect(file.profile.classKey).toBe('first-declension');
    expect(file.profile.label).toBe('First declension');
  });

  it('reads every slot', () => {
    expect(slotCount(parse(source).profile)).toBe(10);
    expect(slotBundles(parse(source).profile)[0]).toBe('case=nom;number=sg');
  });

  it('strips the leading dash from an ending', () => {
    const bundle = featureBundle(latinNoun, { case: 'gen', number: 'pl' });
    expect(slotFor(parse(source).profile, bundle)?.affix).toBe('arum');
  });

  it('reads an ending with no dash', () => {
    const file = parse('profile p\nclass c\nnom.sg a\n');
    const bundle = featureBundle(latinNoun, { case: 'nom', number: 'sg' });
    expect(slotFor(file.profile, bundle)?.affix).toBe('a');
  });

  it('reads a literal form', () => {
    const file = parse('profile p\nclass c\nnom.sg = vis\n');
    const bundle = featureBundle(latinNoun, { case: 'nom', number: 'sg' });
    expect(slotFor(file.profile, bundle)?.literal).toBe('vis');
  });

  it('reads an ending on a named stem', () => {
    const file = parse('profile p\nclass c\nnom.sg oblique -is\n');
    const bundle = featureBundle(latinNoun, { case: 'nom', number: 'sg' });
    expect(slotFor(file.profile, bundle)).toMatchObject({ stemRole: 'oblique', affix: 'is' });
  });

  it('reads a syncretism group', () => {
    const file = parse(source);
    expect(twinsOf(file.syncretism, 'case=gen;number=sg')).toHaveLength(2);
  });

  it('gives an empty map when nothing is declared', () => {
    expect(parse('profile p\nclass c\nnom.sg -a\n').syncretism.groups).toEqual([]);
  });

  it('ignores comments and blank lines', () => {
    const text = '# a note\n\nprofile p\n\nclass c\n# another\nnom.sg -a\n';
    expect(slotCount(parse(text).profile)).toBe(1);
  });

  it('strips a trailing comment from a line', () => {
    const file = parse('profile p\nclass c\nnom.sg -a  # the usual ending\n');
    const bundle = featureBundle(latinNoun, { case: 'nom', number: 'sg' });
    expect(slotFor(file.profile, bundle)?.affix).toBe('a');
  });
});

describe('failures', () => {
  it('refuses a file with no profile line', () => {
    expect(problemMessages(() => parse('class c\nnom.sg -a\n'))).toContain(
      'profile is missing, the file needs a profile line',
    );
  });

  it('refuses a file with no class line', () => {
    expect(problemMessages(() => parse('profile p\nnom.sg -a\n'))).toContain(
      'class is missing, the file needs a class line',
    );
  });

  it('names the line of a slot with nothing after the bundle', () => {
    const messages = problemMessages(() => parse('profile p\nclass c\nnom.sg\n'));
    expect(messages[0]).toContain('line 3');
  });

  it('names the line of an equals sign with no form', () => {
    const messages = problemMessages(() => parse('profile p\nclass c\nnom.sg =\n'));
    expect(messages[0]).toContain('line 3');
  });

  it('refuses a syncretism line with no colon', () => {
    const messages = problemMessages(() =>
      parse('profile p\nclass c\nnom.sg -a\nsyncretism ae gen.sg dat.sg\n'),
    );
    expect(messages[0]).toContain('group name followed by a colon');
  });

  it('refuses a syncretism group with one member', () => {
    const messages = problemMessages(() =>
      parse('profile p\nclass c\nnom.sg -a\nsyncretism ae: gen.sg\n'),
    );
    expect(messages[0]).toContain('at least two bundles');
  });

  it('refuses a bundle the schema does not admit', () => {
    expect(() => parse('profile p\nclass c\ninst.sg -a\n')).toThrow(ValidationError);
  });

  it('collects several bad lines at once', () => {
    const messages = problemMessages(() => parse('profile p\nclass c\nnom.sg\ngen.sg\n'));
    expect(messages).toHaveLength(2);
  });
});

describe('writeParadigmFile', () => {
  it('round trips', () => {
    const file = parse(source);
    const rewritten = parse(writeParadigmFile(file));
    expect(slotCount(rewritten.profile)).toBe(slotCount(file.profile));
    expect(rewritten.syncretism.groups).toHaveLength(1);
  });

  it('writes the header first', () => {
    const lines = writeParadigmFile(parse(source)).split('\n');
    expect(lines[0]).toBe('profile first-declension');
    expect(lines[1]).toContain('first-declension');
  });

  it('writes a literal with an equals sign', () => {
    const file = parse('profile p\nclass c\nnom.sg = vis\n');
    expect(writeParadigmFile(file)).toContain('= vis');
  });

  it('names a stem role that is not the base', () => {
    const file = parse('profile p\nclass c\nnom.sg oblique -is\n');
    expect(writeParadigmFile(file)).toContain('oblique -is');
  });

  it('leaves the syncretism block out when there is none', () => {
    const file = parse('profile p\nclass c\nnom.sg -a\n');
    expect(writeParadigmFile(file)).not.toContain('syncretism');
  });
});
