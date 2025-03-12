import { describe, expect, it } from 'vitest';
import {
  ConflictError,
  ForbiddenError,
  IntegrityError,
  NotFoundError,
  PolicyError,
  UnsupportedError,
  ValidationError,
} from '../../src/domain/errors.js';
import {
  INTERNAL_MESSAGE,
  describeProblem,
  isClientProblem,
  isServerProblem,
  problemResponse,
  statusFor,
  toProblem,
  withProblems,
} from '../../src/http/problem.js';
import { json } from '../../src/http/router.js';
import type { HttpRequest } from '../../src/http/router.js';

const request: HttpRequest = {
  method: 'GET',
  path: '/decks',
  params: {},
  query: {},
  headers: {},
  body: undefined,
};

describe('statusFor', () => {
  it('maps every code to a status', () => {
    expect(statusFor('VALIDATION')).toBe(400);
    expect(statusFor('FORBIDDEN')).toBe(403);
    expect(statusFor('NOT_FOUND')).toBe(404);
    expect(statusFor('CONFLICT')).toBe(409);
    expect(statusFor('POLICY')).toBe(422);
    expect(statusFor('INTEGRITY')).toBe(500);
    expect(statusFor('UNSUPPORTED')).toBe(501);
  });
});

describe('toProblem', () => {
  it('keeps the message of a validation failure', () => {
    const { status, problem } = toProblem(ValidationError.field('lemma', 'must not be blank'));
    expect(status).toBe(400);
    expect(problem.code).toBe('VALIDATION');
    expect(problem.message).toBe('lemma must not be blank');
  });

  it('carries the details when there are any', () => {
    const error = new ValidationError('two problems', {
      details: [
        { path: 'a', message: 'is wrong' },
        { path: 'b', message: 'is wrong' },
      ],
    });
    expect(toProblem(error).problem.details).toHaveLength(2);
  });

  it('leaves details out when there are none', () => {
    expect('details' in toProblem(new ConflictError('taken')).problem).toBe(false);
  });

  it('maps the other domain errors', () => {
    expect(toProblem(new NotFoundError('deck', 'all')).status).toBe(404);
    expect(toProblem(new ForbiddenError('no')).status).toBe(403);
    expect(toProblem(new PolicyError('budget', 'none left')).status).toBe(422);
    expect(toProblem(new UnsupportedError('later')).status).toBe(501);
  });

  it('hides the message of an integrity failure', () => {
    const { status, problem } = toProblem(new IntegrityError('journal line 7 is torn'));
    expect(status).toBe(500);
    expect(problem.message).toBe(INTERNAL_MESSAGE);
    expect(problem.message).not.toContain('journal');
  });

  it('hides everything about an error the domain did not raise', () => {
    const { status, problem } = toProblem(new TypeError('cannot read /etc/secret'));
    expect(status).toBe(500);
    expect(problem.code).toBe('INTERNAL');
    expect(problem.message).toBe(INTERNAL_MESSAGE);
  });

  it('hides a thrown value that is not an error at all', () => {
    expect(toProblem('a string').problem.code).toBe('INTERNAL');
  });
});

describe('problemResponse', () => {
  it('builds a response from an error', () => {
    const response = problemResponse(new NotFoundError('deck', 'all'));
    expect(response.status).toBe(404);
    expect(response.body).toMatchObject({ code: 'NOT_FOUND' });
  });
});

describe('withProblems', () => {
  it('passes a successful response straight through', async () => {
    const handler = withProblems(() => Promise.resolve(json(200, { ok: true })));
    expect((await handler(request)).status).toBe(200);
  });

  it('turns a thrown domain error into a response', async () => {
    const handler = withProblems(() => Promise.reject(new ForbiddenError('no')));
    expect((await handler(request)).status).toBe(403);
  });

  it('turns anything else into a bare 500', async () => {
    const handler = withProblems(() => Promise.reject(new TypeError('boom')));
    const response = await handler(request);
    expect(response.status).toBe(500);
    expect(response.body).toMatchObject({ code: 'INTERNAL' });
  });

  it('reports an unexpected error to the operator', async () => {
    const seen: unknown[] = [];
    const handler = withProblems(
      () => Promise.reject(new TypeError('boom')),
      (error) => seen.push(error),
    );
    await handler(request);
    expect(seen).toHaveLength(1);
    expect((seen[0] as Error).message).toBe('boom');
  });

  it('does not report an ordinary domain failure', async () => {
    const seen: unknown[] = [];
    const handler = withProblems(
      () => Promise.reject(new NotFoundError('deck', 'all')),
      (error) => seen.push(error),
    );
    await handler(request);
    expect(seen).toEqual([]);
  });
});

describe('status predicates', () => {
  it('splits client from server problems', () => {
    expect(isClientProblem(404)).toBe(true);
    expect(isClientProblem(500)).toBe(false);
    expect(isServerProblem(500)).toBe(true);
    expect(isServerProblem(404)).toBe(false);
    expect(isClientProblem(200)).toBe(false);
  });
});

describe('describeProblem', () => {
  it('reads a problem out', () => {
    expect(describeProblem(404, { code: 'NOT_FOUND', message: 'deck all was not found' })).toBe(
      '404 NOT_FOUND: deck all was not found',
    );
  });

  it('counts the details when there are any', () => {
    const problem = {
      code: 'VALIDATION',
      message: 'two problems',
      details: [
        { path: 'a', message: 'x' },
        { path: 'b', message: 'y' },
      ],
    };
    expect(describeProblem(400, problem)).toBe('400 VALIDATION: two problems (2 details)');
  });
});
