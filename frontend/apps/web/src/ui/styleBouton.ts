import { classes } from './classes'

export type Variante = 'action' | 'primaire' | 'secondaire' | 'discret'

const BASE =
  'inline-flex min-h-11 min-w-11 items-center justify-center gap-2 rounded-sm px-4 py-2 text-base font-semibold ' +
  'cursor-pointer transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-60'

const VARIANTES: Record<Variante, string> = {
  action:
    'bg-action text-surface shadow-sm hover:bg-action-hover disabled:hover:bg-action',
  primaire:
    'bg-primary text-surface shadow-sm hover:bg-ink disabled:hover:bg-primary',
  secondaire: 'border-2 border-primary bg-surface text-primary hover:bg-paper',
  discret: 'text-primary underline underline-offset-4 hover:text-ink',
}

/** Classes d'un bouton (44 px minimum), pour donner la même apparence à un autre élément. */
export function classesBouton(
  variante: Variante = 'primaire',
  pleineLargeur = false,
): string {
  return classes(BASE, VARIANTES[variante], pleineLargeur && 'w-full')
}
