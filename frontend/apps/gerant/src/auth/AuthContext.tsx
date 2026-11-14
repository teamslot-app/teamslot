import { useSyncExternalStore, type ReactNode } from 'react'
import { seConnecter, sInscrire } from './actions'
import { session } from './session'
import { AuthContext } from './useAuth'

export function AuthProvider({ children }: { children: ReactNode }) {
  const estConnecte = useSyncExternalStore(session.abonner, session.estConnecte)
  return (
    <AuthContext
      value={{
        estConnecte,
        role: estConnecte ? session.role() : null,
        seConnecter,
        sInscrire,
        seDeconnecter: session.fermer,
      }}
    >
      {children}
    </AuthContext>
  )
}
