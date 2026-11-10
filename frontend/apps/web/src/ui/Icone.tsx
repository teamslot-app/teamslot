import { classes } from './classes'

/** Tracés au format 24×24, trait de 2 px : SVG inline, aucune image ni police d'icônes externe. */
const TRACES = {
  check: 'M5 12.5l4.5 4.5L19 7.5',
  horloge: 'M12 7v5l3 2M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z',
  croix: 'M6 6l12 12M18 6L6 18',
  alerte:
    'M12 8v5M12 16.5v.5M10.3 3.9L2.4 17.5A2 2 0 0 0 4.1 20.5h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z',
  fleche: 'M5 12h14M13 6l6 6-6 6',
} as const

export type NomIcone = keyof typeof TRACES

/** Icône décorative : le sens est toujours porté par un texte voisin, donc masquée aux lecteurs d'écran. */
export function Icone({
  nom,
  className,
}: {
  nom: NomIcone
  className?: string
}) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      className={classes('size-5 shrink-0', className)}
    >
      <path d={TRACES[nom]} />
    </svg>
  )
}
