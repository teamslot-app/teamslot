import type { ReactNode } from 'react'
import { classes } from './classes'
import { Icone, type NomIcone } from './Icone'

export type Statut = 'confirme' | 'attente' | 'annule'

const STATUTS: Record<
  Statut,
  { libelle: string; icone: NomIcone; couleurs: string }
> = {
  confirme: {
    libelle: 'Match confirmé',
    icone: 'check',
    couleurs: 'bg-primary text-surface',
  },
  attente: {
    libelle: 'En attente',
    icone: 'horloge',
    couleurs: 'bg-accent text-ink',
  },
  annule: {
    libelle: 'Annulé',
    icone: 'croix',
    couleurs: 'border-2 border-error bg-surface text-error',
  },
}

type Proprietes = { statut: Statut; children?: ReactNode; className?: string }

/**
 * Badge de statut : texte en casse normale dans le DOM (lecteurs d'écran, tests), affiché en capitales par CSS.
 * Le libellé peut être remplacé (« En attente de paiement ») ; la couleur n'est jamais le seul indice.
 */
export function BadgeStatut({ statut, children, className }: Proprietes) {
  const { libelle, icone, couleurs } = STATUTS[statut]
  return (
    <span
      className={classes(
        'inline-flex items-center gap-1.5 rounded-xs px-2.5 py-1 font-display text-lg leading-none tracking-wide whitespace-nowrap uppercase',
        couleurs,
        className,
      )}
    >
      <Icone nom={icone} className="size-4" />
      <span>{children ?? libelle}</span>
    </span>
  )
}
