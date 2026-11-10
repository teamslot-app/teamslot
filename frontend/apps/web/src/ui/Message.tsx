import type { ReactNode } from 'react'
import { classes } from './classes'
import { Icone } from './Icone'

const BASE = 'flex items-start gap-2 rounded-sm border-l-4 px-3 py-2 text-base'

/** Erreur annoncée immédiatement (role="alert") : icône + texte, rouge brique distinct du rouge d'action. */
export function Alerte({
  children,
  className,
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <p
      role="alert"
      className={classes(
        BASE,
        'border-error bg-error-bg text-error',
        className,
      )}
    >
      <Icone nom="alerte" className="mt-0.5" />
      <span>{children}</span>
    </p>
  )
}

/** Confirmation annoncée poliment (role="status"). */
export function Confirmation({
  children,
  className,
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <p
      role="status"
      className={classes(
        BASE,
        'border-confirm bg-confirm-bg text-confirm',
        className,
      )}
    >
      <Icone nom="check" className="mt-0.5" />
      <span>{children}</span>
    </p>
  )
}
