import { classes } from './classes'

export type Variante = 'action' | 'primaire' | 'secondaire' | 'discret'

const BASE =
  'inline-flex min-h-11 min-w-11 items-center justify-center gap-2 rounded-sm px-4 py-2 text-base font-semibold ' +
  'cursor-pointer transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-60'

const VARIANTES: Record<Variante, string> = {
  action:
    'bg-action text-surface no-underline shadow-sm hover:bg-action-hover hover:text-surface disabled:hover:bg-action',
  primaire:
    'bg-primary text-surface no-underline shadow-sm hover:bg-ink hover:text-surface disabled:hover:bg-primary',
  secondaire:
    'border-2 border-primary bg-surface text-primary no-underline hover:bg-paper hover:text-primary',
  // fond transparent explicite : sinon le style de base des <button> (fond action) s'appliquerait
  discret:
    'bg-transparent text-primary underline underline-offset-4 hover:bg-transparent hover:text-ink',
}

/** Classes d'un bouton (44 px minimum), pour donner la même apparence à un autre élément. */
export function classesBouton(
  variante: Variante = 'primaire',
  pleineLargeur = false,
): string {
  return classes(BASE, VARIANTES[variante], pleineLargeur && 'w-full')
}
