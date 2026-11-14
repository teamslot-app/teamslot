import { Link } from 'react-router-dom'

/** Le nom, l'étoile rouge du drapeau marocain, et « Gérant » pour ne pas confondre avec la plateforme joueur. */
export default function Logo() {
  return (
    <Link to="/" className="inline-flex min-h-11 items-center gap-2 text-xl font-extrabold tracking-tight text-creme">
      <svg aria-hidden="true" viewBox="-12 -12 24 24" className="size-6 fill-none stroke-rouge-vif" strokeWidth="2.2">
        <polygon points="0,-10 5.9,8.1 -9.5,-3.1 9.5,-3.1 -5.9,8.1" strokeLinejoin="round" />
      </svg>
      TeamSlot
      <span className="rounded-[2px] border border-sauge/40 px-1.5 py-0.5 text-xs font-semibold tracking-normal text-sauge">
        Gérant
      </span>
    </Link>
  )
}
