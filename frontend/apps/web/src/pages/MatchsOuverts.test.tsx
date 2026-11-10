import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
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
    expect(lignes[1].textContent).toMatch(/Terrain Démo Massira · 1 place$/)
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
