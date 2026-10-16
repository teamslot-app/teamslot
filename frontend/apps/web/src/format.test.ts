import { describe, expect, it } from 'vitest'
import { dateDuJour, formatHeure, formatMontant } from './format'

describe('formats', () => {
  it('montant en centimes avec le code de la devise du serveur', () => {
    expect(formatMontant({ amount: 15000, currency: 'MAD' }).replace(/\s/g, ' ')).toBe('150,00 MAD')
  })

  it('heure à Marrakech, quel que soit le fuseau du navigateur', () => {
    // En juillet le Maroc est à UTC+1 (hors Ramadan) : 18:00 UTC = 19:00 à Marrakech.
    expect(formatHeure('2026-07-15T18:00:00Z')).toBe('19:00')
  })

  it('date du jour au format AAAA-MM-JJ', () => {
    expect(dateDuJour()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })
})
