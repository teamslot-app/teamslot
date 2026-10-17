import { useState, type FormEvent } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { ErreurAuth } from '../auth/actions'
import { useAuth } from '../auth/useAuth'

export default function Connexion() {
  const { estConnecte, seConnecter } = useAuth()
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
      await seConnecter(phone.trim(), password)
    } catch (err) {
      setErreur(err instanceof ErreurAuth ? err.message : 'Une erreur est survenue.')
      setPassword('')
    } finally {
      setEnCours(false)
    }
  }

  return (
    <main>
      <h1>Connexion</h1>
      <form onSubmit={soumettre}>
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
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        {erreur && <p role="alert">{erreur}</p>}
        <button type="submit" disabled={enCours}>
          {enCours ? 'Connexion…' : 'Se connecter'}
        </button>
      </form>
      <p>
        Pas encore de compte ? <Link to="/inscription">Créer un compte</Link>
      </p>
    </main>
  )
}
