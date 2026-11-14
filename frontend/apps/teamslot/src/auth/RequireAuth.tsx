import type { ReactElement } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from './useAuth'

/** Page réservée aux utilisateurs connectés : sinon, retour à l'écran de connexion. */
export default function RequireAuth({ children }: { children: ReactElement }) {
  const { estConnecte } = useAuth()
  return estConnecte ? children : <Navigate to="/connexion" replace />
}
