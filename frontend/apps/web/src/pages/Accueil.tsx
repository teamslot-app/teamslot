import { useAuth } from '../auth/useAuth'

// Temporaire (SCRUM-94) : remplacée par la liste des terrains à l'étape suivante.
export default function Accueil() {
  const { seDeconnecter } = useAuth()
  return (
    <main>
      <h1>TeamSlot</h1>
      <p>Connecté.</p>
      <button type="button" onClick={seDeconnecter}>
        Se déconnecter
      </button>
    </main>
  )
}
