import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import { session } from './auth/session'

type Utilisateur = ReturnType<typeof userEvent.setup>

const PAIRE = { accessToken: 'jeton-acces', refreshToken: 'jeton-refresh', expiresIn: 900 }
const MOT_DE_PASSE = 'motdepasse-solide'

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

async function connecter(u: Utilisateur, motDePasse = MOT_DE_PASSE) {
  await u.type(screen.getByLabelText('Téléphone'), '+212600000000')
  await u.type(screen.getByLabelText('Mot de passe'), motDePasse)
  await u.click(screen.getByRole('button', { name: 'Se connecter' }))
}

beforeEach(() => session.fermer())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  session.fermer()
})

describe('connexion', () => {
  it('ouvre la session, envoie le bon corps, ne stocke rien dans le navigateur', async () => {
    const reseau = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => reponse(PAIRE))
    const u = userEvent.setup()
    rendre('/connexion')

    await connecter(u)

    expect(await screen.findByRole('heading', { name: 'Terrains' })).toBeTruthy()
    const requete = reseau.mock.calls[0][0] as Request
    expect(cheminDe(requete)).toBe('/api/v1/auth/login')
    expect(requete.headers.get('Authorization')).toBeNull()
    expect(await requete.json()).toEqual({ phone: '+212600000000', password: MOT_DE_PASSE })
    expect(session.accessToken()).toBe('jeton-acces')
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
    expect(document.cookie).toBe('')
  })

  it.each([400, 401, 404])('statut %i : même message, qui ne dit pas si le compte existe', async (statut) => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => reponse({ title: 'refusé' }, statut))
    const u = userEvent.setup()
    rendre('/connexion')

    await connecter(u)

    expect((await screen.findByRole('alert')).textContent).toBe('Téléphone ou mot de passe incorrect')
    expect(session.estConnecte()).toBe(false)
  })

  it('service indisponible (503) : message dédié', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => reponse({ title: 'panne' }, 503))
    const u = userEvent.setup()
    rendre('/connexion')

    await connecter(u)

    expect((await screen.findByRole('alert')).textContent).toMatch(/momentanément indisponible/)
  })

  it('réseau coupé : message dédié', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('network'))
    const u = userEvent.setup()
    rendre('/connexion')

    await connecter(u)

    expect((await screen.findByRole('alert')).textContent).toMatch(/momentanément indisponible/)
  })

  it('le bouton est désactivé pendant l’appel (pas de double envoi)', async () => {
    let terminer: (r: Response) => void = () => {}
    const reseau = vi.spyOn(globalThis, 'fetch').mockImplementation(
      () => new Promise<Response>((fin) => { terminer = fin }),
    )
    const u = userEvent.setup()
    rendre('/connexion')

    await connecter(u)

    const bouton = await screen.findByRole('button', { name: 'Connexion…' })
    expect((bouton as HTMLButtonElement).disabled).toBe(true)
    expect(reseau).toHaveBeenCalledTimes(1)
    terminer(reponse(PAIRE))
    expect(await screen.findByRole('heading', { name: 'Terrains' })).toBeTruthy()
  })

  it('le mot de passe n’est jamais journalisé', async () => {
    const espions = (['log', 'info', 'warn', 'error'] as const).map((m) =>
      vi.spyOn(console, m).mockImplementation(() => {}),
    )
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => reponse({ title: 'refusé' }, 401))
    const u = userEvent.setup()
    rendre('/connexion')

    await connecter(u, 'motdepasse-secret')
    await screen.findByRole('alert')

    for (const espion of espions) {
      expect(JSON.stringify(espion.mock.calls)).not.toContain('motdepasse-secret')
    }
  })
})

describe('inscription', () => {
  async function remplir(u: Utilisateur) {
    await u.type(screen.getByLabelText('Nom affiché'), 'Yassir')
    await u.type(screen.getByLabelText('Téléphone'), '+212600000000')
    await u.type(screen.getByLabelText('Mot de passe'), MOT_DE_PASSE)
    await u.click(screen.getByRole('button', { name: 'Créer mon compte' }))
  }

  it('numéro déjà inscrit (409) : message clair, pas de session', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => reponse({ title: 'conflit' }, 409))
    const u = userEvent.setup()
    rendre('/inscription')

    await remplir(u)

    expect((await screen.findByRole('alert')).textContent).toBe('Ce numéro est déjà inscrit.')
    expect(session.estConnecte()).toBe(false)
  })

  it('inscription (201) puis connexion automatique', async () => {
    const reseau = vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree) =>
      cheminDe(entree).endsWith('/auth/register') ? reponse({ id: 'u1' }, 201) : reponse(PAIRE),
    )
    const u = userEvent.setup()
    rendre('/inscription')

    await remplir(u)

    expect(await screen.findByRole('heading', { name: 'Terrains' })).toBeTruthy()
    expect(reseau.mock.calls.map(([e]) => cheminDe(e))).toEqual([
      '/api/v1/auth/register',
      '/api/v1/auth/login',
    ])
  })
})

describe('accès et déconnexion', () => {
  it('sans session, la page d’accueil renvoie vers la connexion', () => {
    rendre('/')
    expect(screen.getByRole('heading', { name: 'Connexion' })).toBeTruthy()
  })

  it('« Se déconnecter » efface le jeton et revient à la connexion', async () => {
    session.ouvrir({ accessToken: 'jeton-acces', refreshToken: 'jeton-refresh' })
    const u = userEvent.setup()
    rendre('/')
    expect(await screen.findByRole('heading', { name: 'Terrains' })).toBeTruthy()

    await u.click(screen.getByRole('button', { name: 'Se déconnecter' }))

    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeTruthy()
    expect(session.accessToken()).toBeNull()
    expect(session.refreshToken()).toBeNull()
  })
})
