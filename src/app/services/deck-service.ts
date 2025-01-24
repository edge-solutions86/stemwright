import { ConflictError, NotFoundError } from '../../domain/errors.js';
import type { Actor, CourseScope } from '../../domain/course/authz.js';
import { require as requirePermission } from '../../domain/course/authz.js';
import type { Deck, DeckInput, DeckScopeInput } from '../../domain/drill/deck.js';
import { deck, withScope } from '../../domain/drill/deck.js';
import { resolveMembership } from '../../domain/drill/membership.js';
import type { Membership } from '../../domain/drill/membership.js';
import { membershipWarnings } from '../../domain/drill/membership.js';
import type { CivilDate } from '../../domain/time/civil.js';
import { requireLanguage } from '../loaders.js';
import type { Clock, Stores } from '../ports.js';
import type { DrillService } from './drill-service.js';

/**
 * Deck editing, with one rule the domain deliberately does not enforce: a deck
 * that selects nothing is legal to build and refused on save.
 *
 * The domain has to allow it, because a deck can go empty later when a class is
 * renamed or a profile is retired, and a model that could not represent that
 * state could not report it either. The service is the right place to stop
 * somebody creating one on purpose.
 */
export interface DeckPreview {
  readonly deck: Deck;
  readonly membership: Membership;
  readonly warnings: readonly string[];
}

export interface DeckService {
  get(language: string, key: string): Promise<Deck>;
  list(language: string): Promise<Deck[]>;
  create(actor: Actor, scope: CourseScope, language: string, input: DeckInput): Promise<Deck>;
  rescope(
    actor: Actor,
    scope: CourseScope,
    language: string,
    key: string,
    change: DeckScopeInput,
  ): Promise<Deck>;
  remove(actor: Actor, scope: CourseScope, language: string, key: string): Promise<void>;
  preview(language: string, input: DeckInput, on?: CivilDate): Promise<DeckPreview>;
}

export function createDeckService(stores: Stores, clock: Clock, drill: DrillService): DeckService {
  const schemaOf = async (language: string) => (await requireLanguage(stores, language)).schema;

  const previewOf = async (
    language: string,
    input: DeckInput,
    on: CivilDate,
  ): Promise<DeckPreview> => {
    const built = deck(await schemaOf(language), input);
    const membership = resolveMembership(await drill.context(language), built, on);
    return { deck: built, membership, warnings: membershipWarnings(membership) };
  };

  const saveChecked = async (language: string, input: DeckInput, on: CivilDate): Promise<Deck> => {
    const preview = await previewOf(language, input, on);
    if (preview.membership.cells.length === 0) {
      throw new ConflictError(`deck ${input.key} selects no cells on ${on}`);
    }
    await stores.decks.put(preview.deck);
    return preview.deck;
  };

  return {
    async get(language, key) {
      const found = await stores.decks.get(language, key);
      if (found === undefined) {
        throw new NotFoundError('deck', `${language}/${key}`);
      }
      return found;
    },

    list: (language) => stores.decks.listByLanguage(language),

    async create(actor, scope, language, input) {
      requirePermission(actor, 'edit-deck', scope);
      if ((await stores.decks.get(language, input.key)) !== undefined) {
        throw new ConflictError(`deck ${language}/${input.key} already exists`);
      }
      return saveChecked(language, input, clock.today());
    },

    async rescope(actor, scope, language, key, change) {
      requirePermission(actor, 'edit-deck', scope);
      const existing = await stores.decks.get(language, key);
      if (existing === undefined) {
        throw new NotFoundError('deck', `${language}/${key}`);
      }
      const widened = withScope(await schemaOf(language), existing, change);
      return saveChecked(
        language,
        {
          key: widened.key,
          label: widened.label,
          scope: {
            classKeys: widened.scope.classKeys,
            tags: widened.scope.tags,
            lexemeIds: widened.scope.lexemeIds,
            cells: change.cells ?? undefined,
          },
          ...(widened.toleranceLevel === undefined
            ? {}
            : { toleranceLevel: widened.toleranceLevel }),
          ...(widened.note === undefined ? {} : { note: widened.note }),
        },
        clock.today(),
      );
    },

    async remove(actor, scope, language, key) {
      requirePermission(actor, 'edit-deck', scope);
      if ((await stores.decks.get(language, key)) === undefined) {
        throw new NotFoundError('deck', `${language}/${key}`);
      }
      await stores.decks.remove(language, key);
    },

    preview: (language, input, on) => previewOf(language, input, on ?? clock.today()),
  };
}
