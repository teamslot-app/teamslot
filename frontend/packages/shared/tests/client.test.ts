import { describe, expect, it, vi } from 'vitest';
import { createApi } from '../src';

const MATCH_ID = '3f0c9b2a-7e41-4d8a-b1c6-2a9e5f7d4c10';

function reponseJson(corps: unknown) {
  return new Response(JSON.stringify(corps), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('client généré depuis docs/api', () => {
  it('appelle la route du contrat sur la passerelle, avec le jeton', async () => {
    const fetchSimule = vi.fn(async (_requete: Request) =>
      reponseJson({ matchId: MATCH_ID, strategy: 'random', sideA: [], sideB: [] }),
    );
    const api = createApi({
      baseUrl: 'http://passerelle.test',
      getToken: () => 'jeton-de-test',
      fetch: fetchSimule,
    });

    const { data, error } = await api.match.POST('/api/v1/matches/{id}/split', {
      params: { path: { id: MATCH_ID }, query: { strategy: 'random' } },
    });

    expect(error).toBeUndefined();
    expect(data?.strategy).toBe('random');
    const requete = fetchSimule.mock.calls[0]![0];
    expect(requete.method).toBe('POST');
    expect(requete.url).toBe(`http://passerelle.test/api/v1/matches/${MATCH_ID}/split?strategy=random`);
    expect(requete.headers.get('Authorization')).toBe('Bearer jeton-de-test');
  });

  it("n'envoie pas d'en-tête Authorization sans jeton", async () => {
    const fetchSimule = vi.fn(async (_requete: Request) =>
      reponseJson({ matchId: MATCH_ID, strategy: 'random', sideA: [], sideB: [] }),
    );
    const api = createApi({ baseUrl: 'http://passerelle.test', fetch: fetchSimule });

    await api.match.POST('/api/v1/matches/{id}/split', {
      params: { path: { id: MATCH_ID }, query: { strategy: 'random' } },
    });

    expect(fetchSimule.mock.calls[0]![0].headers.get('Authorization')).toBeNull();
  });

  it('refuse à la compilation une valeur absente du contrat', () => {
    const api = createApi({ baseUrl: 'http://passerelle.test' });
    // Contrôlé par « npm run typecheck » : si le contrat accepte un jour « balanced »,
    // cette ligne cesse d'être une erreur et la CI nous le dit : on met alors ce test à jour.
    const appel = () =>
      api.match.POST('/api/v1/matches/{id}/split', {
        // @ts-expect-error « balanced » n'est pas dans l'enum du contrat (random seulement)
        params: { path: { id: MATCH_ID }, query: { strategy: 'balanced' } },
      });
    expect(appel).toBeTypeOf('function');
  });
});
