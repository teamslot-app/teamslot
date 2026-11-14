import { Navigate, Route, Routes } from 'react-router-dom'
import RequireAuth from './auth/RequireAuth'
import Accueil from './pages/Accueil'
import BientotDisponible from './pages/BientotDisponible'
import Connexion from './pages/Connexion'
import Inscription from './pages/Inscription'
import Layout from './pages/Layout'
import VerificationEmail from './pages/VerificationEmail'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Accueil />} />
      <Route path="/connexion" element={<Connexion />} />
      <Route path="/inscription" element={<Inscription />} />
      <Route path="/verification" element={<VerificationEmail />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/terrains" element={<BientotDisponible titre="Terrains" />} />
        <Route path="/matchs-ouverts" element={<BientotDisponible titre="Matchs ouverts" />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
