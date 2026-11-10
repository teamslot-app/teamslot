import type { ReactNode } from 'react'
import { classes } from './classes'

type Proprietes = { children: ReactNode; className?: string }

/** Largeur de lecture avec une gouttière de 16 px sur téléphone (375 px), 32 px sur bureau. */
export function Conteneur({ children, className }: Proprietes) {
  return (
    <div
      className={classes('mx-auto w-full max-w-6xl px-4 lg:px-8', className)}
    >
      {children}
    </div>
  )
}

/** Grille de 4 colonnes sur mobile, 12 sur bureau (lg). Les enfants choisissent leur largeur (col-span-4 lg:col-span-6…). */
export function Grille({ children, className }: Proprietes) {
  return (
    <div
      className={classes(
        'grid grid-cols-4 gap-4 lg:grid-cols-12 lg:gap-6',
        className,
      )}
    >
      {children}
    </div>
  )
}
