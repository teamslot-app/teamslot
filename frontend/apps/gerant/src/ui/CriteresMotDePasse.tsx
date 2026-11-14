import type { Regle } from '../motDePasse'

/** Liste des règles du mot de passe, cochées au fur et à mesure. Icône + texte : pas la couleur seule. */
export default function CriteresMotDePasse({ regles }: { regles: Regle[] }) {
  return (
    <ul className="mt-1 flex flex-col gap-1">
      {regles.map((r) => (
        <li key={r.id} className={`flex items-center gap-2 ${r.respectee ? 'text-craie' : 'text-sauge'}`}>
          <svg
            aria-hidden="true"
            viewBox="0 0 20 20"
            className="size-4 shrink-0 fill-none stroke-current"
            strokeWidth="2"
          >
            {r.respectee ? (
              <path d="M4 10.5l4 4 8-9" strokeLinecap="round" strokeLinejoin="round" />
            ) : (
              <circle cx="10" cy="10" r="6" />
            )}
          </svg>
          <span>
            {r.libelle}
            <span className="sr-only">{r.respectee ? ' : respecté' : ' : pas encore'}</span>
          </span>
        </li>
      ))}
    </ul>
  )
}
