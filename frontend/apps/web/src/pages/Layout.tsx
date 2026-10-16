import { Link, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

/** Cadre des pages réservées aux utilisateurs connectés. */
export default function Layout() {
  const { seDeconnecter } = useAuth()
  return (
    <>
      <header>
        <Link to="/">TeamSlot</Link>
        <button type="button" onClick={seDeconnecter}>
          Se déconnecter
        </button>
      </header>
      <Outlet />
    </>
  )
}
