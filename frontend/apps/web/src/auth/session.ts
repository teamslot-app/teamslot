import type { IdentityComponents } from '@teamslot/shared'

type TokenPair = IdentityComponents['schemas']['TokenPair']

export type Tokens = { accessToken: string; refreshToken: string }

// Jetons EN MÉMOIRE seulement : ni localStorage, ni cookie (voir README, « Jetons »).
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

export const session = {
  accessToken: (): string | null => tokens?.accessToken ?? null,
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
