import createClient from 'openapi-fetch';
import type { paths as IdentityPaths } from './generated/identity';
import type { paths as MatchPaths } from './generated/match';
import type { paths as PaymentPaths } from './generated/payment';
import type { paths as TeamPaths } from './generated/team';
import type { paths as VenuePaths } from './generated/venue';

export interface ApiOptions {
  /** Adresse de la passerelle (ex. http://localhost:8080) : tous les appels passent par elle. */
  baseUrl: string;
  /** Jeton d'accès courant (ADR-013), lu à chaque appel. */
  getToken?: () => string | null | undefined;
  /** Remplaçable dans les tests. */
  fetch?: (request: Request) => Promise<Response>;
}

export function createApi({ baseUrl, getToken, fetch: baseFetch = (r) => fetch(r) }: ApiOptions) {
  const avecJeton = async (request: Request) => {
    const jeton = getToken?.();
    if (jeton) request.headers.set('Authorization', `Bearer ${jeton}`);
    return baseFetch(request);
  };
  const client = <Paths extends object>() => createClient<Paths>({ baseUrl, fetch: avecJeton });

  return {
    identity: client<IdentityPaths>(),
    match: client<MatchPaths>(),
    payment: client<PaymentPaths>(),
    team: client<TeamPaths>(),
    venue: client<VenuePaths>(),
  };
}

export type Api = ReturnType<typeof createApi>;
