import { useEffect, useState } from 'react'
import type { MatchComponents } from '@teamslot/shared'
import { api } from '../api'
import { dateDuJour, formatHeure } from '../format'
import { terrainParId } from '../venues'

type MatchOuvert = MatchComponents['schemas']['OpenMatch']
type Resultat = { date: string; matchs: MatchOuvert[] | null }
// SCRUM-79 : état de la demande pour chaque match de la liste
type Demande = { type: 'envoi' } | { type: 'ok' } | { type: 'erreur'; message: string }

const INDISPONIBLE = 'Service momentanément indisponible. Réessaie dans un instant.'

/** Les messages d'erreur du serveur sont en français et n'écrivent jamais la valeur envoyée. */
function messageServeur(statut: number, erreur: unknown): string {
  const detail = (erreur as { detail?: unknown } | null | undefined)?.detail
  if (statut < 500 && typeof detail === 'string' && detail.trim() !== '') return detail
  return statut >= 500 ? INDISPONIBLE : 'Demande impossible.'
}

/**
 * SCRUM-118 (provisoire) : matchs d'un jour qui cherchent des joueurs. Remplacé par la carte quand
 * search-service existera (ADR-012). Le serveur ne renvoie aucun participant.
 */
export default function MatchsOuverts() {
  const [aujourdhui] = useState(dateDuJour)
  const [date, setDate] = useState(aujourdhui)
  const [resultat, setResultat] = useState<Resultat | null>(null)
  const [demandes, setDemandes] = useState<Record<string, Demande>>({})

  useEffect(() => {
    if (!date) return
    let annule = false // une réponse en retard ne doit pas écraser celle de la date choisie ensuite
    api.match
      .GET('/api/v1/matches/open', { params: { query: { date } } })
      .then(({ data }) => {
        if (!annule) setResultat({ date, matchs: data ?? null })
      })
      .catch(() => {
        if (!annule) setResultat({ date, matchs: null })
      })
    return () => {
      annule = true
    }
  }, [date])

  async function demander(matchId: string) {
    if (demandes[matchId]?.type === 'envoi') return
    const fixer = (d: Demande) => setDemandes((avant) => ({ ...avant, [matchId]: d }))
    fixer({ type: 'envoi' })
    try {
      // Aucun corps : le serveur lit l'identité du joueur dans le jeton.
      const { error, response } = await api.match.POST('/api/v1/matches/{id}/join', {
        params: { path: { id: matchId } },
      })
      fixer(response.ok ? { type: 'ok' } : { type: 'erreur', message: messageServeur(response.status, error) })
    } catch {
      fixer({ type: 'erreur', message: INDISPONIBLE })
    }
  }

  const courant = resultat && resultat.date === date ? resultat : null
  const matchs = courant?.matchs ?? null

  return (
    <main>
      <h1>Matchs ouverts</h1>
      <p>Des groupes cherchent des joueurs pour compléter leur match.</p>
      <label>
        Date
        <input type="date" value={date} min={aujourdhui} onChange={(e) => setDate(e.target.value)} />
      </label>
      {!date && <p>Choisis une date.</p>}
      {date && courant === null && <p>Chargement…</p>}
      {courant && matchs === null && <p role="alert">Impossible de charger les matchs. Réessaie dans un instant.</p>}
      {matchs?.length === 0 && <p>Aucun match ne cherche de joueurs ce jour-là.</p>}
      {matchs && matchs.length > 0 && (
        <ul>
          {matchs.map((m) => {
            const demande = demandes[m.id]
            return (
              <li key={m.id}>
                {formatHeure(m.startsAt)} · {terrainParId(m.venueId)?.name ?? 'Terrain'} ·{' '}
                {m.openSpots === 1 ? '1 place' : `${m.openSpots} places`}{' '}
                {demande?.type === 'ok' ? (
                  <span role="status">Demande envoyée : l’organisateur doit accepter.</span>
                ) : (
                  <button type="button" disabled={demande?.type === 'envoi'} onClick={() => demander(m.id)}>
                    {demande?.type === 'envoi' ? 'Envoi…' : 'Demander à rejoindre'}
                  </button>
                )}
                {demande?.type === 'erreur' && <span role="alert"> {demande.message}</span>}
              </li>
            )
          })}
        </ul>
      )}
    </main>
  )
}
