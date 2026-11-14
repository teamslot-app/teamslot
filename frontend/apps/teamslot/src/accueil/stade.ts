import { CanvasTexture, RepeatWrapping, SRGBColorSpace } from 'three'
import { jeton } from './jetons'

/**
 * Le terrain de quartier : ses dimensions dans la scène (unités ≈ 2 m), à partir desquelles on place
 * le marquage, la cage, le grillage, les projecteurs et les limites du ballon.
 */
export type Stade = {
  cx: number // axe du terrain (x)
  zBut: number // ligne de but (au fond)
  zPres: number // ligne de but de l'autre camp (hors de l'image)
  demiLargeur: number
}

export const PIXELS_PAR_UNITE = 64

/**
 * Marquage et bandes de tonte, sur un canevas TRANSPARENT posé juste au-dessus de la vraie herbe :
 * bandes = voile sombre une bande sur deux, lignes = craie. Le haut du canevas = côté de la cage.
 */
export function textureMarquage(stade: Stade, largeurCage: number): CanvasTexture {
  const largeur = stade.demiLargeur * 2
  const longueur = stade.zPres - stade.zBut
  const k = PIXELS_PAR_UNITE
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(largeur * k)
  canvas.height = Math.round(longueur * k)
  const ctx = canvas.getContext('2d')
  if (ctx) {
    const w = canvas.width
    const h = canvas.height
    // bandes de tonte : l'herbe couchée dans l'autre sens paraît plus sombre
    ctx.fillStyle = jeton('maillot').getStyle()
    ctx.globalAlpha = 0.16
    const bande = 1.6 * k
    for (let y = 0, i = 0; y < h; y += bande, i++) if (i % 2) ctx.fillRect(0, y, w, bande)

    ctx.strokeStyle = jeton('craie').getStyle()
    ctx.fillStyle = ctx.strokeStyle
    ctx.globalAlpha = 0.85
    ctx.lineWidth = 0.1 * k
    const m = 0.35 * k
    ctx.strokeRect(m, m, w - 2 * m, h - 2 * m)
    const milieu = h / 2
    ctx.beginPath()
    ctx.moveTo(m, milieu)
    ctx.lineTo(w - m, milieu)
    ctx.stroke()
    ctx.beginPath()
    ctx.arc(w / 2, milieu, 2.6 * k, 0, Math.PI * 2)
    ctx.stroke()
    ctx.beginPath()
    ctx.arc(w / 2, milieu, 0.13 * k, 0, Math.PI * 2)
    ctx.fill()
    const surface = { l: (largeurCage + 4.4) * k, p: 3.4 * k }
    const petite = { l: (largeurCage + 1.6) * k, p: 1.2 * k }
    ctx.strokeRect(w / 2 - surface.l / 2, m, surface.l, surface.p)
    ctx.strokeRect(w / 2 - petite.l / 2, m, petite.l, petite.p)
    ctx.beginPath()
    ctx.arc(w / 2, m + 2.5 * k, 0.1 * k, 0, Math.PI * 2)
    ctx.fill()
    ctx.beginPath()
    ctx.arc(w / 2, m + surface.p, 1.6 * k, 0.15 * Math.PI, 0.85 * Math.PI)
    ctx.stroke()
  }
  const texture = new CanvasTexture(canvas)
  texture.colorSpace = SRGBColorSpace
  texture.anisotropy = 8
  return texture
}

/** Maille du grillage : losanges de fil, fond transparent, répétée sur toute la clôture. */
export function textureGrillage(): CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 64
  const ctx = canvas.getContext('2d')
  if (ctx) {
    ctx.strokeStyle = jeton('craie').getStyle()
    ctx.lineWidth = 3
    ctx.beginPath()
    ctx.moveTo(0, 32)
    ctx.lineTo(32, 0)
    ctx.lineTo(64, 32)
    ctx.lineTo(32, 64)
    ctx.closePath()
    ctx.stroke()
  }
  const texture = new CanvasTexture(canvas)
  texture.wrapS = texture.wrapT = RepeatWrapping
  texture.colorSpace = SRGBColorSpace
  return texture
}
