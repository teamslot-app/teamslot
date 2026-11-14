import { describe, expect, it } from 'vitest'
import { composerNumero, numeroValide, PAYS, paysDe } from './pays'

describe('composerNumero', () => {
  it('Maroc : on retire le 0 et on ajoute +212', () => {
    expect(composerNumero(paysDe('MA'), '06 45 48 84 42')).toBe('+212645488442')
    expect(composerNumero(paysDe('MA'), '645488442')).toBe('+212645488442')
  })
  it('France : +33', () => {
    expect(composerNumero(paysDe('FR'), '06.12.34.56.78')).toBe('+33612345678')
  })
  it('indicatif déjà tapé : gardé tel quel', () => {
    expect(composerNumero(paysDe('MA'), '+33 6 12 34 56 78')).toBe('+33612345678')
    expect(composerNumero(paysDe('MA'), '00212645488442')).toBe('+212645488442')
  })
})

describe('liste des pays', () => {
  it('tous les pays, Maroc en premier, noms en français, avec drapeau', () => {
    expect(PAYS.length).toBeGreaterThan(240)
    expect(PAYS[0]).toMatchObject({ code: 'MA', nom: 'Maroc', indicatif: '212' })
    expect(paysDe('DE').nom).toBe('Allemagne')
    expect(PAYS.filter((p) => p.drapeau).length).toBeGreaterThan(240)
  })
  it('numéro valide pour le pays choisi', () => {
    expect(numeroValide(paysDe('MA'), '+212645488442')).toBe(true)
    expect(numeroValide(paysDe('MA'), '+21264548844')).toBe(false)
    expect(numeroValide(paysDe('FR'), '+212645488442')).toBe(false)
  })
})
