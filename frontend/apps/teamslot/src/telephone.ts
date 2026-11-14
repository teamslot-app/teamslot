/**
 * Numéro saisi « à la marocaine » → format international attendu par le serveur.
 *   06 45 48 84 42 → +212645488442   ·   00212645488442 → +212645488442   ·   +212645488442 → inchangé
 * Les autres formats sont renvoyés tels quels (espaces, points et tirets retirés) : le serveur décide.
 */
export function versInternational(saisie: string): string {
  const brut = saisie.replace(/[\s.-]/g, '')
  if (/^0[5-7]\d{8}$/.test(brut)) return `+212${brut.slice(1)}`
  if (/^00\d+$/.test(brut)) return `+${brut.slice(2)}`
  return brut
}
