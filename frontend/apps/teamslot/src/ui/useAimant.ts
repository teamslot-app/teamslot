import { useEffect, useRef } from 'react'

/**
 * Effet « aimant » (idée des Magnetic Buttons de Codrops) : sur ordinateur, le bouton glisse de quelques
 * pixels vers le curseur quand on s'en approche. Passe par des variables CSS (--aimant-x / --aimant-y),
 * pas par un attribut style : compatible avec la CSP. Désactivé au toucher et si « réduire les animations ».
 */
export function useAimant<T extends HTMLElement>(force = 0.18, portee = 90) {
  const ref = useRef<T>(null)
  useEffect(() => {
    const el = ref.current
    if (!el || typeof window.matchMedia !== 'function') return
    if (!window.matchMedia('(hover: hover) and (pointer: fine)').matches) return
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

    function bouger(evenement: PointerEvent) {
      if (!el) return
      const r = el.getBoundingClientRect()
      const dx = evenement.clientX - (r.left + r.width / 2)
      const dy = evenement.clientY - (r.top + r.height / 2)
      const proche = Math.hypot(dx, dy) < Math.max(r.width, r.height) / 2 + portee
      el.style.setProperty('--aimant-x', proche ? `${dx * force}px` : '0px')
      el.style.setProperty('--aimant-y', proche ? `${dy * force}px` : '0px')
    }
    window.addEventListener('pointermove', bouger)
    return () => window.removeEventListener('pointermove', bouger)
  }, [force, portee])
  return ref
}
