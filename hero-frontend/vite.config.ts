/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  // Tailwind is built locally; the pages load nothing from any other origin.
  plugins: [react(), tailwindcss()],
  server: {
    port: 5171,
    // One origin for the browser: the API is reached at /api on this same host, so a link token
    // never crosses an origin boundary and no cross-origin setup is needed.
    proxy: {
      '/api': {
        target: 'http://localhost:8280',
        changeOrigin: false,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
