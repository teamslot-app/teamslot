import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listerTerrains } from '../venuesApi'
import type { Terrain } from '../venues'

export default function Terrains() {
  // undefined : chargement ; null : erreur
  const [terrains, setTerrains] = useState<Terrain[] | null | undefined>(undefined)

  useEffect(() => {
    let annule = false // la page quittée avant la réponse ne doit plus rien afficher
    listerTerrains().then((liste) => {
      if (!annule) setTerrains(liste)
    })
    return () => {
      annule = true
    }
  }, [])

  return (
    <main>
      <h1>Terrains</h1>
      {terrains === undefined && <p>Chargement…</p>}
      {terrains === null && <p role="alert">Impossible de charger les terrains. Réessaie dans un instant.</p>}
      {terrains?.length === 0 && <p>Aucun terrain publié pour le moment.</p>}
      {terrains && terrains.length > 0 && (
        <ul>
          {terrains.map((terrain) => (
            <li key={terrain.id}>
              <Link to={`/terrains/${terrain.id}`}>{terrain.name}</Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
