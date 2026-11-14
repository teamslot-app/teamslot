import { useAuth } from '../auth/useAuth'
import Logo from '../ui/Logo'

const PIECES = [
  { titre: 'Le terrain', detail: "Son nom, sa surface et ce qu'il propose (vestiaires, éclairage, chasubles…)." },
  { titre: 'La localisation', detail: "L'adresse et le point exact sur la carte, pour que les joueurs le trouvent." },
  { titre: 'Les photos', detail: 'Quelques photos du terrain : ce sont elles que les joueurs verront.' },
  { titre: 'La patente', detail: 'Pour vérifier que tu exploites bien ce terrain. Elle reste privée.' },
]

/**
 * Décision du 13/11 — compte connecté qui n'a pas (encore) le rôle VENUE_MANAGER : sa prochaine étape est le
 * dossier du terrain, qui EST la demande. Le formulaire et l'envoi des fichiers arrivent au Sprint 4 (dossier dans
 * venue-service, fichiers dans MinIO) ; cette page annonce déjà ce qui sera demandé.
 */
export default function InscrireMonTerrain() {
  const { seDeconnecter } = useAuth()
  return (
    <div className="min-h-svh px-6 py-6 sm:px-12">
      <Logo />
      <main className="mx-auto max-w-2xl py-14">
        <h1 className="text-4xl leading-tight font-extrabold tracking-tight text-creme">Inscris ton terrain</h1>
        <p className="mt-4 text-lg text-sauge">
          Ton compte est prêt. Pour ouvrir ton espace gérant, envoie le dossier de ton terrain : un administrateur
          TeamSlot le vérifie, puis ton terrain apparaît chez les joueurs.
        </p>
        <h2 className="mt-10 text-lg font-bold text-creme">Ce qu'il te faudra</h2>
        <ul className="mt-4 flex flex-col gap-4">
          {PIECES.map((p) => (
            <li key={p.titre} className="border-l-2 border-rouge-vif pl-4">
              <span className="block font-semibold text-creme">{p.titre}</span>
              <span className="block text-sauge">{p.detail}</span>
            </li>
          ))}
        </ul>
        <p className="mt-10 text-sauge">Le formulaire du dossier arrive très bientôt dans ton espace.</p>
        <button type="button" onClick={seDeconnecter} className="brassard mt-6">
          Se déconnecter
        </button>
      </main>
    </div>
  )
}
