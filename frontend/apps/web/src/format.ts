const FUSEAU = 'Africa/Casablanca'

type Montant = { amount: number; currency: string }

/** Montant en centimes -> « 150,00 MAD ». Le code de la devise vient du serveur. */
export function formatMontant({ amount, currency }: Montant): string {
  return new Intl.NumberFormat('fr-MA', {
    style: 'currency',
    currency,
    currencyDisplay: 'code',
  }).format(amount / 100)
}

/** Heure d'un instant ISO (UTC) à Marrakech, quel que soit le fuseau du navigateur : « 18:00 ». */
export function formatHeure(iso: string): string {
  return new Intl.DateTimeFormat('fr-FR', {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: FUSEAU,
  }).format(new Date(iso))
}

/** Date du jour à Marrakech, au format AAAA-MM-JJ (valeur d'un <input type="date">). */
export function dateDuJour(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: FUSEAU })
}
