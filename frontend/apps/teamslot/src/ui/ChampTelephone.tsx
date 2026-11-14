import { useId } from 'react'
import { PAYS, paysDe, type Pays } from '../pays'
import { CLASSE_SAISIE } from './Champ'

type Props = {
  pays: Pays
  onPays: (pays: Pays) => void
  numero: string
  onNumero: (numero: string) => void
  erreur?: string | null
}

/**
 * Téléphone en deux parties : le pays (drapeau + indicatif) puis le numéro. La liste des pays est un vrai
 * <select> : clavier, lecteur d'écran et mobile fonctionnent sans code en plus.
 */
export default function ChampTelephone({ pays, onPays, numero, onNumero, erreur }: Props) {
  const id = useId()
  const idAide = `${id}-aide`
  const idErreur = `${id}-erreur`
  const bordure = erreur ? 'border-alerte' : 'border-sauge/35 hover:border-sauge/60'
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={`${id}-numero`} className="text-sm font-semibold text-creme">
        Téléphone
      </label>
      <div className="flex gap-2">
        <div className="relative shrink-0">
          {pays.drapeau && (
            <img
              src={pays.drapeau}
              alt=""
              width={24}
              height={18}
              className="pointer-events-none absolute top-1/2 left-3 h-[18px] w-6 -translate-y-1/2 rounded-[2px]"
            />
          )}
          <select
            aria-label="Pays"
            value={pays.code}
            onChange={(e) => onPays(paysDe(e.target.value))}
            className={`${CLASSE_SAISIE} ${bordure} w-40 cursor-pointer truncate appearance-none pr-2 pl-11`}
          >
            {PAYS.map((p) => (
              <option key={p.code} value={p.code}>
                +{p.indicatif} {p.nom}
              </option>
            ))}
          </select>
        </div>
        <input
          id={`${id}-numero`}
          type="tel"
          name="phone"
          autoComplete="tel-national"
          inputMode="tel"
          placeholder={pays.code === 'MA' ? '6 12 34 56 78' : 'Numéro'}
          required
          aria-describedby={[idAide, erreur ? idErreur : null].filter(Boolean).join(' ')}
          aria-invalid={erreur ? true : undefined}
          value={numero}
          onChange={(e) => onNumero(e.target.value)}
          className={`${CLASSE_SAISIE} ${bordure} min-w-0 flex-1`}
        />
      </div>
      <p id={idAide} className="text-sm text-sauge">
        Choisis ton pays, puis tape ton numéro (avec ou sans le 0 du début).
      </p>
      {erreur && (
        <p id={idErreur} className="text-sm font-medium text-alerte">
          {erreur}
        </p>
      )}
    </div>
  )
}
