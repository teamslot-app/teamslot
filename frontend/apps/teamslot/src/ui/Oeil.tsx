/** Icône d'œil (ouvert / barré) pour Afficher / Masquer un mot de passe ; décorative, le bouton porte le nom. */
export default function Oeil({ barre }: { barre: boolean }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className="size-5 fill-none stroke-current"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" />
      <circle cx="12" cy="12" r="3" />
      {barre && <path d="M4 4l16 16" />}
    </svg>
  )
}
