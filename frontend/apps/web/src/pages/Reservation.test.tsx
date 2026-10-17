import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { session } from '../auth/session'
import { formatHeure } from '../format'
import { TERRAINS } from '../venues'

const TERRAIN = TERRAINS[0]
const [CHASUBLES, BALLON] = TERRAIN.equipment
const CHEMIN = `/terrains/${TERRAIN.id}/creneaux/s1`
const SUPPLEMENTS: Record<string, number> = { [CHASUBLES.id]: 2000, [BALLON.id]: 1000 }
const MATCH = { id: 'm1', kind: 'FRIENDLY', status: 'PENDING_PAYMENT', slotId: 's1' }

const json = (corps: unknown, status = 200) =>
  new Response(JSON.stringify(corps), { status, headers: { 'Content-Type': 'application/json' } })
const mad = (centimes: number) => ({ amount: centimes, currency: 'MAD' })

type Options = { prix?: (equipements: string[]) => Response; reservation?: () => Response | Promise<Response> }

function serveur(options: Options = {}) {
  const appels: { methode: string; url: URL }[] = []
  const corps: unknown[] = []
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree) => {
    const requete = entree as Request
    const url = new URL(requete.url)
    appels.push({ methode: requete.method, url })
    if (url.pathname.endsWith('/price')) {
      const equipements = url.searchParams.getAll('equipment')
      if (options.prix) return options.prix(equipements)
      const supplement = equipements.reduce((somme, id) => somme + (SUPPLEMENTS[id] ?? 0), 0)
      return json({ slotId: 's1', base: mad(15000), equipmentTotal: mad(supplement), total: mad(15000 + supplement) })
    }
    if (url.pathname === '/api/v1/matches' && requete.method === 'POST') {
      corps.push(await requete.clone().json())
      return options.reservation ? options.reservation() : json(MATCH, 201)
    }
    return json({}, 404)
  })
  return { appels, corps, reservations: () => appels.filter((a) => a.methode === 'POST') }
}

function rendre() {
  return render(
    <MemoryRouter initialEntries={[{ pathname: CHEMIN, state: { start: '2026-10-20T18:00:00Z', end: '2026-10-20T19:00:00Z' } }]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

const bouton = () => screen.getByRole('button', { name: 'Réserver ce créneau' })

beforeEach(() => session.ouvrir({ accessToken: 'jeton', refreshToken: 'r1' }))
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  session.fermer()
})

describe('réservation', () => {
  it('affiche le terrain, l’heure et le prix calculé par le serveur', async () => {
    serveur()
    rendre()

    expect(await screen.findByText(/150,00\sMAD/)).toBeTruthy()
    const attendu = `${formatHeure('2026-10-20T18:00:00Z')} – ${formatHeure('2026-10-20T19:00:00Z')}`
    expect(screen.getByText(/Terrain Démo Guéliz/).textContent).toContain(attendu)
  })

  it('recalcule le prix chez le serveur quand on coche un équipement', async () => {
    const { appels } = serveur()
    const u = userEvent.setup()
    rendre()
    await screen.findByText(/150,00\sMAD/)

    await u.click(screen.getByLabelText('Chasubles'))

    expect(await screen.findByText(/170,00\sMAD/)).toBeTruthy()
    const dernier = appels.filter((a) => a.url.pathname.endsWith('/price')).at(-1)!
    expect(dernier.url.searchParams.get('slot')).toBe('s1')
    expect(dernier.url.searchParams.getAll('equipment')).toEqual([CHASUBLES.id])
  })

  it('réserve : envoie le créneau et les équipements, jamais un montant', async () => {
    const { corps } = serveur()
    const u = userEvent.setup()
    rendre()
    await screen.findByText(/150,00\sMAD/)
    await u.click(screen.getByLabelText('Chasubles'))
    await screen.findByText(/170,00\sMAD/)

    await u.click(bouton())

    expect(await screen.findByRole('heading', { name: 'Ton match' })).toBeTruthy()
    expect(corps).toEqual([{ slotId: 's1', equipmentIds: [CHASUBLES.id] }])
    expect(JSON.stringify(corps)).not.toMatch(/amount|price|total|prix/i)
    expect(screen.queryByRole('button', { name: 'Réserver ce créneau' })).toBeNull()
  })

  it('le bouton est désactivé pendant l’appel : une seule réservation part', async () => {
    const { reservations } = serveur({ reservation: () => new Promise<Response>(() => {}) })
    const u = userEvent.setup()
    rendre()
    await screen.findByText(/150,00\sMAD/)

    await u.click(bouton())
    const enCours = await screen.findByRole('button', { name: 'Réservation…' })
    await u.click(enCours)

    expect((enCours as HTMLButtonElement).disabled).toBe(true)
    expect(reservations()).toHaveLength(1)
  })

  it.each([
    ['slot-already-reserved', "Ce créneau vient d'être pris."],
    ['slot-already-started', 'Ce créneau a déjà commencé.'],
  ])('409 %s : message clair', async (type, message) => {
    serveur({ reservation: () => json({ type: `https://teamslot.app/problems/${type}`, title: 'Conflit', status: 409 }, 409) })
    const u = userEvent.setup()
    rendre()
    await screen.findByText(/150,00\sMAD/)

    await u.click(bouton())

    expect((await screen.findByRole('alert')).textContent).toBe(message)
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('réseau coupé pendant la réservation : message dédié', async () => {
    serveur({
      reservation: () => {
        throw new TypeError('network')
      },
    })
    const u = userEvent.setup()
    rendre()
    await screen.findByText(/150,00\sMAD/)

    await u.click(bouton())

    expect((await screen.findByRole('alert')).textContent).toMatch(/momentanément indisponible/)
  })

  it('créneau introuvable (404) : message clair, réservation impossible', async () => {
    serveur({ prix: () => json({ title: 'introuvable' }, 404) })
    rendre()

    expect((await screen.findByRole('alert')).textContent).toBe("Ce créneau n'existe plus.")
    expect((bouton() as HTMLButtonElement).disabled).toBe(true)
  })
})
