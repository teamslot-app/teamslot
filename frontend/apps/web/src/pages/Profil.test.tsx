import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { session } from '../auth/session'

const json = (corps: unknown, status = 200) =>
  new Response(JSON.stringify(corps), { status, headers: { 'Content-Type': 'application/json' } })
const urlDe = (entree: RequestInfo | URL) => new URL(entree instanceof Request ? entree.url : String(entree))

const PROFIL = {
  id: '0b6f2c1e-5d1a-4c3e-9f7a-2a1b3c4d5e6f',
  phone: '+212600000001',
  displayName: 'Yassir',
  platformRole: 'PLAYER',
  managerStatus: 'NONE',
}

function rendre() {
  return render(
    <MemoryRouter initialEntries={['/profil']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

/** Faux serveur : GET renvoie le profil, PATCH renvoie la réponse donnée. */
function serveur(reponsePatch: () => Response = () => json({ ...PROFIL, displayName: 'Zaki' })) {
  return vi
    .spyOn(globalThis, 'fetch')
    .mockImplementation(async (entree) => ((entree as Request).method === 'PATCH' ? reponsePatch() : json(PROFIL)))
}

const champSurnom = async () => (await screen.findByLabelText('Surnom')) as HTMLInputElement
const bouton = () => screen.getByRole('button', { name: 'Enregistrer' }) as HTMLButtonElement

beforeEach(() => session.ouvrir({ accessToken: 'jeton', refreshToken: 'r1' }))
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  session.fermer()
})

describe('mon profil (SCRUM-95)', () => {
  it('affiche mon surnom et mon téléphone', async () => {
    serveur()
    rendre()

    expect((await champSurnom()).value).toBe('Yassir')
    expect(screen.getByText(/Téléphone : \+212600000001/)).toBeTruthy()
  })

  it('appelle GET /api/v1/users/me avec le jeton, sans identifiant dans l’URL', async () => {
    const appel = serveur()
    rendre()
    await champSurnom()

    const requete = appel.mock.calls[0][0] as Request
    expect(requete.method).toBe('GET')
    expect(urlDe(requete).pathname).toBe('/api/v1/users/me')
    expect(urlDe(requete).search).toBe('')
    expect(requete.headers.get('Authorization')).toBe('Bearer jeton')
  })

  it('enregistre le surnom : PATCH avec displayName seul, sans espaces autour', async () => {
    const appel = serveur()
    rendre()

    fireEvent.change(await champSurnom(), { target: { value: '  Zaki  ' } })
    fireEvent.click(bouton())

    expect((await screen.findByRole('status')).textContent).toBe('Surnom enregistré.')
    const patch = appel.mock.calls.map(([r]) => r as Request).find((r) => r.method === 'PATCH')
    expect(patch).toBeDefined()
    expect(urlDe(patch!).pathname).toBe('/api/v1/users/me')
    expect(patch!.headers.get('Authorization')).toBe('Bearer jeton')
    expect(JSON.parse(await patch!.text())).toEqual({ displayName: 'Zaki' })
    expect((await champSurnom()).value).toBe('Zaki')
  })

  it('surnom vide ou fait d’espaces : bouton désactivé, aucun envoi', async () => {
    const appel = serveur()
    rendre()

    fireEvent.change(await champSurnom(), { target: { value: '   ' } })
    expect(bouton().disabled).toBe(true)
    fireEvent.click(bouton())
    expect(appel.mock.calls.some(([r]) => (r as Request).method === 'PATCH')).toBe(false)
  })

  it('le champ limite le surnom à 40 caractères', async () => {
    serveur()
    rendre()

    expect((await champSurnom()).maxLength).toBe(40)
  })

  it('surnom refusé par le serveur (400) : message d’erreur', async () => {
    serveur(() =>
      json({ type: 'x', title: 'Profil invalide', status: 400, detail: 'Surnom invalide.' }, 400),
    )
    rendre()

    fireEvent.change(await champSurnom(), { target: { value: 'Zaki' } })
    fireEvent.click(bouton())
    expect((await screen.findByRole('alert')).textContent).toMatch(/Surnom non enregistré/)
  })

  it('profil impossible à charger : message d’erreur', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('réseau'))
    rendre()

    expect((await screen.findByRole('alert')).textContent).toMatch(/Impossible de charger ton profil/)
  })

  it('lien « Mon profil » dans l’en-tête', async () => {
    serveur()
    rendre()

    expect((await screen.findByRole('link', { name: 'Mon profil' })).getAttribute('href')).toBe('/profil')
  })
})
