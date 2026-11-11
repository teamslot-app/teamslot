import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { session } from '../auth/session'
import { TERRAINS } from '../venues'

const json = (corps: unknown, status = 200) =>
  new Response(JSON.stringify(corps), { status, headers: { 'Content-Type': 'application/json' } })
const urlDe = (entree: RequestInfo | URL) => new URL(entree instanceof Request ? entree.url : String(entree))

const OUVERTS = [
  { id: 'm1', venueId: TERRAINS[0].id, startsAt: '2026-11-14T18:00:00Z', openSpots: 2, maxPlayers: 10 },
  { id: 'm2', venueId: TERRAINS[2].id, startsAt: '2026-11-14T20:00:00Z', openSpots: 1, maxPlayers: 10 },
]

function rendre() {
  return render(
    <MemoryRouter initialEntries={['/matchs-ouverts']}>
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

describe('matchs ouverts (SCRUM-118)', () => {
  it('liste les matchs du jour : heure, terrain, places', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => json(OUVERTS))
    rendre()

    const lignes = await screen.findAllByRole('listitem')
    expect(lignes).toHaveLength(2)
    expect(lignes[0].textContent).toMatch(/Terrain Démo Guéliz · 2 places/)
    expect(lignes[1].textContent).toMatch(/Terrain Démo Massira · 1 place Demander/)
  })

  it('demande le jour choisi avec le jeton, et recharge quand la date change', async () => {
    const appel = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => json([]))
    rendre()

    expect(await screen.findByText(/Aucun match ne cherche de joueurs/)).toBeTruthy()
    const premiere = appel.mock.calls[0][0] as Request
    expect(urlDe(premiere).pathname).toBe('/api/v1/matches/open')
    expect(urlDe(premiere).searchParams.get('date')).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(premiere.headers.get('Authorization')).toBe('Bearer jeton')

    fireEvent.change(screen.getByLabelText('Date'), { target: { value: '2026-12-01' } })
    await waitFor(() => expect(urlDe(appel.mock.calls.at(-1)![0] as Request).searchParams.get('date')).toBe('2026-12-01'))
  })

  it('serveur injoignable : message d’erreur', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('réseau'))
    rendre()

    expect((await screen.findByRole('alert')).textContent).toMatch(/Impossible de charger les matchs/)
  })

  it('lien « Matchs ouverts » dans l’en-tête', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => json([]))
    rendre()

    expect((await screen.findByRole('link', { name: 'Matchs ouverts' })).getAttribute('href')).toBe('/matchs-ouverts')
  })
})

describe('demander à rejoindre (SCRUM-79)', () => {
  const routeur = (rejoindre: () => Response) =>
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree) => {
      const requete = entree as Request
      const chemin = urlDe(requete).pathname
      if (requete.method === 'POST' && chemin === '/api/v1/matches/m1/join') return rejoindre()
      return json(OUVERTS)
    })

  it('envoie la demande sans corps et l’indique sur la ligne du match', async () => {
    const appel = routeur(() => json({ matchId: 'm1', userId: 'u9', requestedAt: '2026-11-10T10:00:00Z' }, 202))
    const u = userEvent.setup()
    rendre()

    const ligne = (await screen.findAllByRole('listitem'))[0]
    await u.click(within(ligne).getByRole('button', { name: 'Demander à rejoindre' }))

    expect((await within(ligne).findByRole('status')).textContent).toMatch(/Demande envoyée/)
    const post = appel.mock.calls.map((c) => c[0] as Request).find((r) => r.method === 'POST')!
    expect(await post.text()).toBe('')
    expect(post.headers.get('Authorization')).toBe('Bearer jeton')
  })

  it('match complet entre-temps : le message du serveur s’affiche', async () => {
    const detail = 'Ce match n’a plus de place ouverte.'
    routeur(() => json({ type: 'x', title: 'Match complet', status: 409, detail }, 409))
    const u = userEvent.setup()
    rendre()

    const ligne = (await screen.findAllByRole('listitem'))[0]
    await u.click(within(ligne).getByRole('button', { name: 'Demander à rejoindre' }))

    expect((await within(ligne).findByRole('alert')).textContent).toContain(detail)
    expect(within(ligne).getByRole('button', { name: 'Demander à rejoindre' })).toBeTruthy()
  })
})
