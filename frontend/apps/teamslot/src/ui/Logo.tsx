import { Link } from 'react-router-dom'

/** Le nom, et la marque de la maquette Figma : un ballon stylisé (cercle citron et trois points). */
export default function Logo() {
  return (
    <Link to="/" className="inline-flex min-h-11 items-center gap-2.5 font-titre text-xl font-bold tracking-tight text-papier">
      <svg aria-hidden="true" viewBox="0 0 28 28" className="size-7 fill-citron stroke-citron">
        <circle cx="14" cy="14" r="12.5" fill="none" strokeWidth="2" />
        <circle cx="8.5" cy="12" r="2" stroke="none" />
        <circle cx="19.5" cy="12" r="2" stroke="none" />
        <circle cx="14" cy="19.5" r="2" stroke="none" />
      </svg>
      TeamSlot
    </Link>
  )
}
