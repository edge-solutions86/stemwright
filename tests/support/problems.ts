import { ValidationError } from '../../src/domain/errors.js';
import type { ErrorDetail } from '../../src/domain/errors.js';

/**
 * A validation failure carries a short summary and the real information in its
 * details, so a test that only matches the summary would pass against any
 * failure at all. These helpers make the details the thing being asserted.
 */
export function detailsOf(run: () => unknown): readonly ErrorDetail[] {
  try {
    run();
  } catch (error) {
    if (error instanceof ValidationError) {
      return error.details;
    }
    throw error;
  }
  throw new Error('expected a ValidationError and nothing was thrown');
}

export function problemPaths(run: () => unknown): string[] {
  return detailsOf(run).map((detail) => detail.path);
}

export function problemMessages(run: () => unknown): string[] {
  return detailsOf(run).map((detail) => `${detail.path} ${detail.message}`);
}
