import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import { session } from './auth/session'

type Utilisateur = ReturnType<typeof userEvent.setup>

const PAIRE = {
  accessToken: 'jeton-acces',
  refreshToken: 'jeton-refresh',
  expiresIn: 900,
}
const MOT_DE_PASSE = 'motdepasse-solide'

const reponse = (corps: unknown, status = 200) =>
  new Response(JSON.stringify(corps), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
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
    expect(await requete.json()).toEqual({
      phone: '+212600000000',
      password: MOT_DE_PASSE,
    })
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
      () =>
        new Promise<Response>((fin) => {
          terminer = fin
        }),
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

describe('inscription v2 (SCRUM-135)', () => {
  const MDP_FORT = 'Terrain-Synthe-2026'

  async function remplir(u: Utilisateur, { confirmation = MDP_FORT, motDePasse = MDP_FORT } = {}) {
    await u.type(screen.getByLabelText('Nom affiché'), 'Yassir')
    await u.type(screen.getByLabelText('Adresse e-mail'), 'yassir@gmail.com')
    await u.type(screen.getByLabelText('Téléphone'), '06 45 48 84 42')
    await u.type(screen.getByLabelText('Date de naissance'), '2001-04-17')
    await u.type(screen.getByLabelText('Mot de passe'), motDePasse)
    await u.type(screen.getByLabelText('Confirme le mot de passe'), confirmation)
    await u.click(screen.getByRole('button', { name: 'Créer mon compte' }))
  }

  it('envoie e-mail, pays, numéro international et date, puis demande le code reçu par e-mail', async () => {
    const corps: unknown[] = []
    const reseau = vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree) => {
      const requete = entree as Request
      if (cheminDe(entree).endsWith('/auth/register')) {
        corps.push(await requete.clone().json())
        return reponse({ id: 'u1' }, 201)
      }
      return reponse(PAIRE)
    })
    const u = userEvent.setup()
    rendre('/inscription')

    await remplir(u)

    expect(await screen.findByRole('heading', { name: 'Vérifie ton e-mail' })).toBeTruthy()
    expect(screen.getByRole('status').textContent).toContain('yassir@gmail.com')
    expect(corps[0]).toEqual({
      displayName: 'Yassir',
      email: 'yassir@gmail.com',
      phone: '+212645488442',
      phoneCountry: 'MA',
      birthDate: '2001-04-17',
      password: MDP_FORT,
    })
    // pas de connexion tant que l'e-mail n'est pas vérifié (SCRUM-133)
    expect(reseau.mock.calls.map(([e]) => cheminDe(e))).toEqual(['/api/v1/auth/register'])
  })

  it('confirmation différente : rien n’est envoyé, erreur sous le champ', async () => {
    const reseau = vi.spyOn(globalThis, 'fetch')
    const u = userEvent.setup()
    rendre('/inscription')

    await remplir(u, { confirmation: 'Autre-Chose-2026' })

    expect(await screen.findByText('Les deux mots de passe ne sont pas identiques.')).toBeTruthy()
    expect(reseau).not.toHaveBeenCalled()
  })

  it('mot de passe trop faible : bloqué avant l’envoi', async () => {
    const reseau = vi.spyOn(globalThis, 'fetch')
    const u = userEvent.setup()
    rendre('/inscription')

    await remplir(u, { motDePasse: MOT_DE_PASSE, confirmation: MOT_DE_PASSE })

    expect(await screen.findByText('Le mot de passe ne respecte pas encore toutes les règles.')).toBeTruthy()
    expect(reseau).not.toHaveBeenCalled()
  })

  it('les règles se cochent pendant la saisie', async () => {
    const u = userEvent.setup()
    rendre('/inscription')
    await u.type(screen.getByLabelText('Mot de passe'), MDP_FORT)
    expect(screen.getAllByText(/: respecté/)).toHaveLength(3)
  })

  it('bouton œil : affiche puis masque le mot de passe', async () => {
    const u = userEvent.setup()
    rendre('/inscription')
    const champ = screen.getByLabelText('Mot de passe') as HTMLInputElement
    await u.click(screen.getAllByRole('button', { name: 'Afficher le mot de passe' })[0])
    expect(champ.type).toBe('text')
    await u.click(screen.getByRole('button', { name: 'Masquer le mot de passe' }))
    expect(champ.type).toBe('password')
  })

  it('compte existant (409) : message générique, pas de session', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () =>
      reponse(
        {
          type: 'https://teamslot.app/problems/account-exists',
          title: 'Compte déjà existant',
          status: 409,
        },
        409,
      ),
    )
    const u = userEvent.setup()
    rendre('/inscription')

    await remplir(u)

    expect((await screen.findByRole('alert')).textContent).toContain('ce numéro ou cette adresse e-mail')
    expect(session.estConnecte()).toBe(false)
  })

  it('mot de passe refusé par le serveur : on affiche ses règles', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () =>
      reponse(
        {
          type: 'https://teamslot.app/problems/weak-password',
          status: 400,
          violations: ['ne pas contenir le nom affiché'],
        },
        400,
      ),
    )
    const u = userEvent.setup()
    rendre('/inscription')

    await remplir(u)

    expect((await screen.findByRole('alert')).textContent).toBe('Mot de passe refusé : ne pas contenir le nom affiché.')
  })
})

