import { Navigate, Route, Routes } from 'react-router-dom'
import RequireAuth from './auth/RequireAuth'
import Accueil from './pages/Accueil'
import Connexion from './pages/Connexion'
import Inscription from './pages/Inscription'

export default function App() {
  return (
    <Routes>
      <Route path="/connexion" element={<Connexion />} />
      <Route path="/inscription" element={<Inscription />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Accueil />
          </RequireAuth>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
