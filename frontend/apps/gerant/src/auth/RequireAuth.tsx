import type { ReactElement } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from './useAuth'

/** Page réservée aux utilisateurs connectés : sinon, retour à l'écran de connexion. */
export default function RequireAuth({ children }: { children: ReactElement }) {
  const { estConnecte } = useAuth()
  return estConnecte ? children : <Navigate to="/connexion" replace />
}

/** Pages de l'espace gérant : réservées au rôle VENUE_MANAGER, sinon la page « Inscris ton terrain ». */
export function RequireGerant({ children }: { children: ReactElement }) {
  const { role } = useAuth()
  return role === 'VENUE_MANAGER' ? children : <Navigate to="/inscrire-mon-terrain" replace />
}
