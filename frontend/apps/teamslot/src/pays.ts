import { getCountries, getCountryCallingCode, isValidPhoneNumber, type CountryCode } from 'libphonenumber-js'

/**
 * SCRUM-135 — tous les pays du monde pour le téléphone de l'inscription.
 *   Indicatifs et validité des numéros : libphonenumber-js (licence MIT, données de Google libphonenumber).
 *   Noms en français : Intl.DisplayNames, fourni par le navigateur.
 *   Drapeaux : flag-icons (licence MIT), intégrés au build ; seul le drapeau affiché est téléchargé.
 */
const drapeaux = import.meta.glob<string>('../node_modules/flag-icons/flags/4x3/*.svg', {
  eager: true,
  query: '?url',
  import: 'default',
})

function drapeauDe(code: string): string | undefined {
  return drapeaux[`../node_modules/flag-icons/flags/4x3/${code.toLowerCase()}.svg`]
}

export type Pays = { code: CountryCode; nom: string; indicatif: string; drapeau?: string }

const noms = new Intl.DisplayNames(['fr'], { type: 'region' })

const tous: Pays[] = getCountries().map((code) => ({
  code,
  nom: noms.of(code) ?? code,
  indicatif: getCountryCallingCode(code),
  drapeau: drapeauDe(code),
}))

const maroc = tous.find((p) => p.code === 'MA')!

/** Le Maroc en premier (la plupart des joueurs), puis tous les autres par ordre alphabétique français. */
export const PAYS: Pays[] = [
  maroc,
  ...tous.filter((p) => p.code !== 'MA').sort((a, b) => a.nom.localeCompare(b.nom, 'fr')),
]

export const PAYS_PAR_DEFAUT = maroc

export function paysDe(code: string): Pays {
  return PAYS.find((p) => p.code === code) ?? PAYS_PAR_DEFAUT
}

/**
 * Numéro saisi + pays choisi → format international (E.164) attendu par le serveur.
 *   Maroc, « 06 45 48 84 42 » → +212645488442   ·   France, « 06 12 34 56 78 » → +33612345678
 *   « +33 6… » ou « 0033 6… » : l'utilisateur a déjà mis l'indicatif, on le garde tel quel.
 */
export function composerNumero(pays: Pays, saisie: string): string {
  const brut = saisie.replace(/[\s.()-]/g, '')
  if (brut.startsWith('+')) return brut
  if (brut.startsWith('00')) return `+${brut.slice(2)}`
  return `+${pays.indicatif}${brut.replace(/^0/, '')}`
}

/** Le numéro existe-t-il pour ce pays (bon indicatif, bonne longueur, bon début) ? */
export function numeroValide(pays: Pays, telephone: string): boolean {
  return isValidPhoneNumber(telephone, pays.code) && telephone.startsWith(`+${pays.indicatif}`)
}
