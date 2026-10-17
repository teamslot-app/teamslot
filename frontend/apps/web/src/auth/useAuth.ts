import { createContext, useContext } from 'react'

export type AuthValue = {
  estConnecte: boolean
  seConnecter: (phone: string, password: string) => Promise<void>
  sInscrire: (phone: string, password: string, displayName: string) => Promise<void>
  seDeconnecter: () => void
}

export const AuthContext = createContext<AuthValue | null>(null)

export function useAuth(): AuthValue {
  const valeur = useContext(AuthContext)
  if (!valeur) throw new Error('useAuth doit être utilisé dans un <AuthProvider>.')
  return valeur
}
