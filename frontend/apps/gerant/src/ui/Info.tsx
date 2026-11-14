/** Message de réussite lu poliment par les lecteurs d'écran (role="status"), avec une icône : pas la couleur seule. */
export default function Info({ children }: { children: string }) {
  return (
    <p role="status" className="flex items-start gap-2 text-sm font-medium text-craie">
      <svg
        aria-hidden="true"
        viewBox="0 0 20 20"
        className="mt-0.5 size-4 shrink-0 fill-none stroke-current"
        strokeWidth="2"
      >
        <path d="M4 10.5l4 4 8-9" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      {children}
    </p>
  )
}
