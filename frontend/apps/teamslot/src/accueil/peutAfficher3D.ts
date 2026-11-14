/**
 * Le ballon 3D seulement si le navigateur sait faire du WebGL ET si la personne n'a pas demandé
 * de réduire les animations (réglage système « prefers-reduced-motion »). Sinon : terrain et ballon dessinés, fixes.
 */
export function peutAfficher3D(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return false
  try {
    const canvas = document.createElement('canvas')
    return Boolean(canvas.getContext('webgl2') ?? canvas.getContext('webgl'))
  } catch {
    return false
  }
}
