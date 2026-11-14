import type { ReactNode } from 'react'
import Logo from '../ui/Logo'

/**
 * Cadre des écrans Connexion et Inscription de la plateforme propriétaire : le formulaire à gauche, et à droite
 * (sur ordinateur) la photo d'un terrain. Même habillage que la plateforme joueur.
 */
export default function EcranAuth({
  titre,
  sousTitre,
  children,
  bas,
}: {
  titre: string
  sousTitre: string
  children: ReactNode
  bas: ReactNode
}) {
  return (
    <div className="grid min-h-svh lg:grid-cols-[minmax(0,34rem)_1fr]">
      <div className="flex flex-col px-6 py-6 sm:px-12">
        <Logo />
        <main className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center py-10">
          <h1 className="text-4xl leading-tight font-extrabold tracking-tight text-creme">{titre}</h1>
          <p className="mt-2 text-sauge">{sousTitre}</p>
          <div className="mt-8">{children}</div>
          <p className="mt-8 text-sauge">{bas}</p>
        </main>
      </div>
      <div aria-hidden="true" className="relative hidden overflow-hidden lg:block">
        <img src="/affiche.webp" alt="" className="absolute inset-0 size-full object-cover object-[70%_50%]" />
        <div className="absolute inset-0 bg-linear-to-r from-maillot to-transparent to-35%" />
      </div>
    </div>
  )
}
