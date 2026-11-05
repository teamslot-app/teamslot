import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import type { VenueComponents } from '@teamslot/shared'
import { api } from '../api'
import { formatHeure, formatMontant } from '../format'
import { terrainParId } from '../venues'

type Money = VenueComponents['schemas']['Money']
// statut 0 : le serveur n'a pas répondu
type Prix = { cle: string; statut: number; total: Money | null; supplement: Money | null }
type Etat = { type: 'repos' } | { type: 'envoi' } | { type: 'ok' } | { type: 'erreur'; message: string }

const INDISPONIBLE = 'Service momentanément indisponible. Réessaie dans un instant.'

function messageReservation(statut: number, erreur: unknown): string {
  if (statut === 409) {
    const type = (erreur as { type?: unknown } | null | undefined)?.type
    return typeof type === 'string' && type.endsWith('/slot-already-started')
      ? 'Ce créneau a déjà commencé.'
      : "Ce créneau vient d'être pris."
  }
  if (statut === 404) return "Ce créneau n'existe plus."
  if (statut === 400) return 'Réservation invalide : choisis un autre créneau.'
  return INDISPONIBLE
}

export default function Reservation() {
  const { venueId, slotId } = useParams()
  const terrain = terrainParId(venueId)
  const { state } = useLocation()
  const navigate = useNavigate()
  const horaire = state as { start?: string; end?: string } | null
  const [choix, setChoix] = useState<string[]>([])
  const [prix, setPrix] = useState<Prix | null>(null)
  const [etat, setEtat] = useState<Etat>({ type: 'repos' })
  const cle = `${slotId}|${[...choix].sort().join(',')}`

  useEffect(() => {
    if (!terrain || !slotId) return
    let annule = false // une réponse en retard ne doit pas écraser le prix du dernier choix
    api.venue
      .GET('/api/v1/venues/{id}/price', {
        params: { path: { id: terrain.id }, query: { slot: slotId, equipment: choix } },
      })
      .then(({ data, response }) => {
        if (annule) return
        setPrix({
          cle,
          statut: response.status,
          total: data?.total ?? null,
          supplement: data?.equipmentTotal ?? null,
        })
      })
      .catch(() => {
        if (!annule) setPrix({ cle, statut: 0, total: null, supplement: null })
      })
    return () => {
      annule = true
    }
  }, [terrain, slotId, choix, cle])

  if (!terrain || !slotId) return <Navigate to="/" replace />

  const courant = prix && prix.cle === cle ? prix : null
  const verrouille = etat.type === 'envoi' || etat.type === 'ok'

  function basculer(id: string) {
    setChoix((avant) => (avant.includes(id) ? avant.filter((x) => x !== id) : [...avant, id]))
  }

  async function reserver() {
    if (!terrain || !slotId || verrouille) return
    setEtat({ type: 'envoi' })
    const equipmentIds = terrain.equipment.filter((e) => choix.includes(e.id)).map((e) => e.id)
    try {
      // Aucun montant n'est envoyé : le serveur recalcule le prix lui-même.
      const { data, error, response } = await api.match.POST('/api/v1/matches', {
        body: { slotId, equipmentIds: equipmentIds.length > 0 ? equipmentIds : undefined },
      })
      if (data) {
        setEtat({ type: 'ok' })
        navigate(`/matchs/${data.id}`, {
          state: { match: data, venueId: terrain.id, start: horaire?.start, end: horaire?.end },
        })
      } else {
        setEtat({ type: 'erreur', message: messageReservation(response.status, error) })
      }
    } catch {
      setEtat({ type: 'erreur', message: INDISPONIBLE })
    }
  }

  return (
    <main>
      <p>
        <Link to={`/terrains/${terrain.id}`}>← Créneaux</Link>
      </p>
      <h1>Réserver</h1>
      <p>
        {terrain.name}
        {horaire?.start && horaire.end && (
          <>
            {' '}
            · {formatHeure(horaire.start)} – {formatHeure(horaire.end)}
          </>
        )}
      </p>
      {terrain.equipment.length > 0 && (
        <fieldset disabled={verrouille}>
          <legend>Équipements</legend>
          {terrain.equipment.map((equipement) => (
            <label key={equipement.id}>
              <input
                type="checkbox"
                checked={choix.includes(equipement.id)}
                onChange={() => basculer(equipement.id)}
              />
              {equipement.name}
            </label>
          ))}
        </fieldset>
      )}
      {courant === null && <p>Calcul du prix…</p>}
      {courant?.statut === 404 && <p role="alert">Ce créneau n'existe plus.</p>}
      {courant && courant.statut !== 404 && courant.total === null && (
        <p role="alert">Impossible de calculer le prix. Réessaie dans un instant.</p>
      )}
      {courant?.total && (
        <>
          <p>
            Prix : <strong>{formatMontant(courant.total)}</strong>
          </p>
          {courant.supplement && courant.supplement.amount > 0 && (
            <p>dont équipements : {formatMontant(courant.supplement)}</p>
          )}
        </>
      )}
      {etat.type === 'erreur' && <p role="alert">{etat.message}</p>}
      {etat.type === 'ok' ? (
        <p role="status">Créneau réservé : match créé, en attente de paiement.</p>
      ) : (
        <button type="button" disabled={verrouille || !courant?.total} onClick={reserver}>
          {etat.type === 'envoi' ? 'Réservation…' : 'Réserver ce créneau'}
        </button>
      )}
    </main>
  )
}
