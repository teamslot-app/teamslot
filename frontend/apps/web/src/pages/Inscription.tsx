import { useState, type FormEvent } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { ErreurAuth } from '../auth/actions'
import { useAuth } from '../auth/useAuth'

export default function Inscription() {
  const { estConnecte, sInscrire } = useAuth()
  const [displayName, setDisplayName] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [erreur, setErreur] = useState<string | null>(null)
  const [enCours, setEnCours] = useState(false)

  if (estConnecte) return <Navigate to="/" replace />

  async function soumettre(evenement: FormEvent<HTMLFormElement>) {
    evenement.preventDefault()
    if (enCours) return
    setErreur(null)
    setEnCours(true)
    try {
      await sInscrire(phone.trim(), password, displayName.trim())
    } catch (err) {
      setErreur(err instanceof ErreurAuth ? err.message : 'Une erreur est survenue.')
    } finally {
      setEnCours(false)
    }
  }

  return (
    <main>
      <h1>Créer un compte</h1>
      <form onSubmit={soumettre}>
        <label>
          Nom affiché
          <input
            type="text"
            name="displayName"
            autoComplete="nickname"
            required
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
          />
        </label>
        <label>
          Téléphone
          <input
            type="tel"
            name="phone"
            autoComplete="username"
            placeholder="+212600000000"
            required
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
          />
        </label>
        <label>
          Mot de passe
          <input
            type="password"
            name="password"
            autoComplete="new-password"
            minLength={10}
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        {erreur && <p role="alert">{erreur}</p>}
        <button type="submit" disabled={enCours}>
          {enCours ? 'Création…' : 'Créer mon compte'}
        </button>
      </form>
      <p>
        Déjà un compte ? <Link to="/connexion">Se connecter</Link>
      </p>
    </main>
  )
}
