import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import {
  Alerte,
  BadgeStatut,
  Bouton,
  Champ,
  Confirmation,
  CreneauAffichage,
  Grille,
  LienBouton,
  TicketPrix,
} from '.'

const PRIX = { amount: 15000, currency: 'MAD' }

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('Alerte et Confirmation', () => {
  it('erreur : role alert, texte exact, icône masquée aux lecteurs d’écran', () => {
    render(<Alerte>Ce créneau vient d'être pris.</Alerte>)
    const alerte = screen.getByRole('alert')
    expect(alerte.textContent).toBe("Ce créneau vient d'être pris.")
    expect(alerte.querySelector('svg')?.getAttribute('aria-hidden')).toBe(
      'true',
    )
  })

  it('confirmation : role status, texte exact', () => {
    render(<Confirmation>Ami ajouté.</Confirmation>)
    expect(screen.getByRole('status').textContent).toBe('Ami ajouté.')
  })
})

describe('BadgeStatut', () => {
  it.each([
    ['confirme', 'Match confirmé'],
    ['attente', 'En attente'],
    ['annule', 'Annulé'],
  ] as const)(
    '%s : libellé « %s » en casse normale dans le DOM, capitales par CSS, avec icône',
    (statut, libelle) => {
      render(<BadgeStatut statut={statut} />)
      const texte = screen.getByText(libelle)
      expect(texte.parentElement?.className).toContain('uppercase')
      expect(
        texte.parentElement?.querySelector('svg[aria-hidden="true"]'),
      ).not.toBeNull()
    },
  )

  it('le libellé peut être remplacé', () => {
    render(<BadgeStatut statut="attente">En attente de paiement</BadgeStatut>)
    expect(screen.getByText('En attente de paiement')).toBeTruthy()
  })
})

describe('TicketPrix', () => {
  it('affiche le montant du serveur', () => {
    render(<TicketPrix montant={PRIX} />)
    expect(screen.getByText(/150,00\sMAD/)).toBeTruthy()
    expect(screen.queryByText('/ heure')).toBeNull()
  })

  it('« / heure » en option', () => {
    render(<TicketPrix montant={PRIX} parHeure />)
    expect(screen.getByText(/150,00\sMAD/).parentElement?.textContent).toMatch(
      /^150,00\sMAD\/ heure$/,
    )
  })
})

describe('Bouton et LienBouton', () => {
  it('type button par défaut, 44 px minimum, clic transmis', async () => {
    const clic = vi.fn()
    render(<Bouton onClick={clic}>Réserver ce créneau</Bouton>)
    const bouton = screen.getByRole('button', {
      name: 'Réserver ce créneau',
    }) as HTMLButtonElement
    expect(bouton.type).toBe('button')
    expect(bouton.className).toContain('min-h-11')
    await userEvent.setup().click(bouton)
    expect(clic).toHaveBeenCalledTimes(1)
  })

  it('désactivé : aucun clic', async () => {
    const clic = vi.fn()
    render(
      <Bouton disabled onClick={clic}>
        Réservation…
      </Bouton>,
    )
    const bouton = screen.getByRole('button', {
      name: 'Réservation…',
    }) as HTMLButtonElement
    expect(bouton.disabled).toBe(true)
    await userEvent.setup().click(bouton)
    expect(clic).not.toHaveBeenCalled()
  })

  it('type submit conservé dans un formulaire', () => {
    render(<Bouton type="submit">Se connecter</Bouton>)
    expect(
      (
        screen.getByRole('button', {
          name: 'Se connecter',
        }) as HTMLButtonElement
      ).type,
    ).toBe('submit')
  })

  it('LienBouton : un vrai lien vers la route', () => {
    render(
      <MemoryRouter>
        <LienBouton to="/connexion" variante="action">
          Commencer
        </LienBouton>
      </MemoryRouter>,
    )
    const lien = screen.getByRole('link', { name: 'Commencer' })
    expect(lien.getAttribute('href')).toBe('/connexion')
    expect(lien.className).toContain('min-h-11')
  })
})

describe('Champ', () => {
  it('libellé visible et associé, saisie contrôlée', async () => {
    const changement = vi.fn()
    render(
      <Champ
        libelle="Téléphone"
        type="tel"
        name="phone"
        value=""
        onChange={changement}
      />,
    )
    const champ = screen.getByLabelText('Téléphone')
    expect(champ.getAttribute('name')).toBe('phone')
    await userEvent.setup().type(champ, '6')
    expect(changement).toHaveBeenCalled()
  })

  it('aide et erreur reliées par aria-describedby, champ marqué invalide', () => {
    render(
      <Champ
        libelle="Mot de passe"
        type="password"
        aide="10 caractères minimum"
        erreur="Trop court"
        readOnly
      />,
    )
    const champ = screen.getByLabelText('Mot de passe')
    const ids = champ.getAttribute('aria-describedby')?.split(' ') ?? []
    expect(ids.map((id) => document.getElementById(id)?.textContent)).toEqual([
      '10 caractères minimum',
      'Trop court',
    ])
    expect(champ.getAttribute('aria-invalid')).toBe('true')
  })

  it('sans erreur : ni aria-invalid ni description vide', () => {
    render(<Champ libelle="Nom affiché" readOnly />)
    const champ = screen.getByLabelText('Nom affiché')
    expect(champ.hasAttribute('aria-invalid')).toBe(false)
    expect(champ.hasAttribute('aria-describedby')).toBe(false)
  })
})

describe('CreneauAffichage', () => {
  const debut = '2026-10-20T18:00:00Z'
  const fin = '2026-10-20T19:00:00Z'

  it('disponible : heures de Marrakech, prix par heure, action', () => {
    render(
      <MemoryRouter>
        <CreneauAffichage
          debut={debut}
          fin={fin}
          prix={PRIX}
          disponible
          action={<LienBouton to="/x">Réserver</LienBouton>}
        />
      </MemoryRouter>,
    )
    expect(screen.getByText('19:00 – 20:00')).toBeTruthy()
    expect(screen.getByText('/ heure')).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Réserver' })).toBeTruthy()
  })

  it('pris : « Indisponible » à la place de l’action', () => {
    render(
      <CreneauAffichage
        debut={debut}
        fin={fin}
        disponible={false}
        action={<a href="/x">Réserver</a>}
      />,
    )
    expect(screen.getByText('Indisponible')).toBeTruthy()
    expect(screen.queryByRole('link')).toBeNull()
  })
})

describe('Grille', () => {
  it('4 colonnes sur mobile, 12 sur bureau', () => {
    const { container } = render(<Grille>contenu</Grille>)
    const classesGrille = (container.firstElementChild as HTMLElement).className
    expect(classesGrille).toContain('grid-cols-4')
    expect(classesGrille).toContain('lg:grid-cols-12')
  })
})
