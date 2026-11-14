import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import Logo from '../ui/Logo'

const lien = ({ isActive }: { isActive: boolean }) =>
  `inline-flex min-h-11 items-center font-semibold ${isActive ? 'text-creme underline decoration-rouge-vif decoration-3 underline-offset-8' : 'text-sauge hover:text-creme'}`

/** Cadre de l'espace gérant (contenu des pages : SCRUM-141). */
export default function Layout() {
  const { seDeconnecter } = useAuth()
  return (
    <div className="min-h-svh">
      <header className="border-b border-sauge/15">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-x-8 gap-y-2 px-5 py-3 sm:px-8">
          <Logo />
          <nav aria-label="Navigation de l'espace gérant" className="flex flex-wrap items-center gap-6">
            <NavLink to="/mon-terrain" className={lien}>
              Mon terrain
            </NavLink>
            <NavLink to="/reservations" className={lien}>
              Réservations
            </NavLink>
            <button
              type="button"
              onClick={seDeconnecter}
              className="inline-flex min-h-11 items-center font-semibold text-sauge hover:text-creme"
            >
              Se déconnecter
            </button>
          </nav>
        </div>
      </header>
      <Outlet />
    </div>
  )
}
