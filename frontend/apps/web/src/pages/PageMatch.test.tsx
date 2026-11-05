import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { session } from '../auth/session'
import { TERRAINS } from '../venues'

const AVEC_SUR_PLACE = TERRAINS[0] // Guéliz : accepte le paiement sur place
const SANS_SUR_PLACE = TERRAINS[1] // Daoudiate : ne l'accepte pas
const MAD = (centimes: number) => ({ amount: centimes, currency: 'MAD' })
const MATCH = {
  id: 'm1', kind: 'FRIENDLY', visibility: 'PRIVATE', openSpots: 0, status: 'PENDING_PAYMENT',
  ownerId: 'u1', payerId: 'u1', slotId: 's1', totalPrice: MAD(15000),
}
const PARTICIPANTS = 'GET /api/v1/matches/m1/participants'
const AJOUT = 'POST /api/v1/matches/m1/participants'
const EN_LIGNE = 'POST /api/v1/payments/m1/online'
const SUR_PLACE = 'POST /api/v1/payments/m1/on-site'
const ANNULER = 'POST /api/v1/matches/m1/cancel'

const json = (corps: unknown, status = 200) =>
  new Response(JSON.stringify(corps), { status, headers: { 'Content-Type': 'application/json' } })
const paiement = (status: string, mode = 'ONLINE') => ({
  id: 'pay1', matchId: 'm1', payerId: 'u1', mode, totalAmount: MAD(15000), status,
})

type Gestionnaire = (requete: Request) => Response | Promise<Response>

function serveur(surcharges: Record<string, Gestionnaire> = {}) {
  const appels: string[] = []
  const corps: Record<string, string> = {}
  const defaut: Record<string, Gestionnaire> = {
    [PARTICIPANTS]: () => json([]),
    'GET /api/v1/payments/m1': () => json(paiement('PENDING')),
  }
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree) => {
    const requete = entree as Request
    const cle = `${requete.method} ${new URL(requete.url).pathname}`
    appels.push(cle)
    corps[cle] = await requete.clone().text()
    const gestionnaire = surcharges[cle] ?? defaut[cle]
    return gestionnaire ? gestionnaire(requete) : json({}, 404)
  })
  return { appels, corps }
}

