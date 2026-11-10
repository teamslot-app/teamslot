import type { ReactNode } from 'react'
import type { VenueComponents } from '@teamslot/shared'
import { formatHeure } from '../format'
import { classes } from './classes'
import { TicketPrix } from './TicketPrix'

type Money = VenueComponents['schemas']['Money']

type Proprietes = {
  debut: string
  fin: string
  prix?: Money
  disponible: boolean
  /** Action de la rangée (le lien « Réserver » avec son state) ; « Indisponible » la remplace si le créneau est pris. */
  action: ReactNode
}

/** Créneau façon tableau d'affichage : heures en grands chiffres sur fond vert, prix sur ticket, action à droite. */
export function CreneauAffichage({
  debut,
  fin,
  prix,
  disponible,
  action,
}: Proprietes) {
  return (
    <div
      className={classes(
        'flex flex-wrap items-center gap-3 rounded-sm border border-line p-3 shadow-sm',
        disponible ? 'bg-surface' : 'bg-paper',
      )}
    >
      <span className="rounded-xs bg-primary px-3 py-1 font-display text-3xl leading-none tracking-wider text-surface">
        {formatHeure(debut)} – {formatHeure(fin)}
      </span>
      {prix && <TicketPrix montant={prix} parHeure />}
      <span className="ml-auto">
        {disponible ? (
          action
        ) : (
          <span className="text-base font-semibold text-muted">
            Indisponible
          </span>
        )}
      </span>
    </div>
  )
}
