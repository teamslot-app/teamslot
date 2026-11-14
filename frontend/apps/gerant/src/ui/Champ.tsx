import { useId, useState, type InputHTMLAttributes, type ReactNode } from 'react'
import Oeil from './Oeil'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'className'> & {
  libelle: string
  aide?: ReactNode
  /** Message d'erreur du champ : affiché juste en dessous et lu par les lecteurs d'écran. */
  erreur?: string | null
}

export const CLASSE_SAISIE =
  'min-h-12 rounded-[3px] border bg-maillot/70 px-4 text-base text-creme [color-scheme:dark] placeholder:text-sauge/60 focus:border-creme focus:outline-none'

/**
 * Un champ de formulaire : libellé visible au-dessus (jamais seulement un placeholder), aide et erreur reliées
 * au champ (aria-describedby), hauteur 48 px pour le doigt. Pour un mot de passe : bouton œil Afficher / Masquer.
 */
export default function Champ({ libelle, aide, erreur, type = 'text', ...reste }: Props) {
  const id = useId()
  const idAide = `${id}-aide`
  const idErreur = `${id}-erreur`
  const [visible, setVisible] = useState(false)
  const motDePasse = type === 'password'
  const decrit = [aide ? idAide : null, erreur ? idErreur : null].filter(Boolean).join(' ') || undefined
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-semibold text-creme">
        {libelle}
      </label>
      <div className="relative">
        <input
          id={id}
          type={motDePasse && visible ? 'text' : type}
          aria-describedby={decrit}
          aria-invalid={erreur ? true : undefined}
          className={`${CLASSE_SAISIE} w-full ${erreur ? 'border-alerte' : 'border-sauge/35 hover:border-sauge/60'} ${motDePasse ? 'pr-14' : ''}`}
          {...reste}
        />
        {motDePasse && (
          <button
            type="button"
            onClick={() => setVisible((v) => !v)}
            aria-label={visible ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
            aria-pressed={visible}
            className="absolute inset-y-0 right-0 flex min-w-12 items-center justify-center text-sauge hover:text-creme focus-visible:text-creme"
          >
            <Oeil barre={visible} />
          </button>
        )}
      </div>
      {aide && (
        <div id={idAide} className="text-sm text-sauge">
          {aide}
        </div>
      )}
      {erreur && (
        <p id={idErreur} className="text-sm font-medium text-alerte">
          {erreur}
        </p>
      )}
    </div>
  )
}
