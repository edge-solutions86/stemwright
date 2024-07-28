/**
 * Every failure that crosses a module boundary in stemwright is one of these.
 *
 * The `code` is part of the contract: the http layer maps it to a status, the
 * cli maps it to an exit code, and the journal stores it verbatim when an
 * import run records why a row was rejected. Renaming a code is a breaking
 * change, so codes are screaming snake case and never reused for a new meaning.
 */
export type ErrorCode =
  | 'VALIDATION'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'POLICY'
  | 'FORBIDDEN'
  | 'INTEGRITY'
  | 'UNSUPPORTED';

export interface ErrorDetail {
  readonly path: string;
  readonly message: string;
}

export interface StemwrightErrorOptions {
  readonly details?: readonly ErrorDetail[];
  readonly cause?: unknown;
}

export class StemwrightError extends Error {
  readonly code: ErrorCode;
  readonly details: readonly ErrorDetail[];

  constructor(code: ErrorCode, message: string, options: StemwrightErrorOptions = {}) {
    super(message);
    this.name = new.target.name;
    this.code = code;
    this.details = options.details ?? [];
    if (options.cause !== undefined) {
      this.cause = options.cause;
    }
  }

  /** A shape safe to put on the wire or in the journal. */
  toJSON(): { code: ErrorCode; message: string; details: readonly ErrorDetail[] } {
    return { code: this.code, message: this.message, details: this.details };
  }
}

/** The caller sent something the domain cannot accept. */
export class ValidationError extends StemwrightError {
  constructor(message: string, options: StemwrightErrorOptions = {}) {
    super('VALIDATION', message, options);
  }

  static field(path: string, message: string): ValidationError {
    return new ValidationError(`${path} ${message}`, { details: [{ path, message }] });
  }
}

/** A record was addressed by an id that no store holds. */
export class NotFoundError extends StemwrightError {
  readonly kind: string;
  readonly id: string;

  constructor(kind: string, id: string) {
    super('NOT_FOUND', `${kind} ${id} was not found`);
    this.kind = kind;
    this.id = id;
  }
}

/** The write would collide with something already stored. */
export class ConflictError extends StemwrightError {
  constructor(message: string, options: StemwrightErrorOptions = {}) {
    super('CONFLICT', message, options);
  }
}

/** The request was well formed but a configured rule refused it. */
export class PolicyError extends StemwrightError {
  readonly rule: string;

  constructor(rule: string, message: string) {
    super('POLICY', message);
    this.rule = rule;
  }
}

/** The actor may not see or touch this. */
export class ForbiddenError extends StemwrightError {
  constructor(message: string) {
    super('FORBIDDEN', message);
  }
}

/** Stored state contradicts itself. Always a bug, never a user mistake. */
export class IntegrityError extends StemwrightError {
  constructor(message: string, options: StemwrightErrorOptions = {}) {
    super('INTEGRITY', message, options);
  }
}

/** A feature exists in the model but not in this build or this profile. */
export class UnsupportedError extends StemwrightError {
  constructor(message: string) {
    super('UNSUPPORTED', message);
  }
}

export function isStemwrightError(value: unknown): value is StemwrightError {
  return value instanceof StemwrightError;
}

export function errorCodeOf(value: unknown): ErrorCode | undefined {
  return isStemwrightError(value) ? value.code : undefined;
}

/**
 * Collects field level problems and throws once. Callers that validate several
 * fields should use this rather than throwing on the first bad one, because a
 * bulk import wants the whole list back in a single pass.
 */
export class ProblemCollector {
  private readonly collected: ErrorDetail[] = [];

  add(path: string, message: string): void {
    this.collected.push({ path, message });
  }

  addIf(condition: boolean, path: string, message: string): void {
    if (condition) {
      this.add(path, message);
    }
  }

  get size(): number {
    return this.collected.length;
  }

  problems(): readonly ErrorDetail[] {
    return [...this.collected];
  }

  throwIfAny(summary: string): void {
    if (this.collected.length === 0) {
      return;
    }
    throw new ValidationError(summary, { details: this.problems() });
  }

  /**
   * Runs something that validates, and folds whatever it threw into this
   * collector rather than letting it escape. Four modules were doing this by
   * hand, each with a slightly different idea of what to do with the details of
   * a nested failure, and one of them silently dropped them.
   *
   * A nested ValidationError contributes each of its own details under a
   * prefixed path. Anything else contributes one problem carrying its message,
   * because a non validation failure is a bug and hiding it would make the bug
   * look like bad input.
   */
  absorb<T>(path: string, run: () => T): T | undefined {
    try {
      return run();
    } catch (error) {
      if (error instanceof ValidationError && error.details.length > 0) {
        for (const detail of error.details) {
          this.add(`${path}.${detail.path}`, detail.message);
        }
        return undefined;
      }
      this.add(path, (error as Error).message);
      return undefined;
    }
  }

  /** Absorbs under the caller's own path rather than nesting a child path. */
  absorbFlat<T>(path: string, run: () => T): T | undefined {
    try {
      return run();
    } catch (error) {
      if (error instanceof ValidationError && error.details.length > 0) {
        for (const detail of error.details) {
          this.add(path, `${detail.path} ${detail.message}`);
        }
        return undefined;
      }
      this.add(path, (error as Error).message);
      return undefined;
    }
  }
}
