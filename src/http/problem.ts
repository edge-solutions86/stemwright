import type { ErrorCode, ErrorDetail } from '../domain/errors.js';
import { isStemwrightError } from '../domain/errors.js';
import type { Json } from '../infra/codec.js';
import type { Handler, HttpResponse } from './router.js';

/**
 * Turning a domain failure into a response.
 *
 * Two rules, and the second is the one that matters. Every error the domain
 * raises maps to a status and keeps its message, because the caller sent
 * something wrong and deserves to know what. Everything else becomes a bare
 * 500 with a fixed message, because an unexpected error's message is a stack
 * trace, a file path or a connection string, and none of those belong on a
 * wire that a learner is on the other end of.
 */
export interface Problem {
  readonly code: string;
  readonly message: string;
  readonly details?: readonly ErrorDetail[];
}

const STATUS_BY_CODE: Record<ErrorCode, number> = {
  VALIDATION: 400,
  NOT_FOUND: 404,
  CONFLICT: 409,
  POLICY: 422,
  FORBIDDEN: 403,
  INTEGRITY: 500,
  UNSUPPORTED: 501,
};

export const INTERNAL_MESSAGE = 'the request could not be completed';

export function statusFor(code: ErrorCode): number {
  return STATUS_BY_CODE[code];
}

export function toProblem(error: unknown): { status: number; problem: Problem } {
  if (!isStemwrightError(error)) {
    return { status: 500, problem: { code: 'INTERNAL', message: INTERNAL_MESSAGE } };
  }
  if (error.code === 'INTEGRITY') {
    return { status: 500, problem: { code: 'INTEGRITY', message: INTERNAL_MESSAGE } };
  }
  return {
    status: statusFor(error.code),
    problem: {
      code: error.code,
      message: error.message,
      ...(error.details.length === 0 ? {} : { details: error.details }),
    },
  };
}

export function problemResponse(error: unknown): HttpResponse {
  const { status, problem } = toProblem(error);
  return { status, body: problem as unknown as Json };
}

/**
 * Wraps a handler so nothing throws past it. The reporter is called with the
 * original error, so an operator still sees the stack that the client does not.
 */
export function withProblems(
  handler: Handler,
  report: (error: unknown) => void = () => undefined,
): Handler {
  return async (request) => {
    try {
      return await handler(request);
    } catch (error) {
      if (!isStemwrightError(error)) {
        report(error);
      }
      return problemResponse(error);
    }
  };
}

export function isServerProblem(status: number): boolean {
  return status >= 500;
}

export function isClientProblem(status: number): boolean {
  return status >= 400 && status < 500;
}

export function describeProblem(status: number, problem: Problem): string {
  const count = problem.details?.length ?? 0;
  const tail = count === 0 ? '' : ` (${count} details)`;
  return `${status} ${problem.code}: ${problem.message}${tail}`;
}
