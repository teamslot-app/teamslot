import { Navigate, Route, Routes } from 'react-router-dom'
import RequireAuth, { RequireGerant } from './auth/RequireAuth'
import { useAuth } from './auth/useAuth'
import BientotDisponible from './pages/BientotDisponible'
import Connexion from './pages/Connexion'
import Inscription from './pages/Inscription'
import InscrireMonTerrain from './pages/InscrireMonTerrain'
import Layout from './pages/Layout'
import VerificationEmail from './pages/VerificationEmail'

/** « / » : connexion si personne n'est connecté, espace gérant si le jeton porte VENUE_MANAGER, sinon le dossier. */
function Aiguillage() {
  const { estConnecte, role } = useAuth()
  if (!estConnecte) return <Navigate to="/connexion" replace />
  return <Navigate to={role === 'VENUE_MANAGER' ? '/mon-terrain' : '/inscrire-mon-terrain'} replace />
}

/** SCRUM-140 — plateforme propriétaire : même backend et mêmes comptes que la plateforme joueur. */
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Aiguillage />} />
      <Route path="/connexion" element={<Connexion />} />
      <Route path="/inscription" element={<Inscription />} />
      <Route path="/verification" element={<VerificationEmail />} />
      <Route
        path="/inscrire-mon-terrain"
        element={
          <RequireAuth>
            <InscrireMonTerrain />
          </RequireAuth>
        }
      />
      <Route
        element={
          <RequireAuth>
            <RequireGerant>
              <Layout />
            </RequireGerant>
          </RequireAuth>
        }
      >
        <Route path="/mon-terrain" element={<BientotDisponible titre="Mon terrain" />} />
        <Route path="/reservations" element={<BientotDisponible titre="Réservations" />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
