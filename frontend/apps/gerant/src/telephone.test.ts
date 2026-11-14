import { describe, expect, it } from 'vitest'
import { versInternational } from './telephone'

describe('numéro de téléphone', () => {
  it.each([
    ['0645488442', '+212645488442'],
    ['06 45 48 84 42', '+212645488442'],
    ['07-12-34-56-78', '+212712345678'],
    ['00212645488442', '+212645488442'],
    ['+212645488442', '+212645488442'],
    ['+33612345678', '+33612345678'],
  ])('%s → %s', (saisie, attendu) => {
    expect(versInternational(saisie)).toBe(attendu)
  })
})
