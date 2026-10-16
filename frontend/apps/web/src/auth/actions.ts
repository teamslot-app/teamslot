import { api } from '../api'
import { session } from './session'

/** Erreur affichable telle quelle à l'utilisateur. */
export class ErreurAuth extends Error {}

const MESSAGE_CONNEXION = 'Téléphone ou mot de passe incorrect'
const MESSAGE_INDISPONIBLE = 'Service momentanément indisponible. Réessaie dans un instant.'

export async function seConnecter(phone: string, password: string): Promise<void> {
  const resultat = await api.identity
    .POST('/api/v1/auth/login', { body: { phone, password } })
    .catch(() => null)
  if (!resultat) throw new ErreurAuth(MESSAGE_INDISPONIBLE)
  if (resultat.data) {
    session.ouvrir(resultat.data)
    return
  }
  // Un seul message pour 400, 401 et 404 : il ne dit pas si le compte existe.
  throw new ErreurAuth(resultat.response.status >= 500 ? MESSAGE_INDISPONIBLE : MESSAGE_CONNEXION)
}

export async function sInscrire(phone: string, password: string, displayName: string): Promise<void> {
  const resultat = await api.identity
    .POST('/api/v1/auth/register', { body: { phone, password, displayName } })
    .catch(() => null)
  if (!resultat) throw new ErreurAuth(MESSAGE_INDISPONIBLE)
  const { status, ok } = resultat.response
  if (status === 409) throw new ErreurAuth('Ce numéro est déjà inscrit.')
  if (status === 400) {
    throw new ErreurAuth(
      'Vérifie le numéro (format international, par exemple +212600000000) et choisis un mot de passe d’au moins 10 caractères.',
    )
  }
  if (!ok) throw new ErreurAuth(MESSAGE_INDISPONIBLE)
  await seConnecter(phone, password)
}
