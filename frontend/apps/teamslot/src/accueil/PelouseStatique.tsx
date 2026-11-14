/**
 * Image fixe de la vraie scène 3D (rendue à l'avance, sans le texte) : affichée sans WebGL, avec
 * « réduire les animations », et pendant le chargement de la scène (ballon, herbe, ciel : ~9 Mo).
 * Une version en hauteur pour les téléphones. Décorative : texte alternatif vide.
 */
export default function PelouseStatique() {
  return (
    <picture>
      <source media="(max-width: 767px)" srcSet="/accueil/affiche-mobile.webp" />
      <img src="/accueil/affiche-bureau.webp" alt="" className="size-full object-cover" />
    </picture>
  )
}
