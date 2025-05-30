/**
 * A seeded generator, so the property tests are properties rather than flakes.
 * Every run of the suite draws the same values, and a failure can be reproduced
 * by reading the seed out of the message.
 *
 * This is deliberately not a fuzzing library. The point is to cover a lot of
 * ordinary inputs cheaply, not to find adversarial ones, and a dependency that
 * shrinks counterexamples would be more machinery than the properties here are
 * worth.
 */
export interface Random {
  next(): number;
  int(lowest: number, highest: number): number;
  pick<T>(items: readonly T[]): T;
  sample<T>(items: readonly T[], count: number): T[];
  shuffle<T>(items: readonly T[]): T[];
  bool(chance?: number): boolean;
  word(minimum?: number, maximum?: number): string;
}

const LETTERS = 'abcdefghiklmnoprstuvx';

export function seeded(seed: number): Random {
  let state = seed >>> 0 || 0x9e3779b9;

  const next = (): number => {
    state ^= state << 13;
    state >>>= 0;
    state ^= state >>> 17;
    state ^= state << 5;
    state >>>= 0;
    return state / 0x1_0000_0000;
  };

  const int = (lowest: number, highest: number): number =>
    lowest + Math.floor(next() * (highest - lowest + 1));

  const pick = <T>(items: readonly T[]): T => {
    if (items.length === 0) {
      throw new Error('cannot pick from an empty list');
    }
    return items[int(0, items.length - 1)]!;
  };

  const shuffle = <T>(items: readonly T[]): T[] => {
    const out = [...items];
    for (let index = out.length - 1; index > 0; index -= 1) {
      const swap = int(0, index);
      const held = out[index]!;
      out[index] = out[swap]!;
      out[swap] = held;
    }
    return out;
  };

  return {
    next,
    int,
    pick,
    shuffle,
    sample: <T>(items: readonly T[], count: number) => shuffle(items).slice(0, count),
    bool: (chance = 0.5) => next() < chance,
    word: (minimum = 3, maximum = 8) =>
      Array.from({ length: int(minimum, maximum) }, () => pick([...LETTERS])).join(''),
  };
}

/** Runs a property over a fixed number of draws and reports the first failure. */
export function forEachDraw(
  count: number,
  seed: number,
  body: (random: Random, draw: number) => void,
): void {
  for (let draw = 0; draw < count; draw += 1) {
    const used = seed + draw * 7919;
    try {
      body(seeded(used), draw);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      throw new Error(`property failed on draw ${draw} with seed ${used}: ${message}`);
    }
  }
}
