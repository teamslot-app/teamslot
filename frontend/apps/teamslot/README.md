# Nouveau front TeamSlot (`apps/teamslot`)

Front repris de zéro, thème **« Lions de l'Atlas »** : les couleurs du Maroc, vert profond et rouge du maillot, sans
blanc dans l'interface. Sur l'accueil, un terrain de quartier en 3D éclairé de nuit (projecteurs, ombres, grillage), une cage
et un vrai ballon qu'on attrape et qu'on tire
(panneaux aux noms des villes du Maroc) : un but affiche « BUT ! » et fait monter le score. Il est construit **à côté** de `apps/web`, qui reste
le front déployé en staging tant que celui-ci n'a pas toutes les pages. Ensuite, on bascule l'image `web`
sur ce dossier et on supprime `apps/web`.

Stack inchangée : React + TypeScript + Vite, React Router, client d'API généré `@teamslot/shared`
(alias, comme dans `apps/web`). En plus : **Tailwind CSS 4** (jetons du thème dans `src/index.css`), les
police **Schibsted Grotesk** (une seule famille, moderne et sobre), auto-hébergées
(`@fontsource/…`), et
**three.js + React Three Fiber** pour le ballon (chargés seulement sur l'accueil) : rien n'est chargé depuis un autre site, ce
qu'exige la CSP de production (`default-src 'self'`).

## Lancer

```bash
cd frontend/apps/teamslot
npm ci
npm run dev        # http://localhost:5174 (apps/web garde le 5173)
npm test
npm run lint
npm run build
```

## Fichiers réels (`public/accueil/`)

| Fichier | Origine | Licence |
|---|---|---|
| `ballon/` (glTF + textures 1K) | « Dirty Football », Poly Haven, converti depuis le .blend 4K | CC0 |
| `terrain.hdr` (2K) | HDRI « Netball Court », Poly Haven, réduit depuis l'EXR 4K | CC0 |
| `herbe.jpg`, `herbe-normal.jpg` | « Grass006 » d'ambientCG (couleur et NormalGL), réduite en 1K, teinte ajustée à la scène de nuit | CC0 |
| `affiche-*.webp` | rendu fixe de la scène (herbe ambientCG), sans texte | CC0 (dérivé de fichiers CC0) |

Toutes les ressources sont en CC0 (Poly Haven, ambientCG) : le dépôt est public, aucune image sous licence
restrictive ne doit y entrer. Une nouvelle image : vérifier sa licence **avant** le commit.

## La scène 3D (`src/accueil/`)

- `Decor.tsx` : ciel et lumière réels (HDRI en fond et en éclairage, soleil avec ombres), pelouse (photo d'herbe
  répétée avec son relief), marquage et bandes de tonte (`stade.ts`, canevas), grillage avec toile brise-vue.
- `BallonReel.tsx` : le vrai ballon (glTF), recentré et mis à l'échelle.
- `ScenePelouse.tsx` : cage avec filet, physique maison du ballon (gravité, rebonds, roulement, grillage) ;
  on l'attrape, on tire ; ballon dans l'ouverture de la cage → `onBut()`, puis remise au point de coup d'envoi.
- `PelouseStatique.tsx` : image fixe de la scène si pas de WebGL, « réduire les animations », ou pendant le chargement.
- Fichiers séparés (.gltf + .bin + .jpg), pas de .glb : un .glb crée des URL `blob:` que la CSP bloque.

## Boutons (`src/index.css`)

- `.brassard` : action principale, brassard de capitaine rouge en biais avec le « C », qui se soulève au
  survol et s'enfonce à l'appui ; léger effet aimant sur ordinateur (`useAimant`, inspiré des Magnetic
  Buttons de Codrops).
- `.lien-brassard` : action secondaire, un trait rouge se trace sous le texte au survol.

## Règles (vérifiées par `src/gardeFou.test.ts`)

- Couleurs : uniquement les jetons du thème (`bg-nuit`, `text-craie`, `bg-gazon`…), jamais de `#hex`.
- Pas de `style={…}`, pas de `dangerouslySetInnerHTML`, pas d'URL externe.
- Aucun accès à `localStorage`, `sessionStorage` ou aux cookies : les jetons restent en mémoire.
- Contraste AA (4,5:1) : les valeurs mesurées sont notées à côté des jetons.

## Avancement

| Étape | Contenu | État |
|---|---|---|
| 1 | Squelette, thème, accueil public avec le ballon 3D | ✅ |
| 2 | Connexion, inscription, cadre des pages connectées (session reprise de `apps/web`) | ✅ |
| 3 | Terrains, créneaux, réservation | à faire |
| 4 | Page du match, matchs ouverts, demandes | à faire |
| 5 | CI (`teamslot.yml`), image Docker, bascule du staging | à faire |
