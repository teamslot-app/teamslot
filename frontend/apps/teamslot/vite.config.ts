/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'

const frontend = fileURLToPath(new URL('../..', import.meta.url))
const shared = fileURLToPath(new URL('../../packages/shared/src/index.ts', import.meta.url))

// Nouveau front TeamSlot (repris de zéro). Même règles que apps/web : client d'API généré par alias,
// /api renvoyé vers la passerelle en dev, rien chargé depuis un autre site (CSP « default-src 'self' »).
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@teamslot/shared': shared },
  },
  server: {
    port: 5174, // 5173 reste à apps/web : les deux fronts peuvent tourner en même temps
    fs: { allow: [frontend] },
    proxy: {
      // Par défaut la passerelle locale (docker compose). Pour tester contre le staging via Tailscale :
      //   API_CIBLE=http://100.89.116.27:8080 npm run dev
      '/api': process.env.API_CIBLE ?? 'http://localhost:8080',
    },
  },
  build: {
    // three.js (~920 ko, 245 ko gzip) vit dans son propre morceau, chargé à la demande sur l'accueil seulement
    chunkSizeWarningLimit: 1000,
    // drapeaux (SCRUM-135) : un fichier par pays, jamais intégré au JavaScript ; seul le drapeau affiché est chargé
    assetsInlineLimit: (fichier) => (fichier.includes('flag-icons') ? false : undefined),
  },
  test: {
    environment: 'jsdom',
  },
})
