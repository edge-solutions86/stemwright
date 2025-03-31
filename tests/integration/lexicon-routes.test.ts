import { describe, expect, it } from 'vitest';
import { createLexiconService } from '../../src/app/services/lexicon-service.js';
import { ForbiddenError, NotFoundError, ValidationError } from '../../src/domain/errors.js';
import { createRouter } from '../../src/http/router.js';
import { lexiconRoutes } from '../../src/http/routes/lexicon-routes.js';
import { seededStores, testHarness } from '../support/app.js';
import { puella } from '../support/drill.js';
import { directory, makeRequest } from '../support/http.js';

const build = async () => {
  const stores = await seededStores();
  const { bus, clock } = testHarness();
  const lexicon = createLexiconService(stores, bus, clock);
  return { stores, handle: createRouter(lexiconRoutes({ directory, lexicon })) };
};

const nauta = {
  lemma: 'nauta',
  classKey: 'first-declension',
  stems: [{ role: 'base', form: 'naut' }],
  glosses: ['sailor'],
  tags: ['core'],
};

describe('listing lexemes', () => {
  it('lists everything in a language', async () => {
    const { handle } = await build();
    const response = await handle(
      makeRequest('GET', '/languages/latin/lexemes', { token: 'tok-student' }),
    );
    expect(response.status).toBe(200);
    expect(response.body as unknown[]).toHaveLength(4);
  });

  it('searches when a term is given', async () => {
    const { handle } = await build();
    const response = await handle(
      makeRequest('GET', '/languages/latin/lexemes', {
        token: 'tok-student',
        query: { q: 'field' },
      }),
    );
    expect(response.body as unknown[]).toHaveLength(1);
  });

  it('lets a learner read', async () => {
    const { handle } = await build();
    const response = await handle(
      makeRequest('GET', `/lexemes/${puella.id}`, { token: 'tok-student' }),
    );
    expect(response.body).toMatchObject({ lemma: 'puella', citation: 'puella' });
  });

  it('refuses an id nobody stored', async () => {
    const { handle } = await build();
    const request = makeRequest('GET', '/lexemes/lex_zzzzzz', { token: 'tok-student' });
    await expect(handle(request)).rejects.toThrow(NotFoundError);
  });

  it('refuses a request with no token', async () => {
    const { handle } = await build();
    await expect(handle(makeRequest('GET', '/languages/latin/lexemes'))).rejects.toThrow(
      ForbiddenError,
    );
  });
});

describe('creating a lexeme', () => {
  it('stores one and answers 201', async () => {
    const { handle } = await build();
    const response = await handle(
      makeRequest('POST', '/languages/latin/lexemes', { token: 'tok-teacher', body: nauta }),
    );
    expect(response.status).toBe(201);
    expect(response.body).toMatchObject({ lemma: 'nauta', classKey: 'first-declension' });
  });

  it('reads the inherent features off the body', async () => {
    const { handle } = await build();
    const response = await handle(
      makeRequest('POST', '/languages/latin/lexemes', {
        token: 'tok-teacher',
        body: { ...nauta, inherent: {} },
      }),
    );
    expect(response.body).toMatchObject({ inherent: '' });
  });

  it('refuses a learner at the route before the service is reached', async () => {
    const { handle, stores } = await build();
    const request = makeRequest('POST', '/languages/latin/lexemes', {
      token: 'tok-student',
      body: nauta,
    });
    await expect(handle(request)).rejects.toThrow(ForbiddenError);
    expect(await stores.lexemes.listByLanguage('latin')).toHaveLength(4);
  });

  it('refuses a body with no stems', async () => {
    const { handle } = await build();
    const request = makeRequest('POST', '/languages/latin/lexemes', {
      token: 'tok-teacher',
      body: { ...nauta, stems: [] },
    });
    await expect(handle(request)).rejects.toThrow(/body.stems must hold at least one stem/);
  });

  it('names the bad stem row', async () => {
    const { handle } = await build();
    const request = makeRequest('POST', '/languages/latin/lexemes', {
      token: 'tok-teacher',
      body: { ...nauta, stems: [{ role: 'base' }] },
    });
    await expect(handle(request)).rejects.toThrow(/body.stems.0.form is not a string/);
  });

  it('refuses an inherent value that is not a string', async () => {
    const { handle } = await build();
    const request = makeRequest('POST', '/languages/latin/lexemes', {
      token: 'tok-teacher',
      body: { ...nauta, inherent: { gender: 7 } },
    });
    await expect(handle(request)).rejects.toThrow(/body.inherent.gender is not a string/);
  });

  it('refuses a body that is not an object', async () => {
    const { handle } = await build();
    const request = makeRequest('POST', '/languages/latin/lexemes', {
      token: 'tok-teacher',
      body: 'nope',
    });
    await expect(handle(request)).rejects.toThrow(ValidationError);
  });
});

describe('removing a lexeme', () => {
  it('removes one and answers 204', async () => {
    const { handle, stores } = await build();
    const response = await handle(
      makeRequest('DELETE', `/lexemes/${puella.id}`, { token: 'tok-teacher' }),
    );
    expect(response.status).toBe(204);
    expect(await stores.lexemes.listByLanguage('latin')).toHaveLength(3);
  });

  it('refuses a learner', async () => {
    const { handle } = await build();
    const request = makeRequest('DELETE', `/lexemes/${puella.id}`, { token: 'tok-student' });
    await expect(handle(request)).rejects.toThrow(ForbiddenError);
  });

  it('refuses an id nobody stored', async () => {
    const { handle } = await build();
    const request = makeRequest('DELETE', '/lexemes/lex_zzzzzz', { token: 'tok-teacher' });
    await expect(handle(request)).rejects.toThrow(NotFoundError);
  });
});
