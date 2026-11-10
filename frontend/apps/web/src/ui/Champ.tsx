import { useId, type InputHTMLAttributes } from 'react'
import { classes } from './classes'
import { Icone } from './Icone'

type Proprietes = InputHTMLAttributes<HTMLInputElement> & {
  libelle: string
  aide?: string
  erreur?: string | null
}

/**
 * Champ avec libellé toujours visible. Le <label> englobe l'<input> (getByLabelText('Téléphone') fonctionne),
 * l'aide et l'erreur sont reliées par aria-describedby.
 */
export function Champ({
  libelle,
  aide,
  erreur,
  className,
  ...input
}: Proprietes) {
  const id = useId()
  const idAide = aide ? `${id}-aide` : undefined
  const idErreur = erreur ? `${id}-erreur` : undefined
  const decrit = [idAide, idErreur].filter(Boolean).join(' ') || undefined

  return (
    <div className={classes('flex flex-col gap-1', className)}>
      <label className="flex flex-col gap-1 text-sm font-semibold text-ink">
        {libelle}
        <input
          {...input}
          aria-describedby={decrit}
          aria-invalid={erreur ? true : undefined}
          className={classes(
            'min-h-11 rounded-sm border-2 bg-surface px-3 py-2 text-base font-normal text-ink placeholder:text-muted',
            erreur ? 'border-error' : 'border-muted',
          )}
        />
      </label>
      {aide && (
        <p id={idAide} className="text-sm text-muted">
          {aide}
        </p>
      )}
      {erreur && (
        <p
          id={idErreur}
          className="flex items-center gap-1 text-sm font-semibold text-error"
        >
          <Icone nom="alerte" className="size-4" />
          {erreur}
        </p>
      )}
    </div>
  )
}
