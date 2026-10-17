import { describe, expect, it } from 'vitest'
import { dateDuJour, formatHeure, formatMontant } from './format'

const heureDans = (fuseau: string, iso: string) =>
  new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit', timeZone: fuseau }).format(new Date(iso))

describe('formats', () => {
  it('montant en centimes avec le code de la devise du serveur', () => {
    expect(formatMontant({ amount: 15000, currency: 'MAD' }).replace(/\s/g, ' ')).toBe('150,00 MAD')
  })

  it('heure affichée au fuseau Africa/Casablanca, pas à celui du navigateur', () => {
    // Le décalage (UTC+0 ou UTC+1) vient de la base de fuseaux du moteur JavaScript et a changé :
    // ne jamais écrire « 18:00 » ou « 19:00 » en dur ici.
    const iso = '2026-10-17T18:00:00Z'
    expect(formatHeure(iso)).toBe(heureDans('Africa/Casablanca', iso))
    expect(formatHeure(iso)).not.toBe(heureDans('Asia/Tokyo', iso))
  })

  it('date du jour au format AAAA-MM-JJ', () => {
    expect(dateDuJour()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })
})
