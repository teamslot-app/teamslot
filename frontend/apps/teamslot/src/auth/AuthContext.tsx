import { useSyncExternalStore, type ReactNode } from 'react'
import { seConnecter, sInscrire } from './actions'
import { session } from './session'
import { AuthContext } from './useAuth'

export function AuthProvider({ children }: { children: ReactNode }) {
  const estConnecte = useSyncExternalStore(session.abonner, session.estConnecte)
  return (
    <AuthContext value={{ estConnecte, seConnecter, sInscrire, seDeconnecter: session.fermer }}>
      {children}
    </AuthContext>
  )
}