function rendre(venueId = AVEC_SUR_PLACE.id, avecContexte = true) {
  const entree = avecContexte
    ? { pathname: '/matchs/m1', state: { match: MATCH, venueId, start: '2026-10-20T18:00:00Z', end: '2026-10-20T19:00:00Z' } }
    : { pathname: '/matchs/m1' }
  return render(
    <MemoryRouter initialEntries={[entree]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

const bouton = (nom: string) => screen.findByRole('button', { name: nom })

beforeEach(() => session.ouvrir({ accessToken: 'jeton', refreshToken: 'r1' }))
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  session.fermer()
})

describe('page du match : résumé et amis', () => {
  it('affiche le terrain, le prix du serveur et le statut', async () => {
    serveur()
    rendre()

    expect(await screen.findByRole('heading', { name: 'Ton match' })).toBeTruthy()
    expect(screen.getByText(/Terrain Démo Guéliz/)).toBeTruthy()
    expect(screen.getByText(/150,00\sMAD/)).toBeTruthy()
    expect(screen.getByText('En attente de paiement')).toBeTruthy()
  })

  it('un surnom piégé s’affiche tel quel, comme du texte', async () => {
    const piege = '<img src=x onerror=alert(1)>'
    serveur({ [PARTICIPANTS]: () => json([{ id: 'p1', matchId: 'm1', guestName: piege, status: 'CONFIRMED', side: null }]) })
    const { container } = rendre()

    expect(await screen.findByText(piege)).toBeTruthy()
    expect(container.querySelector('img')).toBeNull()
  })

  it('ajoute un ami par surnom : corps sans téléphone ni e-mail, liste rechargée', async () => {
    const { appels, corps } = serveur({
      [AJOUT]: () => json({ id: 'p2', matchId: 'm1', guestName: 'Yassir', status: 'CONFIRMED' }, 201),
    })
    const u = userEvent.setup()
    rendre()

    await u.type(await screen.findByLabelText(/Surnom/), 'Yassir')
    await u.click(screen.getByRole('button', { name: 'Ajouter' }))

    expect((await screen.findByRole('status')).textContent).toBe('Ami ajouté.')
    expect(JSON.parse(corps[AJOUT])).toEqual({ guestName: 'Yassir' })
    await waitFor(() => expect(appels.filter((a) => a === PARTICIPANTS)).toHaveLength(2))
  })

  it('ne demande ni téléphone ni e-mail pour un invité', async () => {
    serveur()
    rendre()
    await screen.findByLabelText(/Surnom/)

    expect(screen.queryByLabelText(/téléphone|e-mail|email|numéro/i)).toBeNull()
  })

  it.each([
    [409, 'Ce surnom est déjà utilisé dans ce match : choisis-en un autre.'],
    [409, 'Ce match n’accepte plus de participants.'],
    [403, 'Cette action est réservée à l’organisateur du match.'],
  ])('ajout refusé (%i) : le message du serveur s’affiche', async (statut, detail) => {
    serveur({ [AJOUT]: () => json({ title: 'refus', status: statut, detail }, statut) })
    const u = userEvent.setup()
    rendre()

    await u.type(await screen.findByLabelText(/Surnom/), 'Yassir')
    await u.click(screen.getByRole('button', { name: 'Ajouter' }))

    expect((await screen.findByRole('alert')).textContent).toBe(detail)
  })
})

describe('page du match : paiement', () => {
  it('payer en ligne : aucun corps envoyé, le match passe à « confirmé »', async () => {
    const { corps } = serveur({ [EN_LIGNE]: () => json(paiement('COMPLETED')) })
    const u = userEvent.setup()
    rendre()

    await u.click(await bouton('Payer en ligne'))

    expect(await screen.findByText('Match confirmé')).toBeTruthy()
    expect(corps[EN_LIGNE]).toBe('')
    expect(screen.queryByRole('button', { name: /Payer/ })).toBeNull()
  })

  it('payer sur place (terrain qui l’accepte) : accepté, à régler sur place', async () => {
    const { corps } = serveur({ [SUR_PLACE]: () => json(paiement('ON_SITE_ACCEPTED', 'ON_SITE')) })
    const u = userEvent.setup()
    rendre()

    await u.click(await bouton('Payer sur place'))

    expect(await screen.findByText('Match confirmé')).toBeTruthy()
    expect(screen.getByText(/à régler sur place/)).toBeTruthy()
    expect(corps[SUR_PLACE]).toBe('')
  })

  it('terrain qui refuse le paiement sur place : seule l’option en ligne existe', async () => {
    serveur()
    rendre(SANS_SUR_PLACE.id)

    await bouton('Payer en ligne')
    expect(screen.queryByRole('button', { name: 'Payer sur place' })).toBeNull()
  })

  it('le bouton est désactivé pendant l’appel : un seul paiement part', async () => {
    const { appels } = serveur({ [EN_LIGNE]: () => new Promise<Response>(() => {}) })
    const u = userEvent.setup()
    rendre()

    const payer = await bouton('Payer en ligne')
    await u.click(payer)
    await u.click(payer)

    expect((payer as HTMLButtonElement).disabled).toBe(true)
    expect(appels.filter((a) => a === EN_LIGNE)).toHaveLength(1)
  })

  it('second paiement (409) : le message du serveur s’affiche', async () => {
    const detail = 'Ce match est déjà payé.'
    serveur({ [EN_LIGNE]: () => json({ title: 'conflit', status: 409, detail }, 409) })
    const u = userEvent.setup()
    rendre()

    await u.click(await bouton('Payer en ligne'))

    expect((await screen.findByRole('alert')).textContent).toBe(detail)
  })

  it('paiement pas encore créé (404) : message et bouton Actualiser', async () => {
    serveur({ 'GET /api/v1/payments/m1': () => json({ title: 'absent' }, 404) })
    rendre()

    expect(await screen.findByText(/Le paiement se prépare/)).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Actualiser' })).toBeTruthy()
  })
})

describe('page du match : annulation et accès', () => {
  it('annuler : confirmation, puis match annulé et plus d’actions', async () => {
    serveur({ [ANNULER]: () => new Response(null, { status: 204 }) })
    const u = userEvent.setup()
    rendre()

    await u.click(await bouton('Annuler le match'))
    await u.click(screen.getByRole('button', { name: 'Oui, annuler' }))

    expect((await screen.findByRole('status')).textContent).toMatch(/Match annulé/)
    expect(screen.queryByRole('button', { name: /Payer/ })).toBeNull()
    expect(screen.queryByLabelText(/Surnom/)).toBeNull()
  })

  it('annulation refusée (409) : le message du serveur s’affiche, le match reste actif', async () => {
    const detail = 'Ce match ne peut plus être annulé.'
    serveur({ [ANNULER]: () => json({ title: 'conflit', status: 409, detail }, 409) })
    const u = userEvent.setup()
    rendre()

    await u.click(await bouton('Annuler le match'))
    await u.click(screen.getByRole('button', { name: 'Oui, annuler' }))

    expect((await screen.findByRole('alert')).textContent).toBe(detail)
    expect(screen.getByRole('button', { name: 'Payer en ligne' })).toBeTruthy()
  })

  it('sans contexte (page rechargée) : retour à la liste des terrains', () => {
    serveur()
    rendre(AVEC_SUR_PLACE.id, false)

    expect(screen.getByRole('heading', { name: 'Terrains' })).toBeTruthy()
  })
})
