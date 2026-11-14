import { Color } from 'three'

/** Lit une couleur du thème (index.css) : la scène 3D suit les jetons, aucune couleur en dur ici. */
export function jeton(nom: string): Color {
  return new Color(getComputedStyle(document.documentElement).getPropertyValue(`--color-${nom}`).trim())
}

