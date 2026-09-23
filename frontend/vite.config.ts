import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

const api = { '/api': process.env.VITE_API_TARGET ?? 'http://localhost:8080' }

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // `host` exposes the dev server on the LAN and `allowedHosts` lets an HTTPS tunnel reach it,
  // so a real phone (and KakaoTalk / Threads links) can open it.
  server: { host: true, allowedHosts: true, proxy: api },
  preview: { host: true, allowedHosts: true, proxy: api },
  build: { target: ['es2020', 'safari14', 'chrome87'] },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
})
