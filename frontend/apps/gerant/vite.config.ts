/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'

const frontend = fileURLToPath(new URL('../..', import.meta.url))
const shared = fileURLToPath(new URL('../../packages/shared/src/index.ts', import.meta.url))

// Plateforme propriétaire TeamSlot (SCRUM-140). Mêmes règles que apps/teamslot : client d'API généré par alias,
// /api renvoyé vers la passerelle en dev, rien chargé depuis un autre site (CSP « default-src 'self' »).
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@teamslot/shared': shared },
  },
  server: {
    port: 5175, // 5173 : apps/web · 5174 : plateforme joueur · 5175 : plateforme propriétaire
    fs: { allow: [frontend] },
    proxy: {
      // Par défaut la passerelle locale (docker compose). Pour tester contre le staging via Tailscale :
      //   API_CIBLE=http://100.89.116.27:8080 npm run dev
      '/api': process.env.API_CIBLE ?? 'http://localhost:8080',
    },
  },
  build: {
    // drapeaux (SCRUM-135) : un fichier par pays, jamais intégré au JavaScript ; seul le drapeau affiché est chargé
    assetsInlineLimit: (fichier) => (fichier.includes('flag-icons') ? false : undefined),
  },
  test: {
    environment: 'jsdom',
  },
})
