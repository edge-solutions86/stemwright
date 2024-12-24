import { recordingBus } from '../../src/app/events.js';
import type { LanguageBundle, Stores } from '../../src/app/ports.js';
import { fixedClock } from '../../src/app/ports.js';
import { memoryStores } from '../../src/infra/memory.js';
import { NO_RULES } from '../../src/domain/paradigm/realise.js';
import { parseCivilDate } from '../../src/domain/time/civil.js';
import { latinBook } from './book.js';
import { latinClasses } from './classes.js';
import { ager, bellum, puella, rex } from './drill.js';
import { latinOrtho } from './ortho.js';
import { latinNoun } from './profiles.js';

export const TODAY = parseCivilDate('2024-10-21');

export const latinBundle: LanguageBundle = {
  language: 'latin',
  schema: latinNoun,
  classes: latinClasses,
  ortho: latinOrtho,
  rules: NO_RULES,
};

export async function seededStores(): Promise<Stores> {
  const stores = memoryStores();
  await stores.languages.put(latinBundle);
  await stores.books.put(latinBook);
  for (const entry of [puella, bellum, ager, rex]) {
    await stores.lexemes.put(entry);
  }
  return stores;
}

export function testHarness() {
  return {
    bus: recordingBus(),
    clock: fixedClock(new Date('2024-10-21T09:00:00Z'), TODAY),
  };
}
