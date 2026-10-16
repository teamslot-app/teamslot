import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import type { VenueComponents } from '@teamslot/shared'
import { api } from '../api'
import { dateDuJour, formatHeure, formatMontant } from '../format'
import { terrainParId } from '../venues'

type Creneau = VenueComponents['schemas']['Slot']
type Resultat = { cle: string; creneaux: Creneau[] | null }

const cleDe = (terrainId: string, date: string) => `${terrainId}|${date}`

export default function Creneaux() {
  const { venueId } = useParams()
  const terrain = terrainParId(venueId)
  const [aujourdhui] = useState(dateDuJour)
  const [date, setDate] = useState(aujourdhui)
  const [resultat, setResultat] = useState<Resultat | null>(null)

  useEffect(() => {
    if (!terrain || !date) return
    const cle = cleDe(terrain.id, date)
    let annule = false // une réponse en retard ne doit pas écraser celle de la date choisie ensuite
    api.venue
      .GET('/api/v1/venues/{id}/slots', { params: { path: { id: terrain.id }, query: { date } } })
      .then(({ data }) => {
        if (!annule) setResultat({ cle, creneaux: data ?? null })
      })
      .catch(() => {
        if (!annule) setResultat({ cle, creneaux: null })
      })
    return () => {
      annule = true
    }
  }, [terrain, date])

  if (!terrain) return <Navigate to="/" replace />

  const courant = resultat && resultat.cle === cleDe(terrain.id, date) ? resultat : null
  const creneaux = courant?.creneaux ?? null

  return (
    <main>
      <p>
        <Link to="/">← Terrains</Link>
      </p>
      <h1>{terrain.name}</h1>
      <label>
        Date
        <input type="date" value={date} min={aujourdhui} onChange={(e) => setDate(e.target.value)} />
      </label>
      {!date && <p>Choisis une date.</p>}
      {date && courant === null && <p>Chargement…</p>}
      {courant && creneaux === null && (
        <p role="alert">Impossible de charger les créneaux. Réessaie dans un instant.</p>
      )}
      {creneaux?.length === 0 && <p>Aucun créneau ce jour-là.</p>}
      {creneaux && creneaux.length > 0 && (
        <ul>
          {creneaux.map((creneau) => (
            <li key={creneau.id}>
              {formatHeure(creneau.start)} – {formatHeure(creneau.end)}
              {creneau.price && <> · {formatMontant(creneau.price)}</>}{' '}
              {creneau.available ? (
                <Link to={`/terrains/${terrain.id}/creneaux/${creneau.id}`}>Réserver</Link>
              ) : (
                <span>Indisponible</span>
              )}
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
