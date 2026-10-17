import { useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useParams } from 'react-router-dom'
import type { MatchComponents, PaymentComponents } from '@teamslot/shared'
import { api } from '../api'
import { formatHeure, formatMontant } from '../format'
import { terrainParId } from '../venues'

type Match = MatchComponents['schemas']['Match']
type Participant = MatchComponents['schemas']['Participant']
type Payment = PaymentComponents['schemas']['Payment']
// Transmis par la page de réservation (navigation) : il n'existe pas de GET /matches/{id}.
type Contexte = { match?: Match; venueId?: string; start?: string; end?: string }
// statut 0 : le serveur n'a pas répondu
type Chargement<T> = { statut: number; donnees: T | null }
type Action = { type: 'repos' } | { type: 'envoi' } | { type: 'ok' } | { type: 'erreur'; message: string }

const INDISPONIBLE = 'Service momentanément indisponible. Réessaie dans un instant.'
const LIBELLES: Record<Payment['status'], string> = {
  PENDING: 'en attente',
  COMPLETED: 'payé en ligne',
  ON_SITE_ACCEPTED: 'à régler sur place',
  COLLECTED: 'encaissé sur place',
  FAILED: 'échoué',
  REFUNDED: 'remboursé',
}
// Un paiement accepté confirme le match (payment.completed ou payment.on_site_accepted, puis match.confirmed).
const PAYE: string[] = ['COMPLETED', 'ON_SITE_ACCEPTED', 'COLLECTED']

/** Les messages d'erreur du serveur sont en français et n'écrivent jamais la valeur envoyée. */
function messageServeur(statut: number, erreur: unknown): string {
  const detail = (erreur as { detail?: unknown } | null | undefined)?.detail
  if (statut < 500 && typeof detail === 'string' && detail.trim() !== '') return detail
  return statut === 0 || statut >= 500 ? INDISPONIBLE : 'Action impossible.'
}

