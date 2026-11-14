import { Link } from 'react-router-dom'

/** Le nom, et l'étoile à cinq branches du drapeau marocain, en rouge. */
export default function Logo() {
  return (
    <Link to="/" className="inline-flex min-h-11 items-center gap-2 text-xl font-extrabold tracking-tight text-creme">
      <svg aria-hidden="true" viewBox="-12 -12 24 24" className="size-6 fill-none stroke-rouge-vif" strokeWidth="2.2">
        <polygon points="0,-10 5.9,8.1 -9.5,-3.1 9.5,-3.1 -5.9,8.1" strokeLinejoin="round" />
      </svg>
      TeamSlot
    </Link>
  )
}
