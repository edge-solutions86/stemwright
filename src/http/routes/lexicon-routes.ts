import { ValidationError } from '../../domain/errors.js';
import { citationForm } from '../../domain/lexicon/lexeme.js';
import type { Lexeme } from '../../domain/lexicon/lexeme.js';
import { stemSet } from '../../domain/lexicon/stem.js';
import type { StemInput } from '../../domain/lexicon/stem.js';
import type { Json } from '../../infra/codec.js';
import {
  encodeStems,
  readArray,
  readOptionalString,
  readString,
  readStringArray,
  requireObject,
} from '../../infra/codec.js';
import type { AccountDirectory, AuthedRequest } from '../auth.js';
import { requireRole, withActor } from '../auth.js';
import type { Route } from '../router.js';
import { json, noContent, route } from '../router.js';
import type { LexiconService } from '../../app/services/lexicon-service.js';

/**
 * Lexicon routes. Reading is open to anybody with an account, because a word
 * list is not private and every learner needs one. Writing is behind both a
 * role check here and the permission check the service does, which is
 * deliberate belt and braces: the route check keeps a learner off the endpoint
 * entirely, and the service check is the one that would still hold if this file
 * were wired up wrong.
 */
export interface LexiconRouteDeps {
  readonly directory: AccountDirectory;
  readonly lexicon: LexiconService;
}

export function lexemeJson(entry: Lexeme): Json {
  return {
    id: entry.id,
    lemma: entry.lemma,
    citation: citationForm(entry),
    language: entry.language,
    classKey: entry.classKey,
    homograph: entry.homograph,
    glosses: [...entry.glosses],
    tags: [...entry.tags],
    stems: encodeStems(entry.stems),
    inherent: entry.inherent.pairs.map((pair) => `${pair.dimension}=${pair.value}`).join(';'),
  };
}

function stemsFrom(body: Record<string, Json>): ReturnType<typeof stemSet> {
  const rows = readArray(body, 'body', 'stems');
  const inputs: StemInput[] = rows.map((entry, index) => {
    const row = requireObject(`body.stems.${index}`, entry);
    const note = readOptionalString(row, `body.stems.${index}`, 'note');
    return {
      role: readString(row, `body.stems.${index}`, 'role'),
      form: readString(row, `body.stems.${index}`, 'form'),
      suppletive: row['suppletive'] === true,
      ...(note === undefined ? {} : { note }),
    };
  });
  if (inputs.length === 0) {
    throw ValidationError.field('body.stems', 'must hold at least one stem');
  }
  return stemSet(inputs);
}

function inherentFrom(body: Record<string, Json>): Record<string, string> {
  const raw = body['inherent'];
  if (raw === undefined || raw === null) {
    return {};
  }
  const row = requireObject('body.inherent', raw);
  const out: Record<string, string> = {};
  for (const [key, value] of Object.entries(row)) {
    if (typeof value !== 'string') {
      throw ValidationError.field(`body.inherent.${key}`, 'is not a string');
    }
    out[key] = value;
  }
  return out;
}

export function lexiconRoutes(deps: LexiconRouteDeps): Route[] {
  const list = async (request: AuthedRequest) => {
    const language = request.params['language']!;
    const term = request.query['q'];
    const entries =
      term === undefined
        ? [...(await deps.lexicon.load(language)).byId.values()]
        : await deps.lexicon.search(language, term);
    return json(200, entries.map(lexemeJson));
  };

  const read = async (request: AuthedRequest) =>
    json(200, lexemeJson(await deps.lexicon.get(request.params['lexemeId']!)));

  const create = async (request: AuthedRequest) => {
    const body = requireObject('body', request.body);
    const built = await deps.lexicon.add(request.actor, request.params['language']!, {
      lemma: readString(body, 'body', 'lemma'),
      classKey: readString(body, 'body', 'classKey'),
      stems: stemsFrom(body),
      inherent: inherentFrom(body),
      glosses: body['glosses'] === undefined ? [] : readStringArray(body, 'body', 'glosses'),
      tags: body['tags'] === undefined ? [] : readStringArray(body, 'body', 'tags'),
    });
    return json(201, lexemeJson(built));
  };

  const remove = async (request: AuthedRequest) => {
    await deps.lexicon.remove(request.actor, request.params['lexemeId']!);
    return noContent();
  };

  return [
    route('GET', '/languages/:language/lexemes', withActor(deps.directory, list)),
    route('GET', '/lexemes/:lexemeId', withActor(deps.directory, read)),
    route(
      'POST',
      '/languages/:language/lexemes',
      withActor(deps.directory, requireRole(['instructor', 'admin'], create)),
    ),
    route(
      'DELETE',
      '/lexemes/:lexemeId',
      withActor(deps.directory, requireRole(['instructor', 'admin'], remove)),
    ),
  ];
}
