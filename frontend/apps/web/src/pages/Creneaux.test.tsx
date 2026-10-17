import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { session } from '../auth/session'
import { TERRAINS } from '../venues'

const TERRAIN = TERRAINS[0]
const CRENEAUX = [
  { id: 's1', start: '2026-10-20T18:00:00Z', end: '2026-10-20T19:00:00Z', available: true, price: { amount: 15000, currency: 'MAD' } },
  { id: 's2', start: '2026-10-20T19:00:00Z', end: '2026-10-20T20:00:00Z', available: false, price: { amount: 15000, currency: 'MAD' } },
]

const json = (corps: unknown, status = 200) =>
  new Response(JSON.stringify(corps), { status, headers: { 'Content-Type': 'application/json' } })
const urlDe = (entree: RequestInfo | URL) => (entree instanceof Request ? entree.url : String(entree))

function rendre(chemin: string) {
  return render(
    <MemoryRouter initialEntries={[chemin]}>
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

describe('terrains et créneaux', () => {
  it('liste les terrains, un lien chacun', () => {
    rendre('/')
    expect(screen.getAllByRole('link').filter((l) => l.getAttribute('href')?.startsWith('/terrains/'))).toHaveLength(
      TERRAINS.length,
    )
  })

  it('affiche les créneaux libres et pris, avec leur prix en MAD', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => json(CRENEAUX))
    rendre(`/terrains/${TERRAIN.id}`)

    expect(await screen.findAllByText(/150,00\sMAD/)).toHaveLength(2)
    const reserver = screen.getAllByRole('link', { name: 'Réserver' })
    expect(reserver).toHaveLength(1)
    expect(reserver[0].getAttribute('href')).toBe(`/terrains/${TERRAIN.id}/creneaux/s1`)
    expect(screen.getByText('Indisponible')).toBeTruthy()
  })

  it('demande les créneaux de la date choisie', async () => {
    const urls: string[] = []
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree) => {
      urls.push(urlDe(entree))
      return json(CRENEAUX)
    })
    rendre(`/terrains/${TERRAIN.id}`)
    await screen.findAllByText(/150,00\sMAD/)

    fireEvent.change(screen.getByLabelText('Date'), { target: { value: '2026-12-01' } })

    await waitFor(() =>
      expect(urls.some((u) => u.includes(`/api/v1/venues/${TERRAIN.id}/slots`) && u.includes('date=2026-12-01'))).toBe(true),
    )
  })

  it('erreur du serveur : message clair', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => json({ title: 'panne' }, 500))
    rendre(`/terrains/${TERRAIN.id}`)

    expect((await screen.findByRole('alert')).textContent).toMatch(/Impossible de charger/)
  })

  it('terrain inconnu : retour à la liste', () => {
    rendre('/terrains/inconnu')
    expect(screen.getByRole('heading', { name: 'Terrains' })).toBeTruthy()
  })
})
