# Plateforme propriétaire TeamSlot (`apps/gerant`)

Décision d'équipe du 13/11 : **deux fronts**, sur **le même backend et les mêmes comptes**.

| | Plateforme joueur | Plateforme propriétaire |
|---|---|---|
| Dossier | `apps/teamslot` | `apps/gerant` (ici) |
| Port en local | 5174 | 5175 |
| Inscription | compte simple | compte simple ; le propriétaire inscrit ensuite son terrain depuis son espace |

Décision du 13/11 : **le dossier du terrain est la demande**. Après l'inscription, le propriétaire arrive sur
« Inscris ton terrain » : il y enverra le dossier (description, localisation, photos, patente ; Sprint 4). Un
administrateur le valide, et c'est seulement alors que le compte reçoit le rôle `VENUE_MANAGER`. Le rôle n'est
**jamais** donné à l'inscription ; tant que le jeton ne le porte pas, l'espace gérant reste fermé, et le serveur
refuse de toute façon les routes gérant (403).

Mêmes règles de sécurité que la plateforme joueur : jetons en mémoire seulement, rien chargé depuis un autre site
(CSP `default-src 'self'`), test `gardeFou.test.ts`.

Les composants communs (formulaire, téléphone avec drapeau, règles du mot de passe…) sont **copiés** depuis
`apps/teamslot` pour l'instant : ils passeront dans un paquet partagé (SCRUM-143). Le déploiement (CI, image, Ingress,
scan ZAP) est SCRUM-142 ; les écrans de l'espace gérant, SCRUM-141.

## Lancer

```bash
cd frontend/packages/shared && npm ci     # client d'API généré, une seule fois
cd ../../apps/gerant
npm ci
npm run dev        # http://localhost:5175 (backend : docker compose dans deploy/, passerelle sur 8080)
npm test
npm run lint
npm run build
```

`public/affiche.webp` : même photo que la plateforme joueur (voir `apps/teamslot/README.md` pour son origine).
