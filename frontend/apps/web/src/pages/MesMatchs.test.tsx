import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { session } from '../auth/session'
import { TERRAINS } from '../venues'

const json = (corps: unknown, status = 200) =>
  new Response(JSON.stringify(corps), { status, headers: { 'Content-Type': 'application/json' } })
const urlDe = (entree: RequestInfo | URL) => new URL(entree instanceof Request ? entree.url : String(entree))

const match = (id: string, venueId: string, startsAt: string, status: string) => ({
  id,
  kind: 'FRIENDLY',
  visibility: 'PRIVATE',
  openSpots: 0,
  maxPlayers: 10,
  status,
  ownerId: 'u1',
  payerId: 'u1',
  slotId: `s-${id}`,
  venueId,
  startsAt,
  totalPrice: { amount: 15000, currency: 'MAD' },
  version: 0,
})

// Heures en milieu de journée : le jour est le même quelle que soit la base de fuseaux du navigateur de test
const MATCHS = [
  match('m1', TERRAINS[0].id, '2026-11-17T18:00:00Z', 'CONFIRMED'),
  match('m2', TERRAINS[1].id, '2026-11-18T20:00:00Z', 'CANCELLED'),
]

function rendre() {
  return render(
    <MemoryRouter initialEntries={['/mes-matchs']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => session.ouvrir({ accessToken: 'jeton', refreshToken: 'r1' }))
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  session.fermer()
})

describe('mes matchs à venir (SCRUM-77)', () => {
  it('liste mes matchs : jour, heure, terrain, statut en toutes lettres, lien vers le match', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => json(MATCHS))
    rendre()

    const lignes = await screen.findAllByRole('listitem')
    expect(lignes).toHaveLength(2)
    expect(lignes[0].textContent).toMatch(/novembre à \d{2}:\d{2} · Terrain Démo Guéliz · Confirmé/)
    expect(within(lignes[0]).getByRole('link').getAttribute('href')).toBe('/matchs/m1')
    expect(within(lignes[1]).getByRole('link').getAttribute('href')).toBe('/matchs/m2')
  })

  it('un match annulé reste affiché, avec le mot « Annulé »', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => json(MATCHS))
    rendre()

    const lignes = await screen.findAllByRole('listitem')
    expect(lignes[1].textContent).toMatch(/Terrain Démo Daoudiate · Annulé/)
  })

  it('appelle GET /api/v1/matches avec le jeton, sans aucun identifiant de joueur', async () => {
    const appel = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => json([]))
    rendre()

    expect(await screen.findByText(/Aucun match à venir/)).toBeTruthy()
    const requete = appel.mock.calls[0][0] as Request
    expect(requete.method).toBe('GET')
    expect(urlDe(requete).pathname).toBe('/api/v1/matches')
    expect(urlDe(requete).search).toBe('')
    expect(requete.headers.get('Authorization')).toBe('Bearer jeton')
  })

  it('aucun match : message et lien pour réserver', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => json([]))
    rendre()

    expect((await screen.findByRole('link', { name: 'Réserver un terrain' })).getAttribute('href')).toBe('/')
  })

  it('serveur injoignable ou en erreur : message d’erreur', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('réseau'))
    rendre()
    expect((await screen.findByRole('alert')).textContent).toMatch(/Impossible de charger tes matchs/)
    cleanup()

    vi.spyOn(globalThis, 'fetch').mockImplementation(async () =>
      json({ type: 'x', title: 'Erreur', status: 500, detail: 'Une erreur interne est survenue.' }, 500),
    )
    rendre()
    expect((await screen.findByRole('alert')).textContent).toMatch(/Impossible de charger tes matchs/)
  })

  it('lien « Mes matchs » dans l’en-tête', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => json([]))
    rendre()

    expect((await screen.findByRole('link', { name: 'Mes matchs' })).getAttribute('href')).toBe('/mes-matchs')
  })
})
