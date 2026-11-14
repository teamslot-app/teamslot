import { Suspense, lazy, useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import PelouseStatique from '../accueil/PelouseStatique'
import { peutAfficher3D } from '../accueil/peutAfficher3D'
import Logo from '../ui/Logo'
import { useAimant } from '../ui/useAimant'

// three.js ne se télécharge QUE sur l'accueil, et seulement si la scène 3D peut s'afficher
const ScenePelouse = lazy(() => import('../accueil/ScenePelouse'))

/**
 * Page publique : on arrive sur la pelouse, le ballon au rond central, la cage au fond. On peut tirer :
 * un but affiche « BUT ! » et fait monter le score. Un seul objectif de la page : créer son compte ;
 * le brassard ne dépend jamais du ballon (clavier, lecteur d'écran, mobile).
 */
export default function Accueil() {
  const [en3D] = useState(peutAfficher3D)
  const [buts, setButs] = useState(0)
  const [celebration, setCelebration] = useState(false)
  const aimant = useAimant<HTMLAnchorElement>()

  const surBut = useCallback(() => {
    setButs((n) => n + 1)
    setCelebration(true)
  }, [])

  useEffect(() => {
    if (!celebration) return
    const minuterie = window.setTimeout(() => setCelebration(false), 1800)
    return () => window.clearTimeout(minuterie)
  }, [celebration, buts])

  return (
    <div className="relative isolate min-h-svh overflow-hidden">
      <div className="absolute inset-0 -z-10">
        {en3D ? (
          <Suspense fallback={<PelouseStatique />}>
            <ScenePelouse onBut={surBut} />
          </Suspense>
        ) : (
          <PelouseStatique />
        )}
      </div>

      {/* Annonce du but : visible au centre, et lue par les lecteurs d'écran (aria-live) */}
      <div
        aria-live="polite"
        className="pointer-events-none absolute inset-x-0 top-[30%] flex justify-center lg:top-[34%] lg:justify-start lg:pl-[calc(64%-7.5rem)]"
      >
        {celebration && (
          <p key={buts} className="anim-but text-7xl font-black tracking-tight text-creme sm:text-8xl">
            But !
          </p>
        )}
      </div>

      {/* Voile sombre sous le texte (à gauche sur ordinateur, en bas sur téléphone) : garde le contraste sur la pelouse éclairée */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-linear-to-t from-maillot from-25% via-maillot/70 via-45% to-transparent to-62% lg:bg-linear-to-r lg:from-15% lg:via-maillot/55 lg:via-32% lg:to-52%"
      />

      {/* Voile en haut : l'en-tête reste lisible devant le ciel clair */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 top-0 h-40 bg-linear-to-b from-maillot/85 to-transparent"
      />

      <div className="pointer-events-none relative flex min-h-svh flex-col">
        <header className="mx-auto flex w-full max-w-7xl items-center justify-between gap-4 px-5 py-4 sm:px-8">
          <span className="pointer-events-auto">
            <Logo />
          </span>
          <div className="flex items-center gap-6">
            {en3D && (
              <p className="hidden font-bold text-creme sm:block">
                Buts <span className="tabular-nums">{buts}</span>
              </p>
            )}
            <Link to="/connexion" className="lien-brassard pointer-events-auto">
              Se connecter
            </Link>
          </div>
        </header>

        <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col justify-end px-5 pb-12 sm:px-8 lg:justify-center lg:pb-24">
          <h1 className="max-w-[12ch] text-[2.6rem] leading-[1.02] font-extrabold tracking-tight text-balance text-creme sm:text-6xl lg:text-[4.25rem]">
            Réserve ton terrain. Organise ton match.
          </h1>
          <p className="mt-5 max-w-sm text-lg leading-relaxed text-sauge">
            De Tanger à Laâyoune, trouve un créneau près de chez toi, invite tes amis et complète ton équipe.
          </p>
          <div className="mt-9 flex flex-wrap items-center gap-x-8 gap-y-4">
            <Link ref={aimant} to="/inscription" className="brassard pointer-events-auto">
              <span aria-hidden="true" className="brassard-c">
                C
              </span>
              Créer mon compte
            </Link>
            <Link to="/connexion" className="lien-brassard pointer-events-auto">
              J'ai déjà un compte
            </Link>
          </div>
          {en3D && <p className="mt-8 hidden text-sm text-sauge sm:block">Attrape le ballon et tire dans la cage.</p>}
        </main>
      </div>
    </div>
  )
}
