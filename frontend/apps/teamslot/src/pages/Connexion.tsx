import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ErreurAuth, ErreurEmailNonVerifie } from '../auth/actions'
import { useAuth } from '../auth/useAuth'
import { versInternational } from '../telephone'
import Alerte from '../ui/Alerte'
import Champ from '../ui/Champ'
import Info from '../ui/Info'
import EcranAuth from './EcranAuth'

/** Logique identique à apps/web (déjà relue côté sécurité) : seul l'habillage change. */
export default function Connexion() {
  const { estConnecte, seConnecter } = useAuth()
  const navigate = useNavigate()
  const info = (useLocation().state as { info?: string } | null)?.info
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [erreur, setErreur] = useState<string | null>(null)
  const [enCours, setEnCours] = useState(false)

  if (estConnecte) return <Navigate to="/terrains" replace />

  async function soumettre(evenement: FormEvent<HTMLFormElement>) {
    evenement.preventDefault()
    if (enCours) return
    setErreur(null)
    setEnCours(true)
    try {
      await seConnecter(versInternational(phone), password)
    } catch (err) {
      if (err instanceof ErreurEmailNonVerifie) {
        navigate('/verification', { state: { depuisConnexion: true } })
        return
      }
      setErreur(err instanceof ErreurAuth ? err.message : 'Une erreur est survenue.')
      setPassword('')
    } finally {
      setEnCours(false)
    }
  }

  return (
    <EcranAuth
      titre="Connexion"
      sousTitre="Content de te revoir sur le terrain."
      bas={
        <>
          Pas encore de compte ?{' '}
          <Link to="/inscription" className="lien-brassard">
            Créer un compte
          </Link>
        </>
      }
    >
      <form onSubmit={soumettre} className="flex flex-col gap-5" noValidate={false}>
        <Champ
          libelle="Téléphone"
          type="tel"
          name="phone"
          autoComplete="username"
          inputMode="tel"
          placeholder="06 12 34 56 78"
          required
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
        />
        <Champ
          libelle="Mot de passe"
          type="password"
          name="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        {info && !erreur && <Info>{info}</Info>}
        {erreur && <Alerte>{erreur}</Alerte>}
        <button type="submit" disabled={enCours} className="brassard mt-2 self-start disabled:opacity-60">
          {enCours ? 'Connexion…' : 'Se connecter'}
        </button>
      </form>
    </EcranAuth>
  )
}
