import { api } from '../api'
import { session } from './session'

/** Erreur affichable telle quelle à l'utilisateur. */
export class ErreurAuth extends Error {}

/** SCRUM-135 : mot de passe correct, mais e-mail pas encore vérifié (403) : on propose de saisir le code. */
export class ErreurEmailNonVerifie extends ErreurAuth {}

const MESSAGE_CONNEXION = 'Téléphone ou mot de passe incorrect'
const MESSAGE_INDISPONIBLE = 'Service momentanément indisponible. Réessaie dans un instant.'

export async function seConnecter(phone: string, password: string): Promise<void> {
  const resultat = await api.identity.POST('/api/v1/auth/login', { body: { phone, password } }).catch(() => null)
  if (!resultat) throw new ErreurAuth(MESSAGE_INDISPONIBLE)
  if (resultat.data) {
    session.ouvrir(resultat.data)
    return
  }
  const type = ((resultat.error ?? {}) as { type?: string }).type?.split('/').pop()
  if (resultat.response.status === 403 && type === 'email-not-verified') {
    throw new ErreurEmailNonVerifie("Ton e-mail n'est pas encore vérifié.")
  }
  // Un seul message pour 400, 401 et 404 : il ne dit pas si le compte existe.
  throw new ErreurAuth(resultat.response.status >= 500 ? MESSAGE_INDISPONIBLE : MESSAGE_CONNEXION)
}

/** Inscription propriétaire : formulaire v2 (SCRUM-135). Un compte simple : devenir gérant passe par le dossier du terrain. */
export type DemandeInscription = {
  displayName: string
  email: string
  phone: string
  phoneCountry: string
  birthDate: string
  password: string
}

/** Message affiché pour chaque erreur du serveur (type du ProblemDetail). */
const MESSAGES_INSCRIPTION: Record<string, string> = {
  'account-exists': 'Un compte existe déjà avec ce numéro ou cette adresse e-mail. Essaie de te connecter.',
  'invalid-phone': 'Ce numéro ne correspond pas au pays choisi.',
  'invalid-birth-date': 'Vérifie ta date de naissance.',
}

export async function sInscrire(demande: DemandeInscription): Promise<void> {
  // Décision du 13/11 : aucune demande à l'inscription. Le rôle VENUE_MANAGER n'est donné que par un administrateur,
  // après validation du dossier du terrain (photos, patente, localisation) envoyé depuis l'espace propriétaire.
  const resultat = await api.identity.POST('/api/v1/auth/register', { body: demande }).catch(() => null)
  if (!resultat) throw new ErreurAuth(MESSAGE_INDISPONIBLE)
  const { status, ok } = resultat.response
  if (status === 400 || status === 409) {
    const probleme = (resultat.error ?? {}) as {
      type?: string
      violations?: unknown
    }
    const type = probleme.type?.split('/').pop() ?? ''
    if (type === 'weak-password' && Array.isArray(probleme.violations)) {
      throw new ErreurAuth(`Mot de passe refusé : ${probleme.violations.join(', ')}.`)
    }
    throw new ErreurAuth(
      MESSAGES_INSCRIPTION[type] ??
        (status === 409 ? MESSAGES_INSCRIPTION['account-exists'] : 'Vérifie les champs du formulaire.'),
    )
  }
  if (!ok) throw new ErreurAuth(MESSAGE_INDISPONIBLE)
  // SCRUM-133/135 : pas de connexion tout de suite : l'e-mail doit d'abord être vérifié avec le code reçu.
}

/** Messages de la vérification du code (types des ProblemDetails du contrat identity 2.1.0). */
const MESSAGES_VERIFICATION: Record<string, string> = {
  'invalid-code': 'Ce code n’est pas le bon. Vérifie-le, ou demande un nouveau code.',
  'code-expired': 'Ce code a expiré. Demande un nouveau code.',
  'too-many-attempts': 'Trop d’essais pour ce code. Demande un nouveau code.',
}

/** SCRUM-135 — vérifie l'e-mail avec le code à 6 chiffres reçu (204 si c'est bon). */
export async function verifierEmail(email: string, code: string): Promise<void> {
  const resultat = await api.identity.POST('/api/v1/auth/email/verify', { body: { email, code } }).catch(() => null)
  if (!resultat) throw new ErreurAuth(MESSAGE_INDISPONIBLE)
  if (resultat.response.ok) return
  const type = ((resultat.error ?? {}) as { type?: string }).type?.split('/').pop() ?? ''
  throw new ErreurAuth(
    MESSAGES_VERIFICATION[type] ??
      (resultat.response.status >= 500 ? MESSAGE_INDISPONIBLE : MESSAGES_VERIFICATION['invalid-code']),
  )
}

/** SCRUM-135 — demande un nouveau code. Le serveur répond toujours 202 : on ne sait pas (et on ne dit pas) si
 * un e-mail est vraiment parti. */
export async function renvoyerCode(email: string): Promise<void> {
  const resultat = await api.identity.POST('/api/v1/auth/email/resend', { body: { email } }).catch(() => null)
  if (!resultat || resultat.response.status >= 500) throw new ErreurAuth(MESSAGE_INDISPONIBLE)
}