export default function PageMatch() {
  const { matchId } = useParams()
  const contexte = useLocation().state as Contexte | null
  const match = contexte?.match
  const terrain = terrainParId(contexte?.venueId ?? match?.venueId)
  const [rechargement, setRechargement] = useState(0)
  const [participants, setParticipants] = useState<Chargement<Participant[]> | null>(null)
  const [paiement, setPaiement] = useState<Chargement<Payment> | null>(null)
  const [surnom, setSurnom] = useState('')
  const [ajout, setAjout] = useState<Action>({ type: 'repos' })
  const [reglement, setReglement] = useState<Action>({ type: 'repos' })
  const [annulation, setAnnulation] = useState<'repos' | 'confirmer' | 'envoi' | 'fait'>('repos')
  const [erreurAnnulation, setErreurAnnulation] = useState<string | null>(null)

  useEffect(() => {
    if (!matchId) return
    let annule = false // une réponse en retard ne doit pas écraser la plus récente
    api.match
      .GET('/api/v1/matches/{id}/participants', { params: { path: { id: matchId } } })
      .then(({ data, response }) => {
        if (!annule) setParticipants({ statut: response.status, donnees: data ?? null })
      })
      .catch(() => {
        if (!annule) setParticipants({ statut: 0, donnees: null })
      })
    return () => {
      annule = true
    }
  }, [matchId, rechargement])

  useEffect(() => {
    if (!matchId) return
    let annule = false
    api.payment
      .GET('/api/v1/payments/{matchId}', { params: { path: { matchId } } })
      .then(({ data, response }) => {
        if (!annule) setPaiement({ statut: response.status, donnees: data ?? null })
      })
      .catch(() => {
        if (!annule) setPaiement({ statut: 0, donnees: null })
      })
    return () => {
      annule = true
    }
  }, [matchId, rechargement])

  if (!matchId || !match || match.id !== matchId) return <Navigate to="/" replace />

  const statutPaiement = paiement?.donnees?.status
  const confirme = statutPaiement !== undefined && PAYE.includes(statutPaiement)
  const annule = annulation === 'fait'
  const statutAffiche = annule ? 'Match annulé' : confirme ? 'Match confirmé' : 'En attente de paiement'

  async function ajouter(evenement: FormEvent<HTMLFormElement>) {
    evenement.preventDefault()
    const nom = surnom.trim()
    if (!matchId || nom === '' || ajout.type === 'envoi') return
    setAjout({ type: 'envoi' })
    try {
      const { error, response } = await api.match.POST('/api/v1/matches/{id}/participants', {
        params: { path: { id: matchId } },
        body: { guestName: nom }, // ni téléphone ni e-mail pour un invité
      })
      if (response.ok) {
        setSurnom('')
        setAjout({ type: 'ok' })
        setRechargement((n) => n + 1)
      } else {
        setAjout({ type: 'erreur', message: messageServeur(response.status, error) })
      }
    } catch {
      setAjout({ type: 'erreur', message: INDISPONIBLE })
    }
  }

  async function regler(mode: 'online' | 'on-site') {
    if (!matchId || reglement.type === 'envoi') return
    setReglement({ type: 'envoi' })
    try {
      // Aucun corps, donc aucun montant : le serveur connaît le prix.
      const { data, error, response } =
        mode === 'online'
          ? await api.payment.POST('/api/v1/payments/{matchId}/online', { params: { path: { matchId } } })
          : await api.payment.POST('/api/v1/payments/{matchId}/on-site', { params: { path: { matchId } } })
      if (data) {
        setPaiement({ statut: response.status, donnees: data })
        setReglement({ type: 'ok' })
      } else {
        setReglement({ type: 'erreur', message: messageServeur(response.status, error) })
      }
    } catch {
      setReglement({ type: 'erreur', message: INDISPONIBLE })
    }
  }

  async function annuler() {
    if (!matchId || annulation === 'envoi') return
    setAnnulation('envoi')
    setErreurAnnulation(null)
    try {
      const { error, response } = await api.match.POST('/api/v1/matches/{id}/cancel', {
        params: { path: { id: matchId } },
      })
      if (response.ok) {
        setAnnulation('fait')
      } else {
        setAnnulation('repos')
        setErreurAnnulation(messageServeur(response.status, error))
      }
    } catch {
      setAnnulation('repos')
      setErreurAnnulation(INDISPONIBLE)
    }
  }

  return (
    <main>
      <p>
        <Link to="/">← Terrains</Link>
      </p>
      <h1>Ton match</h1>
      <p>
        {terrain?.name ?? 'Terrain'}
        {contexte?.start && contexte.end && (
          <>
            {' '}
            · {formatHeure(contexte.start)} – {formatHeure(contexte.end)}
          </>
        )}
      </p>
      {match.totalPrice && (
        <p>
          Prix : <strong>{formatMontant(match.totalPrice)}</strong>
        </p>
      )}
      <p>
        Statut : <strong>{statutAffiche}</strong>
      </p>

      <h2>Mes amis</h2>
      {participants === null && <p>Chargement…</p>}
      {participants && participants.donnees === null && (
        <p role="alert">Impossible de charger les participants.</p>
      )}
      {participants?.donnees?.length === 0 && <p>Personne d’autre pour l’instant.</p>}
      {participants?.donnees && participants.donnees.length > 0 && (
        <ul>
          {participants.donnees.map((p) => (
            <li key={p.id}>
              {p.guestName ?? 'Joueur avec compte'}
              {p.side && <> · côté {p.side}</>}
            </li>
          ))}
        </ul>
      )}
      {!annule && (
        <form onSubmit={ajouter}>
          <label>
            Surnom d’un ami
            <input
              type="text"
              name="guestName"
              maxLength={40}
              required
              value={surnom}
              onChange={(e) => setSurnom(e.target.value)}
            />
          </label>
          <button type="submit" disabled={ajout.type === 'envoi'}>
            {ajout.type === 'envoi' ? 'Ajout…' : 'Ajouter'}
          </button>
          {ajout.type === 'erreur' && <p role="alert">{ajout.message}</p>}
          {ajout.type === 'ok' && <p role="status">Ami ajouté.</p>}
        </form>
      )}

      <h2>Paiement</h2>
      {paiement?.donnees && <p>Paiement : {LIBELLES[paiement.donnees.status]}</p>}
      {paiement?.statut === 404 && (
        <p>
          Le paiement se prépare…{' '}
          <button type="button" onClick={() => setRechargement((n) => n + 1)}>
            Actualiser
          </button>
        </p>
      )}
      {paiement && paiement.donnees === null && paiement.statut !== 404 && (
        <p role="alert">Impossible de lire le paiement.</p>
      )}
      {!annule && !confirme && (
        <div>
          <button type="button" disabled={reglement.type === 'envoi'} onClick={() => regler('online')}>
            Payer en ligne
          </button>
          {terrain?.acceptsOnSitePayment && (
            <button type="button" disabled={reglement.type === 'envoi'} onClick={() => regler('on-site')}>
              Payer sur place
            </button>
          )}
        </div>
      )}
      {reglement.type === 'erreur' && <p role="alert">{reglement.message}</p>}

      {annule ? (
        <p role="status">Match annulé. Le créneau sera libéré ; un paiement en ligne déjà effectué sera remboursé.</p>
      ) : annulation === 'confirmer' ? (
        <p>
          Annuler ce match ? <button type="button" onClick={annuler}>Oui, annuler</button>{' '}
          <button type="button" onClick={() => setAnnulation('repos')}>Non, le garder</button>
        </p>
      ) : (
        <button type="button" disabled={annulation === 'envoi'} onClick={() => setAnnulation('confirmer')}>
          Annuler le match
        </button>
      )}
      {erreurAnnulation && <p role="alert">{erreurAnnulation}</p>}
    </main>
  )
}
