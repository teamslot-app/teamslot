// TEMPORAIRE (SCRUM-94) : GET /api/v1/venues (listVenues) n'est pas codé côté serveur et il n'existe pas de
// GET /venues/{id} pour les équipements. Ce sont les 3 terrains des données de démo de venue-service
// (db/demo/R__demo_data.sql, profil demo). À supprimer quand l'API existera.
// Aucun prix ici : les prix affichés et payés viennent toujours du serveur.
export type Equipement = { id: string; name: string }
export type Terrain = {
  id: string
  name: string
  acceptsOnSitePayment: boolean
  equipment: Equipement[]
}

export const TERRAINS: Terrain[] = [
  {
    id: 'a1000000-0000-4000-8000-000000000001',
    name: 'Terrain Démo Guéliz',
    acceptsOnSitePayment: true,
    equipment: [
      { id: 'c1000000-0000-4000-8000-000000000001', name: 'Chasubles' },
      { id: 'c1000000-0000-4000-8000-000000000002', name: 'Ballon' },
    ],
  },
  {
    id: 'a1000000-0000-4000-8000-000000000002',
    name: 'Terrain Démo Daoudiate',
    acceptsOnSitePayment: false,
    equipment: [
      { id: 'c1000000-0000-4000-8000-000000000003', name: 'Chasubles' },
      { id: 'c1000000-0000-4000-8000-000000000004', name: 'Ballon' },
    ],
  },
  {
    id: 'a1000000-0000-4000-8000-000000000003',
    name: 'Terrain Démo Massira',
    acceptsOnSitePayment: true,
    equipment: [
      { id: 'c1000000-0000-4000-8000-000000000005', name: 'Chasubles' },
      { id: 'c1000000-0000-4000-8000-000000000006', name: 'Ballon' },
    ],
  },
]

export const terrainParId = (id: string | undefined) => TERRAINS.find((t) => t.id === id)
