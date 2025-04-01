import { createServer as createNodeServer } from 'node:http';
import type { IncomingMessage, Server, ServerResponse } from 'node:http';
import { ValidationError } from '../domain/errors.js';
import type { Json } from '../infra/codec.js';
import { withProblems } from './problem.js';
import type { Handler, HttpRequest, Method } from './router.js';
import { parseQuery } from './router.js';

/**
 * The node adapter. It does four things and nothing else: read the body, build
 * the request the router understands, write the response, and make sure no
 * failure escapes as a hanging socket.
 *
 * A body larger than the cap is refused while it is still arriving rather than
 * after it has all been buffered, because the point of a cap that is enforced
 * after the fact is difficult to explain.
 */
export const DEFAULT_BODY_LIMIT = 1_000_000;

export interface ServerOptions {
  readonly bodyLimit?: number;
  readonly onError?: (error: unknown) => void;
  readonly onRequest?: (line: string) => void;
}

const METHODS: readonly Method[] = ['GET', 'POST', 'PUT', 'DELETE'];

export function isMethod(value: string): value is Method {
  return (METHODS as readonly string[]).includes(value);
}

export async function readBody(
  message: IncomingMessage,
  limit = DEFAULT_BODY_LIMIT,
): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    let size = 0;

    message.on('data', (chunk: Buffer) => {
      size += chunk.length;
      if (size > limit) {
        message.destroy();
        reject(new ValidationError(`the request body is larger than ${limit} bytes`));
        return;
      }
      chunks.push(chunk);
    });
    message.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    message.on('error', reject);
  });
}

export function parseBody(text: string): Json | undefined {
  if (text.trim() === '') {
    return undefined;
  }
  try {
    return JSON.parse(text) as Json;
  } catch {
    throw new ValidationError('the request body is not valid json');
  }
}

export function toHttpRequest(message: IncomingMessage, body: Json | undefined): HttpRequest {
  const method = (message.method ?? 'GET').toUpperCase();
  if (!isMethod(method)) {
    throw new ValidationError(`the method ${method} is not supported`);
  }
  const url = message.url ?? '/';
  const at = url.indexOf('?');
  const headers: Record<string, string> = {};
  for (const [key, value] of Object.entries(message.headers)) {
    if (typeof value === 'string') {
      headers[key] = value;
    }
  }

  return {
    method,
    path: at < 0 ? url : url.slice(0, at),
    params: {},
    query: at < 0 ? {} : parseQuery(url.slice(at)),
    headers,
    body,
  };
}

export function createServer(handler: Handler, options: ServerOptions = {}): Server {
  const guarded = withProblems(handler, options.onError ?? (() => undefined));

  return createNodeServer((message: IncomingMessage, reply: ServerResponse) => {
    void (async () => {
      const started = Date.now();
      let status = 500;
      try {
        const text = await readBody(message, options.bodyLimit ?? DEFAULT_BODY_LIMIT);
        const request = toHttpRequest(message, parseBody(text));
        const response = await guarded(request);
        status = response.status;
        const payload = response.status === 204 ? '' : JSON.stringify(response.body);
        reply.writeHead(response.status, {
          'content-type': 'application/json; charset=utf-8',
          ...(response.headers ?? {}),
        });
        reply.end(payload);
      } catch (error) {
        const { status: failed, body } = failureOf(error, options.onError);
        status = failed;
        reply.writeHead(failed, { 'content-type': 'application/json; charset=utf-8' });
        reply.end(JSON.stringify(body));
      } finally {
        options.onRequest?.(
          `${message.method ?? 'GET'} ${message.url ?? '/'} ${status} ${Date.now() - started}ms`,
        );
      }
    })();
  });
}

function failureOf(
  error: unknown,
  onError: ((error: unknown) => void) | undefined,
): { status: number; body: Json } {
  if (error instanceof ValidationError) {
    return { status: 400, body: { code: 'VALIDATION', message: error.message } };
  }
  onError?.(error);
  return { status: 500, body: { code: 'INTERNAL', message: 'the request could not be completed' } };
}
