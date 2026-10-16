import { Navigate, Route, Routes } from 'react-router-dom'
import RequireAuth from './auth/RequireAuth'
import Connexion from './pages/Connexion'
import Creneaux from './pages/Creneaux'
import Inscription from './pages/Inscription'
import Layout from './pages/Layout'
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
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
