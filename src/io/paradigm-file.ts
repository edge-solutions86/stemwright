import { ProblemCollector, ValidationError } from '../domain/errors.js';
import type { FeatureSchema } from '../domain/features/schema.js';
import type { ParadigmProfile, ParadigmSlotInput } from '../domain/paradigm/profile.js';
import { paradigmProfile } from '../domain/paradigm/profile.js';
import type { SyncretismMap } from '../domain/paradigm/syncretism.js';
import { NO_SYNCRETISM, syncretismMap } from '../domain/paradigm/syncretism.js';
import type { SyncretismGroupInput } from '../domain/paradigm/syncretism.js';

/**
 * A text format for paradigms, because the people who write them are linguists
 * with a text editor rather than programmers with a json schema.
 *
 *     profile first-declension
 *     class   first-declension
 *     label   First declension
 *
 *     nom.sg  -a
 *     gen.sg  -ae
 *     nom.sg  = vis        a literal form rather than an ending
 *     gen.sg  aorist -os   an ending on a named stem
 *
 *     syncretism ae: gen.sg dat.sg nom.pl
 *
 * A line beginning with a hash is a comment and a blank line is nothing. Every
 * failure names its line number, because the person fixing it is looking at the
 * file in an editor with the line numbers switched on.
 */
export interface ParadigmFile {
  readonly profile: ParadigmProfile;
  readonly syncretism: SyncretismMap;
}

interface Header {
  key: string | undefined;
  classKey: string | undefined;
  label: string | undefined;
}

function splitLine(line: string): string[] {
  return line
    .trim()
    .split(/\s+/)
    .filter((part) => part !== '');
}

function slotFrom(parts: readonly string[], line: number): ParadigmSlotInput {
  const bundle = parts[0]!;
  const rest = parts.slice(1);

  if (rest.length === 0) {
    throw ValidationError.field(`line ${line}`, 'gives a bundle with nothing after it');
  }
  if (rest[0] === '=') {
    const literal = rest.slice(1).join(' ');
    if (literal === '') {
      throw ValidationError.field(`line ${line}`, 'has an equals sign with no form after it');
    }
    return { bundle, literal };
  }
  if (rest.length === 1) {
    return { bundle, affix: rest[0]!.replace(/^-/, '') };
  }
  return { bundle, stemRole: rest[0]!, affix: rest[1]!.replace(/^-/, '') };
}

function groupFrom(parts: readonly string[], line: number): SyncretismGroupInput {
  const head = parts[1];
  if (head === undefined || !head.endsWith(':')) {
    throw ValidationError.field(`line ${line}`, 'needs a group name followed by a colon');
  }
  const members = parts.slice(2);
  if (members.length < 2) {
    throw ValidationError.field(`line ${line}`, 'needs at least two bundles');
  }
  return { key: head.slice(0, -1), members };
}

export function parseParadigmFile(schema: FeatureSchema, text: string): ParadigmFile {
  const problems = new ProblemCollector();
  const header: Header = { key: undefined, classKey: undefined, label: undefined };
  const slots: ParadigmSlotInput[] = [];
  const groups: SyncretismGroupInput[] = [];

  for (const [index, raw] of text.split('\n').entries()) {
    const line = index + 1;
    const stripped = raw.split('#')[0] ?? '';
    const parts = splitLine(stripped);
    if (parts.length === 0) {
      continue;
    }

    switch (parts[0]) {
      case 'profile':
        header.key = parts[1];
        continue;
      case 'class':
        header.classKey = parts[1];
        continue;
      case 'label':
        header.label = parts.slice(1).join(' ');
        continue;
      case 'syncretism':
        problems.absorbFlat(`line ${line}`, () => groups.push(groupFrom(parts, line)));
        continue;
      default:
        problems.absorbFlat(`line ${line}`, () => slots.push(slotFrom(parts, line)));
        continue;
    }
  }

  if (header.key === undefined) {
    problems.add('profile', 'is missing, the file needs a profile line');
  }
  if (header.classKey === undefined) {
    problems.add('class', 'is missing, the file needs a class line');
  }
  problems.throwIfAny('the paradigm file is not usable');

  return {
    profile: paradigmProfile(schema, {
      key: header.key!,
      classKey: header.classKey!,
      ...(header.label === undefined || header.label === '' ? {} : { label: header.label }),
      slots,
    }),
    syncretism: groups.length === 0 ? NO_SYNCRETISM : syncretismMap(schema, groups),
  };
}

export function writeParadigmFile(file: ParadigmFile): string {
  const lines = [
    `profile ${file.profile.key}`,
    `class   ${file.profile.classKey}`,
    `label   ${file.profile.label}`,
    '',
  ];

  for (const slot of file.profile.slots.values()) {
    if (slot.literal !== undefined) {
      lines.push(`${slot.bundle} = ${slot.literal}`);
      continue;
    }
    const stem = slot.stemRole === 'base' ? '' : `${slot.stemRole} `;
    lines.push(`${slot.bundle} ${stem}-${slot.affix}`);
  }

  if (file.syncretism.groups.length > 0) {
    lines.push('');
    for (const group of file.syncretism.groups) {
      lines.push(`syncretism ${group.key}: ${group.members.join(' ')}`);
    }
  }

  return `${lines.join('\n')}\n`;
}
