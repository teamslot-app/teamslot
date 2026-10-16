import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'
import { session } from './auth/session'

const ORIGINE = window.location.origin
const MATCH_ID = '3f0c9b2a-7e41-4d8a-b1c6-2a9e5f7d4c10'
const CHEMIN = `/api/v1/matches/${MATCH_ID}/participants`

function json(corps: unknown, status = 200) {
  return new Response(JSON.stringify(corps), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
const urlDe = (entree: RequestInfo | URL) => (entree instanceof Request ? entree.url : String(entree))
const jetonDe = (entree: RequestInfo | URL) =>
  entree instanceof Request ? entree.headers.get('Authorization') : null
const paire = (jeton: string) => ({ accessToken: jeton, refreshToken: 'r2', expiresIn: 900 })
const lister = () =>
  api.match.GET('/api/v1/matches/{id}/participants', { params: { path: { id: MATCH_ID } } })

beforeEach(() => session.fermer())
afterEach(() => {
  vi.restoreAllMocks()
  session.fermer()
})

describe('client API : jeton et rafraîchissement', () => {
  it('401 : rafraîchit une fois puis rejoue avec le nouveau jeton', async () => {
    session.ouvrir({ accessToken: 'vieux', refreshToken: 'r1' })
    const appels: string[] = []
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree) => {
      const url = urlDe(entree)
      appels.push(`${jetonDe(entree) ?? '-'} ${url.replace(ORIGINE, '')}`)
      if (url.endsWith('/api/v1/auth/refresh')) return json(paire('neuf'))
      return jetonDe(entree) === 'Bearer neuf' ? json([]) : json({}, 401)
    })

    const { response } = await lister()

    expect(response.status).toBe(200)
    expect(appels).toEqual([`Bearer vieux ${CHEMIN}`, '- /api/v1/auth/refresh', `Bearer neuf ${CHEMIN}`])
    expect(session.accessToken()).toBe('neuf')
  })

  it('rafraîchissement refusé : session fermée, sans boucle', async () => {
    session.ouvrir({ accessToken: 'vieux', refreshToken: 'r1' })
    const reseau = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => json({}, 401))

    const { response } = await lister()

    expect(response.status).toBe(401)
    expect(session.estConnecte()).toBe(false)
    expect(reseau).toHaveBeenCalledTimes(2) // l'appel, puis une seule tentative de rafraîchissement
  })

  it('mauvais mot de passe : 401 sans rafraîchir ni fermer la session', async () => {
    session.ouvrir({ accessToken: 'actuel', refreshToken: 'r1' })
    const reseau = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => json({}, 401))

    const { response } = await api.identity.POST('/api/v1/auth/login', {
      body: { phone: '+212600000000', password: 'mauvais-mot-de-passe' },
    })

    expect(response.status).toBe(401)
    expect(reseau).toHaveBeenCalledTimes(1)
    expect(session.estConnecte()).toBe(true)
  })

  it('plusieurs 401 en même temps : un seul rafraîchissement', async () => {
    session.ouvrir({ accessToken: 'vieux', refreshToken: 'r1' })
    let rafraichissements = 0
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree) => {
      if (urlDe(entree).endsWith('/api/v1/auth/refresh')) {
        rafraichissements++
        await new Promise((fin) => setTimeout(fin, 10))
        return json(paire('neuf'))
      }
      return jetonDe(entree) === 'Bearer neuf' ? json([]) : json({}, 401)
    })

    const reponses = await Promise.all([lister(), lister(), lister()])

    expect(reponses.map((r) => r.response.status)).toEqual([200, 200, 200])
    expect(rafraichissements).toBe(1)
  })
})
