// @vitest-environment node
/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

// Vitest remplace les imports CSS par un module vide (même en ?raw) : on lit le fichier sur le disque.
const indexCss = readFileSync(new URL('../index.css', import.meta.url), 'utf8')

/*
 * Garde-fou du design system et de la CSP (default-src 'self') : on relit le code source (hors tests)
 * et on refuse ce qui casserait les règles, pour qu'un écart soit vu en revue et non en production.
 */
const sources = import.meta.glob<string>(
  ['/src/**/*.{ts,tsx}', '!/src/**/*.test.{ts,tsx}'],
  {
    query: '?raw',
    import: 'default',
    eager: true,
  },
)

const INTERDITS: Array<[string, RegExp]> = [
  ['couleur hex (utiliser un token)', /#[0-9a-fA-F]{3,8}\b/],
  [
    'valeur Tailwind arbitraire de couleur',
    /\b(?:bg|text|border|fill|stroke|ring|outline|shadow|from|via|to|decoration)-\[/,
  ],
  ['attribut style inline (CSP)', /\bstyle=\{/],
  ['dangerouslySetInnerHTML', /dangerouslySetInnerHTML/],
  [
    'URL externe (police, image, script : tout doit venir du site)',
    /https?:\/\//,
  ],
  [
    'arrondi supérieur à 4 px',
    /\brounded(?:-[trblse]{1,2})?-(?:md|lg|xl|2xl|3xl|4xl|full)\b/,
  ],
  ['dégradé décoratif', /\bbg-(?:gradient|linear|radial|conic)-/],
  ['glassmorphism', /\bbackdrop-(?:blur|saturate)/],
  ['emoji (utiliser une icône SVG)', /\p{Extended_Pictographic}/u],
  // usage réel seulement : session.ts cite ces noms dans un commentaire pour dire qu'on ne s'en sert pas
  [
    'stockage du navigateur (jetons en mémoire seulement)',
    /\b(?:localStorage|sessionStorage)\s*[.[]|document\.cookie\s*=/,
  ],
]

describe('garde-fou du design system', () => {
  it('trouve bien les fichiers à contrôler', () => {
    expect(Object.keys(sources)).toContain('/src/ui/Bouton.tsx')
    expect(Object.keys(sources)).toContain('/src/App.tsx')
  })

  it('chaque règle attrape bien un exemple fautif', () => {
    const exemples = [
      'className="text-[#E63946]"',
      'className="bg-[var(--x)]"',
      '<div style={{ color: 1 }} />',
      '<div dangerouslySetInnerHTML={x} />',
      "@import url('https://fonts.googleapis.com/css2')",
      'className="rounded-lg"',
      'className="bg-linear-to-r"',
      'className="backdrop-blur-md"',
      'Bravo ⚽',
      "localStorage.setItem('jeton', x)",
    ]
    INTERDITS.forEach(([regle, motif], i) =>
      expect(motif.test(exemples[i]), regle).toBe(true),
    )
    expect(INTERDITS[1][1].test('className="rounded-sm"')).toBe(false)
    expect(INTERDITS[5][1].test('className="rounded-sm rounded-xs"')).toBe(
      false,
    )
  })

  it.each(INTERDITS)('aucun fichier source : %s', (_regle, motif) => {
    const fautifs = Object.entries(sources)
      .filter(([, contenu]) => motif.test(contenu))
      .map(([chemin]) => chemin)
    expect(fautifs).toEqual([])
  })

  it('index.css : aucune police ni ressource distante', () => {
    expect(indexCss).not.toMatch(/https?:\/\//)
    expect(indexCss).not.toMatch(/fonts\.googleapis|fonts\.gstatic/)
    expect(indexCss).toMatch(/@fontsource\/bebas-neue/)
    expect(indexCss).toMatch(/@fontsource-variable\/inter/)
  })
})
