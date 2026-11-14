/** Message d'erreur lu immédiatement par les lecteurs d'écran (role="alert"), avec une icône : pas la couleur seule. */
export default function Alerte({ children }: { children: string }) {
  return (
    <p role="alert" className="flex items-start gap-2 text-sm font-medium text-alerte">
      <svg aria-hidden="true" viewBox="0 0 20 20" className="mt-0.5 size-4 shrink-0 fill-current">
        <path d="M10 1.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17Zm-.9 4.5h1.8v5.5H9.1V6Zm0 7h1.8v1.8H9.1V13Z" />
      </svg>
      {children}
    </p>
  )
}
