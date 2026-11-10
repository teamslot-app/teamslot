import { useEffect, useState } from 'react'
import type { MatchComponents } from '@teamslot/shared'
import { api } from '../api'
import { dateDuJour, formatHeure } from '../format'
import { terrainParId } from '../venues'

type MatchOuvert = MatchComponents['schemas']['OpenMatch']
type Resultat = { date: string; matchs: MatchOuvert[] | null }

/**
 * SCRUM-118 (provisoire) : matchs d'un jour qui cherchent des joueurs. Remplacé par la carte quand
 * search-service existera (ADR-012). Le serveur ne renvoie aucun participant.
 */
export default function MatchsOuverts() {
  const [aujourdhui] = useState(dateDuJour)
  const [date, setDate] = useState(aujourdhui)
  const [resultat, setResultat] = useState<Resultat | null>(null)

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
          {matchs.map((m) => (
            <li key={m.id}>
              {formatHeure(m.startsAt)} · {terrainParId(m.venueId)?.name ?? 'Terrain'} ·{' '}
              {m.openSpots === 1 ? '1 place' : `${m.openSpots} places`}
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
