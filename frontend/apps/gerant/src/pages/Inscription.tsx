import { useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ErreurAuth } from '../auth/actions'
import { useAuth } from '../auth/useAuth'
import { motDePasseValide, reglesMotDePasse } from '../motDePasse'
import { composerNumero, numeroValide, PAYS_PAR_DEFAUT, type Pays } from '../pays'
import Alerte from '../ui/Alerte'
import Champ from '../ui/Champ'
import ChampTelephone from '../ui/ChampTelephone'
import CriteresMotDePasse from '../ui/CriteresMotDePasse'
import EcranAuth from './EcranAuth'

type Erreurs = Partial<Record<'email' | 'telephone' | 'naissance' | 'password' | 'confirmation', string>>

const aujourdhui = () => new Date().toISOString().slice(0, 10)

/**
 * SCRUM-140 — inscription propriétaire : le même formulaire v2 que la plateforme joueur (SCRUM-135). Elle crée un
 * compte simple ; le propriétaire inscrit ensuite son terrain depuis son espace (le dossier, c'est la demande).
 */
export default function Inscription() {
  const { estConnecte, sInscrire } = useAuth()
  const navigate = useNavigate()
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [pays, setPays] = useState<Pays>(PAYS_PAR_DEFAUT)
  const [numero, setNumero] = useState('')
  const [naissance, setNaissance] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [erreurs, setErreurs] = useState<Erreurs>({})
  const [erreur, setErreur] = useState<string | null>(null)
  const [enCours, setEnCours] = useState(false)
  const formulaire = useRef<HTMLFormElement>(null)

  const telephone = composerNumero(pays, numero)
  const contexte = { nom: displayName, email, telephone }
  const regles = useMemo(
    () => reglesMotDePasse(password, { nom: displayName, email, telephone }),
    [password, displayName, email, telephone],
  )

  if (estConnecte) return <Navigate to="/" replace />

  function verifier(): Erreurs {
    const e: Erreurs = {}
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()))
      e.email = 'Entre une adresse e-mail valide, par exemple nom@gmail.com.'
    if (!numeroValide(pays, telephone)) e.telephone = `Ce numéro n'est pas valide pour ${pays.nom}.`
    if (!naissance || naissance < '1900-01-01' || naissance >= aujourdhui()) e.naissance = 'Entre ta date de naissance.'
    if (!motDePasseValide(password, contexte)) e.password = 'Le mot de passe ne respecte pas encore toutes les règles.'
    if (confirmation !== password) e.confirmation = 'Les deux mots de passe ne sont pas identiques.'
    return e
  }

  async function soumettre(evenement: FormEvent<HTMLFormElement>) {
    evenement.preventDefault()
    if (enCours) return
    setErreur(null)
    const trouvees = verifier()
    setErreurs(trouvees)
    if (Object.keys(trouvees).length > 0) {
      // on place le curseur sur le premier champ à corriger
      requestAnimationFrame(() => formulaire.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus())
      return
    }
    setEnCours(true)
    try {
      await sInscrire({
        displayName: displayName.trim(),
        email: email.trim(),
        phone: telephone,
        phoneCountry: pays.code,
        birthDate: naissance,
        password,
      })
      // SCRUM-135 : le compte est créé, l'e-mail doit être vérifié avant la première connexion
      navigate('/verification', { state: { email: email.trim() } })
    } catch (err) {
      setErreur(err instanceof ErreurAuth ? err.message : 'Une erreur est survenue.')
    } finally {
      setEnCours(false)
    }
  }

  return (
    <EcranAuth
      titre="Créer mon compte propriétaire"
      sousTitre="D'abord ton compte, ensuite ton terrain : tu l'inscriras depuis ton espace."
      bas={
        <>
          Déjà un compte ?{' '}
          <Link to="/connexion" className="lien-brassard">
            Se connecter
          </Link>
        </>
      }
    >
      <form ref={formulaire} onSubmit={soumettre} noValidate className="flex flex-col gap-5">
        <Champ
          libelle="Nom affiché"
          name="displayName"
          autoComplete="nickname"
          maxLength={40}
          aide="Ton nom, tel que les joueurs le verront."
          required
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
        />
        <Champ
          libelle="Adresse e-mail"
          type="email"
          name="email"
          autoComplete="email"
          inputMode="email"
          placeholder="nom@gmail.com"
          aide="On t'enverra un code à 6 chiffres pour la vérifier."
          required
          erreur={erreurs.email}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <ChampTelephone pays={pays} onPays={setPays} numero={numero} onNumero={setNumero} erreur={erreurs.telephone} />
        <Champ
          libelle="Date de naissance"
          type="date"
          name="birthDate"
          autoComplete="bday"
          min="1900-01-01"
          max={aujourdhui()}
          required
          erreur={erreurs.naissance}
          value={naissance}
          onChange={(e) => setNaissance(e.target.value)}
        />
        <Champ
          libelle="Mot de passe"
          type="password"
          name="password"
          autoComplete="new-password"
          maxLength={128}
          aide={<CriteresMotDePasse regles={regles} />}
          required
          erreur={erreurs.password}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Champ
          libelle="Confirme le mot de passe"
          type="password"
          name="confirmation"
          autoComplete="new-password"
          required
          erreur={erreurs.confirmation}
          value={confirmation}
          onChange={(e) => setConfirmation(e.target.value)}
        />
        {erreur && <Alerte>{erreur}</Alerte>}
        <button type="submit" disabled={enCours} className="brassard mt-2 self-start disabled:opacity-60">
          {enCours ? 'Création…' : 'Créer mon compte'}
        </button>
      </form>
    </EcranAuth>
  )
}
