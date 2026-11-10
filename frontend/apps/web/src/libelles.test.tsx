import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, type InitialEntry } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import { session } from './auth/session'
import { TERRAINS } from './venues'

/*
 * Accessibilité : chaque champ de formulaire de chaque page a un libellé associé et non vide
 * (un placeholder ne compte pas). Une nouvelle page doit être ajoutée à PAGES.
 */
const TERRAIN = TERRAINS[0]
const MAD = (c: number) => ({ amount: c, currency: 'MAD' })
const MATCH = {
  id: 'm1',
  kind: 'FRIENDLY',
  visibility: 'PRIVATE',
  openSpots: 0,
  status: 'PENDING_PAYMENT',
  ownerId: 'u1',
  payerId: 'u1',
  slotId: 's1',
  totalPrice: MAD(15000),
}

const json = (corps: unknown, status = 200) =>
  new Response(JSON.stringify(corps), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

function api() {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree) => {
    const chemin = new URL((entree as Request).url).pathname
    if (chemin.endsWith('/slots'))
      return json([
        {
          id: 's1',
          start: '2026-10-20T18:00:00Z',
          end: '2026-10-20T19:00:00Z',
          available: true,
          price: MAD(15000),
        },
      ])
    if (chemin.endsWith('/price'))
      return json({
        slotId: 's1',
        base: MAD(15000),
        equipmentTotal: MAD(0),
        total: MAD(15000),
      })
    if (chemin.endsWith('/participants')) return json([])
    if (chemin.startsWith('/api/v1/payments/'))
      return json({
        id: 'p1',
        matchId: 'm1',
        status: 'PENDING',
        totalAmount: MAD(15000),
      })
    return json({}, 404)
  })
}

const PAGES: Array<{
  nom: string
  entree: InitialEntry
  connecte: boolean
  titre: string
  champs: number
}> = [
  {
    nom: 'Connexion',
    entree: '/connexion',
    connecte: false,
    titre: 'Connexion',
    champs: 2,
  },
  {
    nom: 'Inscription',
    entree: '/inscription',
    connecte: false,
    titre: 'Créer un compte',
    champs: 3,
  },
  {
    nom: 'Terrains',
    entree: '/',
    connecte: true,
    titre: 'Terrains',
    champs: 0,
  },
  {
    nom: 'Créneaux',
    entree: `/terrains/${TERRAIN.id}`,
    connecte: true,
    titre: TERRAIN.name,
    champs: 1,
  },
  {
    nom: 'Réservation',
    entree: `/terrains/${TERRAIN.id}/creneaux/s1`,
    connecte: true,
    titre: 'Réserver',
    champs: 2,
  },
  {
    nom: 'Page du match',
    entree: {
      pathname: '/matchs/m1',
      state: { match: MATCH, venueId: TERRAIN.id },
    },
    connecte: true,
    titre: 'Ton match',
    champs: 1,
  },
]

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  session.fermer()
})

describe('chaque champ de formulaire a un libellé', () => {
  it.each(PAGES)(
    '$nom',
    async ({ entree, connecte, titre, champs: attendus }) => {
      if (connecte) session.ouvrir({ accessToken: 'jeton', refreshToken: 'r1' })
      api()
      const { container } = render(
        <MemoryRouter initialEntries={[entree]}>
          <AuthProvider>
            <App />
          </AuthProvider>
        </MemoryRouter>,
      )
      await screen.findByRole('heading', { level: 1, name: titre })

      const champs = [
        ...container.querySelectorAll<HTMLInputElement>(
          'input, select, textarea',
        ),
      ]
      expect(champs).toHaveLength(attendus)
      const sansLibelle = champs
        .filter(
          (champ) =>
            ![...(champ.labels ?? [])].some(
              (l) => (l.textContent ?? '').trim() !== '',
            ),
        )
        .map((champ) => champ.outerHTML)
      expect(sansLibelle).toEqual([])
    },
  )

  it('le contrôle détecte bien un champ sans libellé', () => {
    const { container } = render(<input placeholder="Téléphone" />)
    expect(container.querySelector('input')?.labels?.length).toBe(0)
  })
})
