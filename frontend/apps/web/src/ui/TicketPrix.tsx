import type { VenueComponents } from '@teamslot/shared'
import { formatMontant } from '../format'
import { classes } from './classes'

type Money = VenueComponents['schemas']['Money']

type Proprietes = { montant: Money; parHeure?: boolean; className?: string }

/** Prix sur ticket ambre, talon perforé à gauche : « 150,00 MAD / heure ». Le montant vient du serveur. */
export function TicketPrix({
  montant,
  parHeure = false,
  className,
}: Proprietes) {
  return (
    <span
      className={classes(
        'inline-flex items-baseline gap-1 rounded-xs border-l-4 border-dashed border-primary bg-accent px-2.5 py-1 font-display whitespace-nowrap text-ink',
        className,
      )}
    >
      <span className="text-2xl leading-none">{formatMontant(montant)}</span>
      {parHeure && <span className="text-base leading-none">/ heure</span>}
    </span>
  )
}
