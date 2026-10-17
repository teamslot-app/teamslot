# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])

```

You can also install [eslint-plugin-react-x](https://npmx.dev/package/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://npmx.dev/package/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])

```

## Jetons et session (SCRUM-94)

Les jetons (accès et rafraîchissement) sont gardés **en mémoire seulement** (`src/auth/session.ts`) : ni `localStorage`, ni cookie. Un script injecté dans la page (XSS) ne pourrait donc pas les lire dans le stockage du navigateur. **Conséquence : recharger la page demande de se reconnecter.**

Un appel authentifié qui reçoit 401 tente **un seul** rafraîchissement (`POST /api/v1/auth/refresh`), puis est rejoué ; si le rafraîchissement échoue, la session est fermée et l'utilisateur revient à l'écran de connexion. Les routes `/api/v1/auth/*` ne déclenchent jamais ce mécanisme. Les erreurs de connexion donnent toujours le même message (« Téléphone ou mot de passe incorrect ») : il ne dit pas si le compte existe.

## Développer en local

1. À la racine du dépôt, lancer toute l'application : `docker compose -f deploy/docker-compose.yml up -d --build` (la passerelle écoute sur le port 8080).
2. Ici : `npm run dev`. Le serveur de Vite renvoie `/api` vers `http://localhost:8080`.
3. Le client d'API vient de `frontend/packages/shared` (généré depuis `docs/api`, voir SCRUM-69) ; `@teamslot/shared` est un alias dans `vite.config.ts` et `tsconfig.app.json`.
4. L'image Docker se construit depuis `frontend/` : `docker build -f apps/web/Dockerfile frontend`.