describe('vérification de l’e-mail (SCRUM-135)', () => {
  const PROBLEME = (type: string, status: number) =>
    reponse({ type: `https://teamslot.app/problems/${type}`, title: type, status }, status)

  function rendreVerification(etat: Record<string, unknown>) {
    return render(
      <MemoryRouter initialEntries={[{ pathname: '/verification', state: etat }]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    )
  }

  it('bon code : retour à la connexion avec un message de réussite', async () => {
    const corps: unknown[] = []
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree) => {
      corps.push(await (entree as Request).clone().json())
      return new Response(null, { status: 204 })
    })
    const u = userEvent.setup()
    rendreVerification({ email: 'yassir@gmail.com' })

    await u.type(screen.getByLabelText('Code reçu par e-mail'), '482913')
    await u.click(screen.getByRole('button', { name: 'Vérifier' }))

    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeTruthy()
    expect(screen.getByRole('status').textContent).toBe('Ton e-mail est vérifié. Tu peux te connecter.')
    expect(corps[0]).toEqual({ email: 'yassir@gmail.com', code: '482913' })
  })

  it.each([
    ['invalid-code', 400, 'Ce code n’est pas le bon. Vérifie-le, ou demande un nouveau code.'],
    ['code-expired', 410, 'Ce code a expiré. Demande un nouveau code.'],
    ['too-many-attempts', 429, 'Trop d’essais pour ce code. Demande un nouveau code.'],
  ])('erreur %s : message clair', async (type, status, message) => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => PROBLEME(type, status))
    const u = userEvent.setup()
    rendreVerification({ email: 'yassir@gmail.com' })

    await u.type(screen.getByLabelText('Code reçu par e-mail'), '000000')
    await u.click(screen.getByRole('button', { name: 'Vérifier' }))

    expect((await screen.findByRole('alert')).textContent).toBe(message)
  })

  it('seuls les chiffres sont gardés, et il en faut 6', async () => {
    const reseau = vi.spyOn(globalThis, 'fetch')
    const u = userEvent.setup()
    rendreVerification({ email: 'yassir@gmail.com' })

    await u.type(screen.getByLabelText('Code reçu par e-mail'), '12ab3')
    expect((screen.getByLabelText('Code reçu par e-mail') as HTMLInputElement).value).toBe('123')
    await u.click(screen.getByRole('button', { name: 'Vérifier' }))

    expect((await screen.findByRole('alert')).textContent).toBe('Le code fait 6 chiffres.')
    expect(reseau).not.toHaveBeenCalled()
  })

  it('après l’inscription, « Renvoyer le code » attend 60 s (comme le serveur)', () => {
    rendreVerification({ email: 'yassir@gmail.com' })
    const bouton = screen.getByRole('button', { name: /Renvoyer le code/ }) as HTMLButtonElement
    expect(bouton.disabled).toBe(true)
    expect(bouton.textContent).toContain('60 s')
  })

  it('connexion refusée car e-mail non vérifié : page du code, qui demande l’e-mail, renvoi possible', async () => {
    const chemins: string[] = []
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree) => {
      chemins.push(cheminDe(entree))
      return cheminDe(entree).endsWith('/auth/login')
        ? PROBLEME('email-not-verified', 403)
        : new Response(null, { status: 202 })
    })
    const u = userEvent.setup()
    rendre('/connexion')
    await connecter(u)

    expect(await screen.findByRole('heading', { name: 'Vérifie ton e-mail' })).toBeTruthy()
    await u.type(screen.getByLabelText('Adresse e-mail'), 'yassir@gmail.com')
    await u.click(screen.getByRole('button', { name: 'Renvoyer le code' }))

    expect((await screen.findByRole('status')).textContent).toContain('un nouveau code vient de partir')
    expect(chemins).toEqual(['/api/v1/auth/login', '/api/v1/auth/email/resend'])
    expect(session.estConnecte()).toBe(false)
  })
})

describe('accès et déconnexion', () => {
  it('sans session, une page réservée renvoie vers la connexion', () => {
    rendre('/terrains')
    expect(screen.getByRole('heading', { name: 'Connexion' })).toBeTruthy()
  })

  it('« Se déconnecter » efface le jeton et revient à la connexion', async () => {
    session.ouvrir({
      accessToken: 'jeton-acces',
      refreshToken: 'jeton-refresh',
    })
    const u = userEvent.setup()
    rendre('/terrains')
    expect(await screen.findByRole('heading', { name: 'Terrains' })).toBeTruthy()

    await u.click(screen.getByRole('button', { name: 'Se déconnecter' }))

    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeTruthy()
    expect(session.accessToken()).toBeNull()
    expect(session.refreshToken()).toBeNull()
  })
})
