import type { IdentityComponents } from '@teamslot/shared'

type TokenPair = IdentityComponents['schemas']['TokenPair']

export type Tokens = { accessToken: string; refreshToken: string }

// Jetons EN MÉMOIRE seulement : jamais dans le stockage du navigateur ni dans un cookie (vérifié par gardeFou.test.ts).
let tokens: Tokens | null = null
let refreshEnCours: Promise<boolean> | null = null
const abonnes = new Set<() => void>()

function notifier() {
  abonnes.forEach((abonne) => abonne())
}

async function demanderNouvelleSession(): Promise<boolean> {
  const refreshToken = tokens?.refreshToken
  if (!refreshToken) return false
  try {
    const reponse = await globalThis.fetch(`${window.location.origin}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!reponse.ok) return false
    const paire = (await reponse.json()) as TokenPair
    session.ouvrir(paire)
    return true
  } catch {
    return false
  }
}

/** Un seul rafraîchissement à la fois : les requêtes qui reçoivent un 401 en même temps attendent le même. */
function refresh(): Promise<boolean> {
  refreshEnCours ??= demanderNouvelleSession().finally(() => {
    refreshEnCours = null
  })
  return refreshEnCours
}

/**
 * Rôle lu dans le jeton d'accès, pour l'AFFICHAGE seulement (quelle page montrer).
 * Le front ne vérifie pas la signature : c'est le serveur qui décide des droits, à chaque requête.
 */
function roleDuJeton(jeton: string | undefined): string | null {
  const charge = jeton?.split('.')[1]
  if (!charge) return null
  try {
    const json = atob(charge.replace(/-/g, '+').replace(/_/g, '/'))
    const role = (JSON.parse(json) as { role?: unknown }).role
    return typeof role === 'string' ? role : null
  } catch {
    return null
  }
}

export const session = {
  accessToken: (): string | null => tokens?.accessToken ?? null,
  role: (): string | null => roleDuJeton(tokens?.accessToken),
  refreshToken: (): string | null => tokens?.refreshToken ?? null,
  estConnecte: (): boolean => tokens !== null,
  ouvrir(paire: Tokens) {
    tokens = { accessToken: paire.accessToken, refreshToken: paire.refreshToken }
    notifier()
  },
  fermer() {
    tokens = null
    notifier()
  },
  abonner(abonne: () => void) {
    abonnes.add(abonne)
    return () => {
      abonnes.delete(abonne)
    }
  },
  refresh,
}
