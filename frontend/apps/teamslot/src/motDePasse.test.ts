import { describe, expect, it } from 'vitest'
import { motDePasseValide, reglesMotDePasse } from './motDePasse'

const ctx = {
  nom: 'Yassir',
  email: 'yassir.b@gmail.com',
  telephone: '+212645488442',
}

describe('règles du mot de passe (mêmes que le serveur)', () => {
  it('mot de passe solide', () => {
    expect(motDePasseValide('Terrain-Synthe-2026', ctx)).toBe(true)
  })
  it('trop court / deux types seulement', () => {
    expect(motDePasseValide('Court-12', ctx)).toBe(false)
    expect(motDePasseValide('toutenminuscules2026', ctx)).toBe(false)
  })
  it('contient le nom, l’e-mail ou le numéro', () => {
    expect(motDePasseValide('YASSIR-Gardien-2026', ctx)).toBe(false)
    expect(motDePasseValide('Yassir.B-Match-9', { ...ctx, nom: 'Y' })).toBe(false)
    expect(motDePasseValide('Match-488442-Soir', ctx)).toBe(false)
  })
  it('vide : aucune règle cochée', () => {
    expect(reglesMotDePasse('', ctx).some((r) => r.respectee)).toBe(false)
  })
})
