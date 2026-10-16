/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'

const frontend = fileURLToPath(new URL('../..', import.meta.url))
const shared = fileURLToPath(new URL('../../packages/shared/src/index.ts', import.meta.url))

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    // Le client généré vit dans frontend/packages/shared (SCRUM-69) : pas d'espace de travail npm, un alias suffit.
    alias: { '@teamslot/shared': shared },
  },
  server: {
    // Par défaut, le serveur de dev ne sert que apps/web : on autorise aussi packages/shared.
    fs: { allow: [frontend] },
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
  },
})
