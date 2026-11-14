import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import App from './App'
import { AuthProvider } from './auth/AuthContext'

function ouvrir(chemin: string) {
  return render(
    <MemoryRouter initialEntries={[chemin]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

afterEach(cleanup)

describe('accueil', () => {
  it("affiche une seule accroche, qui couvre tout le Maroc", () => {
    ouvrir('/')
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Réserve ton terrain. Organise ton match.')
    expect(screen.getByText(/De Tanger à Laâyoune/)).toBeTruthy()
    expect(screen.queryByText(/Marrakech/)).toBeNull()
  })

  it("le bouton principal mène à l'inscription", async () => {
    ouvrir('/')
    await userEvent.click(screen.getByRole('link', { name: 'Créer mon compte' }))
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Créer un compte')
  })

  it('« Se connecter » mène à la connexion', async () => {
    ouvrir('/')
    await userEvent.click(screen.getByRole('link', { name: 'Se connecter' }))
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Connexion')
  })

  it("une adresse inconnue renvoie à l'accueil", () => {
    ouvrir('/nimporte-quoi')
    expect(screen.getByRole('link', { name: 'Créer mon compte' })).toBeTruthy()
  })

  it("sans WebGL (ou animations réduites) : image fixe du terrain, décorative, et pas de consigne de jeu", () => {
    const { container } = ouvrir('/')
    const image = container.querySelector('img')
    expect(image?.getAttribute('alt')).toBe('')
    expect(image?.getAttribute('src')).toBe('/accueil/affiche-bureau.webp')
    expect(screen.queryByText(/Attrape le ballon/)).toBeNull()
  })
})
