import type { Application } from '../app/application.js';
import type { AccountDirectory } from './auth.js';
import { withProblems } from './problem.js';
import type { Handler, Route } from './router.js';
import { createRouter, json, route } from './router.js';
import { bulkRoutes } from './routes/bulk-routes.js';
import { drillRoutes } from './routes/drill-routes.js';
import { lexiconRoutes } from './routes/lexicon-routes.js';
import { reportRoutes } from './routes/report-routes.js';

/**
 * The whole http surface, assembled once.
 *
 * Health and readiness are separate and they answer different questions. Health
 * says the process is up, and a load balancer uses it to decide whether to kill
 * the container. Readiness says the process can serve, and an orchestrator uses
 * it to decide whether to send traffic. Wiring them to the same check is the
 * standard way to build a service that restarts itself forever under load.
 */
export interface ApiOptions {
  readonly directory: AccountDirectory;
  readonly version?: string;
  readonly onError?: (error: unknown) => void;
  readonly isReady?: () => boolean;
}

export function healthRoutes(options: ApiOptions): Route[] {
  const version = options.version ?? 'unknown';
  const isReady = options.isReady ?? (() => true);

  return [
    route('GET', '/health', () => Promise.resolve(json(200, { status: 'up', version }))),
    route('GET', '/ready', () =>
      Promise.resolve(
        isReady()
          ? json(200, { status: 'ready' })
          : json(503, { status: 'not ready', message: 'the store has not finished loading' }),
      ),
    ),
  ];
}

export function apiRoutes(app: Application, options: ApiOptions): Route[] {
  const scopeOf = (courseKey: string) => app.courses.scopeOf(courseKey);

  return [
    ...healthRoutes(options),
    ...drillRoutes({ directory: options.directory, drill: app.drill, scopeOf }),
    ...bulkRoutes({ directory: options.directory, drill: app.drill, scopeOf }),
    ...lexiconRoutes({ directory: options.directory, lexicon: app.lexicon }),
    ...reportRoutes({ directory: options.directory, reports: app.reports, scopeOf }),
  ];
}

export function createApi(app: Application, options: ApiOptions): Handler {
  return withProblems(createRouter(apiRoutes(app, options)), options.onError ?? (() => undefined));
}

export function routeTable(app: Application, options: ApiOptions): string[] {
  return apiRoutes(app, options)
    .map((entry) => `${entry.method} ${entry.pattern}`)
    .sort();
}
