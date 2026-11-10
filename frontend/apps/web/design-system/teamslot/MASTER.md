# TeamSlot — design system « Stade Authentique » (MASTER)

Source de vérité du style de `frontend/apps/web`. Une page peut le préciser dans `pages/<page>.md` ; sinon ce fichier s'applique.

**Esprit** : le terrain de quartier à Marrakech, le soir. Tableau d'affichage, feuille de match, ticket d'entrée. Usage principal : téléphone, en extérieur ou dans la pénombre, souvent d'une main.

**Origine** : généré avec le skill ui-ux-pro-max (`--design-system "sports booking football pitch mobile"`, dials variance 4 / motion 3 / density 5), puis corrigé pour suivre la direction artistique validée. Gardé du résultat : Bebas Neue en titres, mouvement « subtil », motif « héros + un seul appel à l'action » (accueil), checklist de livraison. Écarté : primaire rouge `#DC2626` et fond rosé (remplacés par la palette ci-dessous), Source Sans 3 (remplacée par Inter), import Google Fonts (interdit par la CSP : polices auto-hébergées), GSAP (inutile). Inspiration 21st.dev (structure seulement, aucun code importé) : ticket à talon perforé, badge à icône en tête, liste de créneaux lisible d'un coup d'œil.

## 1. Couleurs (tokens Tailwind, `src/index.css`)

Les couleurs par défaut de Tailwind sont effacées (`--color-*: initial`) : `bg-red-500` n'existe pas. **Jamais de hex dans un composant** (un test le vérifie).

| Token | Hex | Rôle |
|---|---|---|
| `primary` | `#0E3B2E` | Vert terrain : en-têtes, heures du tableau d'affichage, bouton principal, focus |
| `surface` | `#FFFFFF` | Fonds de blocs, texte sur fond sombre |
| `paper` | `#F8F4E7` | Fond de page (feuille de match) |
| `action` | `#C62F3B` | Appel à l'action (Réserver, Commencer). Pas `#E63946` : 4,0:1 seulement avec le blanc |
| `action-hover` | `#A3242F` | Survol de l'action |
| `accent` | `#FFC857` | Ambre : prix, scores, statut « en attente ». **Toujours en fond**, jamais en texte sur clair |
| `ink` | `#1F2937` | Texte courant |
| `muted` | `#4B5563` | Texte secondaire, bordures de champ |
| `line` | `#D6CFB8` | Séparateurs décoratifs uniquement (1,4:1, ne porte aucune information) |
| `error` / `error-bg` | `#9A3412` / `#FBEAE3` | Rouge brique des erreurs, distinct du rouge d'action |
| `confirm` / `confirm-bg` | `#14532D` / `#E3F0E6` | Confirmations |

### Contrastes vérifiés (WCAG 2.2, AA = 4,5:1 texte, 3:1 composants)

| Texte / fond | Ratio |
|---|---|
| surface / action (bouton Réserver) | 5,43 |
| action / paper | 4,93 |
| surface / action-hover | 7,37 |
| surface / primary | 12,47 |
| ink / paper | 13,34 |
| ink / accent (ticket, badge en attente) | 9,54 |
| primary / accent | 8,11 |
| muted / paper | 6,87 (bordure de champ ≥ 3:1) |
| error / surface · error / error-bg | 7,31 · 6,26 |
| confirm / confirm-bg | 7,75 |
| accent / primary (focus sur fond sombre) | 8,11 |

À éviter : `action` sur `primary` (2,3:1), `accent` en texte sur `paper` ou `surface`.

## 2. Typographie

Auto-hébergée (`@fontsource/bebas-neue` latin 400, `@fontsource-variable/inter` axe wght) : les woff2 sont copiés dans `dist/assets`, aucune requête externe.

| Token | Police | Usage |
|---|---|---|
| `font-display` | Bebas Neue | Titres, heures, prix, badges. Capitales natives, jamais en dessous de 18 px |
| `font-sans` | Inter Variable | Texte, libellés, boutons. 16 px minimum pour le texte courant et les champs (évite le zoom iOS) |

Échelle : `text-sm` 14 (aide, libellés) · `text-base` 16 (texte) · `text-lg` 18 (badge) · `text-2xl` 24 (prix) · `text-3xl` 30 (heure de créneau) · `text-4xl`+ (titres de page, en Bebas). Interligne du texte : 1,5.

## 3. Forme, profondeur, mouvement

- Arrondis : `rounded-xs` 2 px (badges, tickets), `rounded-sm` 4 px (boutons, champs, blocs). **Rien au-delà**, pas de `rounded-full`.
- Ombres : `shadow-sm`, `shadow-md` seulement, sobres.
- Interdits : dégradés décoratifs, glassmorphism (`backdrop-blur`), emoji, grandes cartes arrondies génériques.
- Mouvement : transitions de couleur 150 ms ; `prefers-reduced-motion` les neutralise (base CSS).

## 4. Mise en page

- Mobile d'abord, référence 375 px. `Conteneur` : gouttière 16 px (`px-4`), 32 px sur bureau, largeur max 72 rem.
- `Grille` : 4 colonnes sur mobile, 12 à partir de `lg` (1024 px). Les enfants choisissent `col-span-4 lg:col-span-6`, etc.
- Points de contrôle : 375, 768, 1024, 1440 px. Aucun défilement horizontal.

