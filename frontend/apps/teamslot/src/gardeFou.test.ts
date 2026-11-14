import { describe, expect, it } from 'vitest'

/**
 * Garde-fou : des règles de sécurité et de thème vérifiées sur TOUS les fichiers de src/, y compris
 * ceux qui seront ajoutés plus tard. Un fichier qui les enfreint fait échouer la CI.
 * import.meta.glob (Vite) lit le texte brut des fichiers, sans dépendre de Node.
 */
const tous = import.meta.glob<string>(['./**/*.{ts,tsx}', '!./**/*.test.{ts,tsx}'], {
  query: '?raw',
  import: 'default',
  eager: true,
})
const sources = Object.entries(tous).map(([chemin, texte]) => ({ chemin, texte }))

const regles: [string, RegExp][] = [
  ["couleur en dur (utiliser les jetons de index.css)", /#[0-9a-fA-F]{3,8}\b|rgba?\(/],
  ["style en ligne (bloqué par la CSP de production)", /style=\{/],
  ['HTML injecté (risque XSS)', /dangerouslySetInnerHTML/],
  ['ressource venue d’un autre site (bloquée par la CSP)', /https?:\/\/(?!www\.w3\.org)/],
  ['stockage du navigateur (jetons : mémoire seulement)', /localStorage|sessionStorage|document\.cookie/],
]

describe('garde-fou', () => {
  it('trouve bien les fichiers à vérifier', () => {
    expect(sources.length).toBeGreaterThan(3)
  })

  for (const [regle, motif] of regles) {
    it(`aucun fichier : ${regle}`, () => {
      const fautifs = sources.filter((s) => motif.test(s.texte)).map((s) => s.chemin)
      expect(fautifs).toEqual([])
    })
  }
})
