import { NotFoundError } from '../../domain/errors.js';
import type { Actor } from '../../domain/course/authz.js';
import { require as requirePermission } from '../../domain/course/authz.js';
import type { Lexeme, LexemeInput } from '../../domain/lexicon/lexeme.js';
import { lexeme } from '../../domain/lexicon/lexeme.js';
import type { Lexicon } from '../../domain/lexicon/lexicon.js';
import { buildLexicon, lexemesByTag, lexemesUnderClass } from '../../domain/lexicon/lexicon.js';
import { normaliseInput } from '../../domain/text/normalise.js';
import type { EventBus } from '../events.js';
import type { Clock, LanguageBundle, Stores } from '../ports.js';

/**
 * The lexicon service. It is thin on purpose: every rule about what a lexeme
 * may be lives in the domain, and what is left here is loading what the domain
 * needs, checking who is asking, and telling the world afterwards.
 *
 * It rebuilds the whole `Lexicon` on every read. That is the right trade at a
 * few thousand lexemes and the wrong one at a million, and the note is here so
 * the next person changing it knows which side of that line the decision was
 * made on.
 */
export interface LexiconService {
  language(name: string): Promise<LanguageBundle>;
  load(language: string): Promise<Lexicon>;
  get(id: string): Promise<Lexeme>;
  add(actor: Actor, language: string, input: LexemeInput): Promise<Lexeme>;
  replace(actor: Actor, language: string, input: LexemeInput & { id: string }): Promise<Lexeme>;
  remove(actor: Actor, id: string): Promise<void>;
  search(language: string, term: string): Promise<Lexeme[]>;
  byClass(language: string, classKey: string): Promise<Lexeme[]>;
  byTag(language: string, tag: string): Promise<Lexeme[]>;
}

export function createLexiconService(stores: Stores, bus: EventBus, clock: Clock): LexiconService {
  const language = async (name: string): Promise<LanguageBundle> => {
    const bundle = await stores.languages.get(name);
    if (bundle === undefined) {
      throw new NotFoundError('language', name);
    }
    return bundle;
  };

  const load = async (name: string): Promise<Lexicon> =>
    buildLexicon(name, await stores.lexemes.listByLanguage(name));

  return {
    language,
    load,

    async get(id) {
      const found = await stores.lexemes.get(id);
      if (found === undefined) {
        throw new NotFoundError('lexeme', id);
      }
      return found;
    },

    async add(actor, name, input) {
      requirePermission(actor, 'edit-lexicon');
      const bundle = await language(name);
      const built = lexeme({ schema: bundle.schema, classes: bundle.classes }, input);
      await stores.lexemes.put(built);
      bus.publish({
        kind: 'lexeme.added',
        at: clock.now(),
        actorId: actor.id,
        lexemeId: built.id,
        language: name,
      });
      return built;
    },

    async replace(actor, name, input) {
      requirePermission(actor, 'edit-lexicon');
      const existing = await stores.lexemes.get(input.id);
      if (existing === undefined) {
        throw new NotFoundError('lexeme', input.id);
      }
      const bundle = await language(name);
      const built = lexeme({ schema: bundle.schema, classes: bundle.classes }, input);
      await stores.lexemes.put(built);
      return built;
    },

    async remove(actor, id) {
      requirePermission(actor, 'edit-lexicon');
      const existing = await stores.lexemes.get(id);
      if (existing === undefined) {
        throw new NotFoundError('lexeme', id);
      }
      await stores.lexemes.remove(id);
    },

    async search(name, term) {
      const needle = normaliseInput(term).toLowerCase();
      if (needle === '') {
        return [];
      }
      const entries = await stores.lexemes.listByLanguage(name);
      return entries.filter(
        (entry) =>
          entry.lemma.toLowerCase().includes(needle) ||
          entry.glosses.some((gloss) => gloss.toLowerCase().includes(needle)),
      );
    },

    async byClass(name, classKey) {
      const bundle = await language(name);
      return lexemesUnderClass(await load(name), bundle.classes, classKey);
    },

    async byTag(name, tag) {
      return [...lexemesByTag(await load(name), tag)];
    },
  };
}
