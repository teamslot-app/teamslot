import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import { session } from './auth/session'

type Utilisateur = ReturnType<typeof userEvent.setup>

const MDP_FORT = 'Terrain-Synthe-2026'
/** Jeton factice : seul le rôle de la charge utile compte pour l'affichage (le serveur vérifie la signature). */
const jetonAvecRole = (role: string) => `en-tete.${btoa(JSON.stringify({ sub: 'u1', role }))}.signature`
const paire = (role: string) => ({ accessToken: jetonAvecRole(role), refreshToken: 'r', expiresIn: 900 })

const reponse = (corps: unknown, status = 200) =>
  new Response(JSON.stringify(corps), { status, headers: { 'Content-Type': 'application/json' } })
const cheminDe = (entree: RequestInfo | URL) =>
  new URL(entree instanceof Request ? entree.url : String(entree)).pathname

function rendre(chemin: string) {
  return render(
    <MemoryRouter initialEntries={[chemin]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

async function connecter(u: Utilisateur) {
  await u.type(screen.getByLabelText('Téléphone'), '0645488442')
  await u.type(screen.getByLabelText('Mot de passe'), MDP_FORT)
  await u.click(screen.getByRole('button', { name: 'Se connecter' }))
}

beforeEach(() => session.fermer())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  session.fermer()
})

describe('plateforme propriétaire (SCRUM-140)', () => {
  it('sans session, « / » mène à la connexion', () => {
    rendre('/')
    expect(screen.getByRole('heading', { name: 'Connexion' })).toBeTruthy()
  })

  it('inscription : compte simple (aucun type de compte envoyé), puis vérification de l’e-mail', async () => {
    const corps: Record<string, unknown>[] = []
    const reseau = vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree) => {
      corps.push(await (entree as Request).clone().json())
      return reponse({ id: 'u1', platformRole: 'PLAYER', managerStatus: 'NONE' }, 201)
    })
    const u = userEvent.setup()
    rendre('/inscription')

    await u.type(screen.getByLabelText('Nom affiché'), 'Terrain Guéliz')
    await u.type(screen.getByLabelText('Adresse e-mail'), 'gerant@gmail.com')
    await u.type(screen.getByLabelText('Téléphone'), '06 45 48 84 42')
    await u.type(screen.getByLabelText('Date de naissance'), '1985-06-02')
    await u.type(screen.getByLabelText('Mot de passe'), MDP_FORT)
    await u.type(screen.getByLabelText('Confirme le mot de passe'), MDP_FORT)
    await u.click(screen.getByRole('button', { name: 'Créer mon compte' }))

    expect(await screen.findByRole('heading', { name: 'Vérifie ton e-mail' })).toBeTruthy()
    expect(corps[0]).toMatchObject({ phone: '+212645488442', phoneCountry: 'MA' })
    expect(corps[0]).not.toHaveProperty('accountType')
    expect(reseau.mock.calls.map(([e]) => cheminDe(e))).toEqual(['/api/v1/auth/register'])
  })

  it('connexion d’un compte dont l’e-mail n’est pas vérifié : page du code', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () =>
      reponse({ type: 'https://teamslot.app/problems/email-not-verified', title: 'non vérifié', status: 403 }, 403),
    )
    const u = userEvent.setup()
    rendre('/connexion')
    await connecter(u)

    expect(await screen.findByRole('heading', { name: 'Vérifie ton e-mail' })).toBeTruthy()
    expect(session.estConnecte()).toBe(false)
  })

  it('compte pas encore gérant : la connexion mène au dossier, jamais à l’espace gérant', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => reponse(paire('PLAYER')))
    const u = userEvent.setup()
    rendre('/connexion')
    await connecter(u)

    expect(await screen.findByRole('heading', { name: 'Inscris ton terrain' })).toBeTruthy()
    expect(screen.queryByRole('link', { name: 'Mon terrain' })).toBeNull()
  })

  it('gérant validé : la connexion ouvre l’espace gérant', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => reponse(paire('VENUE_MANAGER')))
    const u = userEvent.setup()
    rendre('/connexion')
    await connecter(u)

    expect(await screen.findByRole('heading', { name: 'Mon terrain' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Réservations' })).toBeTruthy()
  })

  it('un compte pas encore gérant qui tape l’adresse de l’espace gérant est renvoyé vers le dossier', () => {
    session.ouvrir({ accessToken: jetonAvecRole('PLAYER'), refreshToken: 'r' })
    rendre('/reservations')
    expect(screen.getByRole('heading', { name: 'Inscris ton terrain' })).toBeTruthy()
  })

  it('« Se déconnecter » efface la session', async () => {
    session.ouvrir({ accessToken: jetonAvecRole('PLAYER'), refreshToken: 'r' })
    const u = userEvent.setup()
    rendre('/inscrire-mon-terrain')
    await u.click(screen.getByRole('button', { name: 'Se déconnecter' }))
    expect(session.estConnecte()).toBe(false)
    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeTruthy()
  })
})
