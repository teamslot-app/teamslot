import { Navigate, Route, Routes } from 'react-router-dom'
import RequireAuth from './auth/RequireAuth'
import Connexion from './pages/Connexion'
import Creneaux from './pages/Creneaux'
import Inscription from './pages/Inscription'
import Layout from './pages/Layout'
import MatchsOuverts from './pages/MatchsOuverts'
import MesMatchs from './pages/MesMatchs'
import PageMatch from './pages/PageMatch'
import Profil from './pages/Profil'
import Reservation from './pages/Reservation'
import Terrains from './pages/Terrains'

export default function App() {
  return (
    <Routes>
      <Route path="/connexion" element={<Connexion />} />
      <Route path="/inscription" element={<Inscription />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<Terrains />} />
        <Route path="/terrains/:venueId" element={<Creneaux />} />
        <Route path="/terrains/:venueId/creneaux/:slotId" element={<Reservation />} />
        <Route path="/matchs/:matchId" element={<PageMatch />} />
        <Route path="/matchs-ouverts" element={<MatchsOuverts />} />
        <Route path="/mes-matchs" element={<MesMatchs />} />
        <Route path="/profil" element={<Profil />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
