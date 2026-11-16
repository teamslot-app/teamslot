import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import type { MatchComponents } from '@teamslot/shared'
import { api } from '../api'
import { formatHeure, formatJour } from '../format'
import { terrainParId } from '../venues'

type Match = MatchComponents['schemas']['Match']

/** Statut écrit en toutes lettres : il ne repose jamais sur la couleur seule. */
const STATUTS: Record<Match['status'], string> = {
  PENDING_PAYMENT: 'En attente de paiement',
  CONFIRMED: 'Confirmé',
  IN_PROGRESS: 'En cours',
  PLAYED: 'Joué',
  CLOSED: 'Terminé',
  CANCELLED: 'Annulé',
  NO_SHOW: 'Absence',
  DISPUTED: 'Contesté',
}

/**
 * SCRUM-77 : mes matchs à venir, comme organisateur ou participant avec compte ; les matchs annulés restent
 * affichés. Aucun identifiant envoyé : le serveur lit l'identité dans le jeton. Jour et heure à Marrakech.
 */
export default function MesMatchs() {
  // undefined : chargement ; null : erreur
  const [matchs, setMatchs] = useState<Match[] | null | undefined>(undefined)

  useEffect(() => {
    let annule = false // la page quittée avant la réponse ne doit plus rien afficher
    api.match
      .GET('/api/v1/matches')
      .then(({ data }) => {
        if (!annule) setMatchs(data ?? null)
      })
      .catch(() => {
        if (!annule) setMatchs(null)
      })
    return () => {
      annule = true
    }
  }, [])

  return (
    <main>
      <h1>Mes matchs</h1>
      {matchs === undefined && <p>Chargement…</p>}
      {matchs === null && <p role="alert">Impossible de charger tes matchs. Réessaie dans un instant.</p>}
      {matchs?.length === 0 && (
        <p>
          Aucun match à venir. <Link to="/">Réserver un terrain</Link>
        </p>
      )}
      {matchs && matchs.length > 0 && (
        <ul>
          {matchs.map((m) => (
            <li key={m.id}>
              <Link to={`/matchs/${m.id}`}>
                {m.startsAt ? `${formatJour(m.startsAt)} à ${formatHeure(m.startsAt)}` : 'Match'}
              </Link>{' '}
              · {terrainParId(m.venueId)?.name ?? 'Terrain'} · {STATUTS[m.status] ?? m.status}
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
