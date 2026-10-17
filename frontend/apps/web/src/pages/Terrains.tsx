import { Link } from 'react-router-dom'
import { TERRAINS } from '../venues'

export default function Terrains() {
  return (
    <main>
      <h1>Terrains</h1>
      <ul>
        {TERRAINS.map((terrain) => (
          <li key={terrain.id}>
            <Link to={`/terrains/${terrain.id}`}>{terrain.name}</Link>
          </li>
        ))}
      </ul>
    </main>
  )
}
