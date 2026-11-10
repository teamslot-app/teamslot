import type { ButtonHTMLAttributes } from 'react'
import { Link, type LinkProps } from 'react-router-dom'
import { classes } from './classes'
import { classesBouton, type Variante } from './styleBouton'

type ProprietesBouton = ButtonHTMLAttributes<HTMLButtonElement> & {
  variante?: Variante
  pleineLargeur?: boolean
}

/** Bouton de 44 px minimum ; type « button » par défaut pour ne jamais soumettre un formulaire par accident. */
export function Bouton({
  variante,
  pleineLargeur,
  className,
  type = 'button',
  ...reste
}: ProprietesBouton) {
  return (
    <button
      type={type}
      className={classes(classesBouton(variante, pleineLargeur), className)}
      {...reste}
    />
  )
}

type ProprietesLien = LinkProps & {
  variante?: Variante
  pleineLargeur?: boolean
}

/** Lien de navigation (react-router) avec l'apparence d'un bouton : « Réserver », « Commencer ». */
export function LienBouton({
  variante,
  pleineLargeur,
  className,
  ...reste
}: ProprietesLien) {
  return (
    <Link
      className={classes(classesBouton(variante, pleineLargeur), className)}
      {...reste}
    />
  )
}
