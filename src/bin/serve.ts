import { createApplication } from '../app/application.js';
import { memoryDirectory } from '../http/auth.js';
import { createApi } from '../http/api.js';
import { createServer } from '../http/server.js';
import { memoryStores } from '../infra/memory.js';

/**
 * The server entry point. It reads what it needs from the environment and does
 * nothing else, so that everything it wires is testable without starting it.
 *
 * The account directory here is a placeholder that reads a token map out of the
 * environment. A deployment replaces it with whatever the institution already
 * runs, and the rest of the process does not change.
 */
export interface ServeConfig {
  readonly port: number;
  readonly version: string;
  readonly accounts: Record<string, { id: string; role: 'learner' | 'instructor' | 'admin' }>;
}

export function readConfig(env: Record<string, string | undefined>): ServeConfig {
  const port = Number(env['STEMWRIGHT_PORT'] ?? '8080');
  if (!Number.isInteger(port) || port < 0 || port > 65535) {
    throw new Error(`STEMWRIGHT_PORT is not a port number: ${String(env['STEMWRIGHT_PORT'])}`);
  }

  const raw = env['STEMWRIGHT_ACCOUNTS'];
  const accounts = raw === undefined ? {} : (JSON.parse(raw) as ServeConfig['accounts']);

  return { port, version: env['STEMWRIGHT_VERSION'] ?? 'dev', accounts };
}

export function buildServer(config: ServeConfig) {
  const app = createApplication(memoryStores());
  const handler = createApi(app, {
    directory: memoryDirectory(config.accounts),
    version: config.version,
    onError: (error) => {
      console.error('unhandled failure', error);
    },
  });
  return createServer(handler, {
    onError: (error) => {
      console.error('unhandled failure', error);
    },
  });
}

export function main(env: Record<string, string | undefined>): void {
  const config = readConfig(env);
  buildServer(config).listen(config.port, () => {
    console.error(`stemwright ${config.version} listening on ${config.port}`);
  });
}

if (process.argv[1]?.endsWith('serve.js') === true) {
  main(process.env);
}
