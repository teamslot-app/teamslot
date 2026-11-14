import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ErreurAuth, renvoyerCode, verifierEmail } from '../auth/actions'
import Alerte from '../ui/Alerte'
import Champ from '../ui/Champ'
import Info from '../ui/Info'
import EcranAuth from './EcranAuth'

/** Délai entre deux renvois, comme la limite du serveur (1 par minute). */
const ATTENTE_RENVOI_S = 60

type Etat = { email?: string; depuisConnexion?: boolean } | null

/**
 * SCRUM-135 — saisie du code à 6 chiffres reçu par e-mail (SCRUM-133). On y arrive après l'inscription (e-mail
 * déjà connu) ou après une connexion refusée (403 email-not-verified : on demande l'e-mail). L'e-mail passe par
 * l'état de navigation, jamais par l'adresse de la page (il ne doit pas finir dans l'historique ou les journaux).
 */
export default function VerificationEmail() {
  const navigate = useNavigate()
  const etat = useLocation().state as Etat
  const [email, setEmail] = useState(etat?.email ?? '')
  const [code, setCode] = useState('')
  const [erreur, setErreur] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(
    etat?.email ? `On t’a envoyé un code à 6 chiffres à ${etat.email}.` : null,
  )
  const [enCours, setEnCours] = useState(false)
  const [attente, setAttente] = useState(etat?.email ? ATTENTE_RENVOI_S : 0)

  useEffect(() => {
    if (attente <= 0) return
    const minuterie = setTimeout(() => setAttente((s) => s - 1), 1000)
    return () => clearTimeout(minuterie)
  }, [attente])

  async function soumettre(evenement: FormEvent<HTMLFormElement>) {
    evenement.preventDefault()
    if (enCours) return
    setErreur(null)
    if (!/^\d{6}$/.test(code)) {
      setErreur('Le code fait 6 chiffres.')
      return
    }
    setEnCours(true)
    try {
      await verifierEmail(email.trim(), code)
      navigate('/connexion', { replace: true, state: { info: 'Ton e-mail est vérifié. Tu peux te connecter.' } })
    } catch (err) {
      setErreur(err instanceof ErreurAuth ? err.message : 'Une erreur est survenue.')
      setCode('')
    } finally {
      setEnCours(false)
    }
  }

  async function renvoyer() {
    setErreur(null)
    setInfo(null)
    if (!email.trim()) {
      setErreur('Indique ton adresse e-mail pour recevoir un nouveau code.')
      return
    }
    try {
      await renvoyerCode(email.trim())
      setInfo('Si cette adresse attend une vérification, un nouveau code vient de partir. Pense aux spams.')
      setAttente(ATTENTE_RENVOI_S)
    } catch (err) {
      setErreur(err instanceof ErreurAuth ? err.message : 'Une erreur est survenue.')
    }
  }

  return (
    <EcranAuth
      titre="Vérifie ton e-mail"
      sousTitre={
        etat?.depuisConnexion
          ? 'Ton compte attend encore la vérification de ton e-mail.'
          : 'Dernière étape avant d’inscrire ton terrain.'
      }
      bas={
        <Link to="/connexion" className="lien-brassard">
          Retour à la connexion
        </Link>
      }
    >
      <form onSubmit={soumettre} noValidate className="flex flex-col gap-5">
        {!etat?.email && (
          <Champ
            libelle="Adresse e-mail"
            type="email"
            name="email"
            autoComplete="email"
            inputMode="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        )}
        <Champ
          libelle="Code reçu par e-mail"
          name="code"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          aide="6 chiffres, valable 15 minutes."
          required
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
        />
        {erreur && <Alerte>{erreur}</Alerte>}
        {info && <Info>{info}</Info>}
        <button type="submit" disabled={enCours} className="brassard mt-2 self-start disabled:opacity-60">
          {enCours ? 'Vérification…' : 'Vérifier'}
        </button>
        <button
          type="button"
          onClick={renvoyer}
          disabled={attente > 0}
          className="self-start text-sm font-semibold text-sauge underline-offset-4 hover:text-creme hover:underline disabled:no-underline disabled:opacity-60"
        >
          {attente > 0 ? `Renvoyer le code (dans ${attente} s)` : 'Renvoyer le code'}
        </button>
      </form>
    </EcranAuth>
  )
}
