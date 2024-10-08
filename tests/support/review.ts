import type { ReviewSettings } from '../../src/domain/drill/review.js';
import { ladder } from '../../src/domain/mastery/ladder.js';
import { leechPolicy } from '../../src/domain/mastery/leech.js';
import { NO_RULES } from '../../src/domain/paradigm/realise.js';
import { latinOrtho } from './ortho.js';

export const flatLadder = ladder({ key: 'flat', rungs: [1, 3, 7, 16], spread: 0 });

export const reviewSettings: ReviewSettings = {
  ortho: latinOrtho,
  rules: NO_RULES,
  ladder: flatLadder,
  leech: leechPolicy({ threshold: 3 }),
};