## 5. Accessibilité (non négociable)

- Contraste AA (tableau ci-dessus).
- Libellé visible au-dessus de chaque champ, jamais un placeholder seul (`Champ`).
- Focus visible : contour 2 px `primary` décalé de 2 px ; en `accent` dans un conteneur `.sur-sombre`.
- Erreurs : `role="alert"`, icône **et** texte, couleur brique (`Alerte`). Confirmations : `role="status"` (`Confirmation`).
- Cibles tactiles de 44 × 44 px minimum (`min-h-11 min-w-11` sur `Bouton`, `LienBouton`, `Champ`).
- Icônes décoratives `aria-hidden` ; le sens est toujours porté par un texte.
- Statuts : texte en casse normale dans le DOM, capitales par CSS (`uppercase`), pour les lecteurs d'écran.

## 5 bis. Styles de base des éléments HTML (`@layer base`)

Le preflight de Tailwind retire tout le style natif. Pour qu'une page soit utilisable **avant même d'utiliser les composants**, `src/index.css` donne aux éléments nus un style lisible, uniquement avec les tokens :

- `main` : une colonne centrée de 28 rem max, gouttière de 16 px, espacement de 16 px.
- `h1`/`h2` : Bebas Neue, `primary`. `a` : souligné 2 px, `primary`. Un lien seul dans son paragraphe (« ← Terrains ») fait 44 px de haut.
- `form` en colonne ; `label` en bloc au-dessus du champ ; une case à cocher reste sur la ligne de son libellé (ligne de 44 px cliquable).
- `input`, `select`, `textarea` : fond `surface`, bordure 2 px **`muted`** (et non `line` : la bordure d'un champ doit atteindre 3:1, `line` n'en fait que 1,42), 44 px, 16 px de texte.
- `button` : fond `action`, texte `surface`, 44 px ; dans l'en-tête, contour blanc sur vert.
- `ul`/`li` dans `main` : une rangée blanche par élément (48 px) ; un lien seul occupe la rangée, la dernière action se cale à droite.
- `[role=alert]` / `[role=status]` : couleurs `error` / `confirm` avec une icône en masque CSS (`data:`, autorisé par `img-src`), absente du texte lu. Les composants `Alerte`/`Confirmation` ont leur propre SVG : pas de doublon.

Toute classe utilitaire (donc tout composant de `src/ui`) remplace ces règles, car le calque `utilities` passe avant `base`.

Vérification visuelle : captures Playwright à 375 et 1440 px de Connexion, Inscription, Terrains, Créneaux, Réservation et page du match, avec contrôle automatique de l'absence de défilement horizontal et des cibles de moins de 44 px. `src/libelles.test.tsx` vérifie que chaque champ de chaque page a un libellé.

## 6. Composants (`src/ui/`)

| Composant | Rôle |
|---|---|
| `Bouton` | Variantes `action` (rouge), `primaire` (vert), `secondaire` (contour), `discret` (lien). `type="button"` par défaut |
| `LienBouton` | `Link` de react-router avec l'apparence d'un bouton (Réserver, Commencer) |
| `Champ` | Label englobant + input 44 px, aide et erreur reliées par `aria-describedby`, `aria-invalid` |
| `Alerte` / `Confirmation` | Messages `alert` / `status` avec icône |
| `BadgeStatut` | `confirme` (vert, check) · `attente` (ambre, horloge) · `annule` (contour brique, croix). Libellé remplaçable |
| `TicketPrix` | Ticket ambre à talon perforé, montant via `formatMontant`, option « / heure » |
| `CreneauAffichage` | Rangée tableau d'affichage : heures en Bebas sur vert, ticket de prix, action ou « Indisponible » |
| `Conteneur` / `Grille` | Gouttières et grille 4/12 |
| `Icone` | SVG inline : `check`, `horloge`, `croix`, `alerte`, `fleche` |

### Anatomie des composants signature

```
Créneau (tableau d'affichage)
┌──────────────────────────────────────────────────────────┐
│ ▐19:00 – 20:00▌  ┆150,00 MAD / heure   [ RÉSERVER ]      │
│  Bebas 30, vert   ticket ambre          bouton action 44px│
└──────────────────────────────────────────────────────────┘
Badge :  [✓ MATCH CONFIRMÉ]  [◷ EN ATTENTE]  [✕ ANNULÉ]
```

## 7. Contraintes techniques (CSP)

`default-src 'self'; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'; base-uri 'self'` :
aucune police, image, script ou style externe ; pas d'attribut `style` ni de `dangerouslySetInnerHTML`. Le test `src/ui/gardeFou.test.ts` vérifie ces règles (et les interdits du §3) sur tout le code source.

## 8. Checklist avant livraison

- [ ] Aucune couleur hors tokens, aucun hex dans un composant
- [ ] Contrastes du §1 respectés pour toute nouvelle combinaison
- [ ] Focus visible au clavier sur chaque élément interactif
- [ ] Cibles de 44 px, 8 px d'espace entre deux cibles
- [ ] Erreurs en `alert` avec icône et texte, confirmations en `status`
- [ ] Rendu vérifié à 375, 768, 1024 et 1440 px, sans défilement horizontal
- [ ] `prefers-reduced-motion` respecté
- [ ] `npx tsc -b`, `npm run lint`, `npm test`, `npm run build` verts
