import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../api'

const lireProfil = () => api.identity.GET('/api/v1/users/me')
type ProfilUtilisateur = NonNullable<Awaited<ReturnType<typeof lireProfil>>['data']>

const SURNOM_MAX = 40

/**
 * SCRUM-95 : mon profil. Aucun identifiant envoyé : le serveur lit le compte dans le jeton.
 * Seul le surnom se modifie ici ; le serveur revérifie tout (1 à 40 caractères, tout autre champ refusé).
 */
export default function Profil() {
  // undefined : chargement ; null : erreur
  const [profil, setProfil] = useState<ProfilUtilisateur | null | undefined>(undefined)
  const [surnom, setSurnom] = useState('')
  const [etat, setEtat] = useState<'repos' | 'envoi' | 'enregistre' | 'erreur'>('repos')

  useEffect(() => {
    let annule = false // la page quittée avant la réponse ne doit plus rien afficher
    lireProfil()
      .then(({ data }) => {
        if (annule) return
        setProfil(data ?? null)
        setSurnom(data?.displayName ?? '')
      })
      .catch(() => {
        if (!annule) setProfil(null)
      })
    return () => {
      annule = true
    }
  }, [])

  const propre = surnom.trim()
  const valide = propre.length >= 1 && propre.length <= SURNOM_MAX

  async function enregistrer(e: FormEvent) {
    e.preventDefault()
    if (!valide) return
    setEtat('envoi')
    try {
      // Seul displayName est envoyé : jamais le profil entier (le serveur refuserait tout autre champ)
      const { data } = await api.identity.PATCH('/api/v1/users/me', { body: { displayName: propre } })
      if (!data) {
        setEtat('erreur')
        return
      }
      setProfil(data)
      setSurnom(data.displayName ?? propre)
      setEtat('enregistre')
    } catch {
      setEtat('erreur')
    }
  }

  return (
    <main>
      <h1>Mon profil</h1>
      {profil === undefined && <p>Chargement…</p>}
      {profil === null && <p role="alert">Impossible de charger ton profil. Réessaie dans un instant.</p>}
      {profil && (
        <>
          <p>Téléphone : {profil.phone}</p>
          <form onSubmit={enregistrer}>
            <label htmlFor="surnom">Surnom</label>{' '}
            <input
              id="surnom"
              name="displayName"
              autoComplete="nickname"
              required
              maxLength={SURNOM_MAX}
              value={surnom}
              onChange={(e) => {
                setSurnom(e.target.value)
                setEtat('repos')
              }}
            />{' '}
            <button type="submit" disabled={!valide || etat === 'envoi'}>
              Enregistrer
            </button>
          </form>
          {etat === 'enregistre' && <p role="status">Surnom enregistré.</p>}
          {etat === 'erreur' && (
            <p role="alert">Surnom non enregistré : 1 à 40 caractères, ou serveur injoignable. Réessaie.</p>
          )}
        </>
      )}
    </main>
  )
}
