import type { VenueComponents } from '@teamslot/shared'
import { api } from './api'
import { memoriserTerrains, type Terrain } from './venues'

type Venue = VenueComponents['schemas']['Venue']

/** Aucun prix ici : les prix affichés et payés viennent toujours du serveur. */
const versTerrain = (v: Venue): Terrain => ({
  id: v.id,
  name: v.name,
  acceptsOnSitePayment: v.acceptsOnSitePayment,
  equipment: (v.equipment ?? []).map((e) => ({ id: e.id, name: e.name })),
})

/** SCRUM-138 : terrains publiés (GET /api/v1/venues, triés par nom). null en cas d'erreur. */
export async function listerTerrains(): Promise<Terrain[] | null> {
  try {
    const { data } = await api.venue.GET('/api/v1/venues', { params: { query: { size: 50 } } })
    if (!data) return null
    const terrains = data.content.map(versTerrain)
    memoriserTerrains(terrains)
    return terrains
  } catch {
    return null
  }
}
