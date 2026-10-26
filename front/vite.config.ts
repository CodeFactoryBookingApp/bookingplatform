import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// The backend has no CORS (SecurityConfig), so the browser must call /api on its own origin.
// In dev Vite proxies it; in production vercel.json rewrites it. No key ever lives in the front.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_API_TARGET || 'https://bookingplatform-81wi.onrender.com'
  return {
    plugins: [react(), tailwindcss()],
    resolve: { alias: { '@': path.resolve(import.meta.dirname, 'src') } },
    server: {
      // Supabase email links point to the Site URL (localhost:3000), see docs/operations/runbook.md
      port: 3000,
      strictPort: true,
      proxy: { '/api': { target, changeOrigin: true, secure: true, timeout: 150_000, proxyTimeout: 150_000 } },
    },
    preview: { port: 3000, proxy: { '/api': { target, changeOrigin: true, secure: true } } },
  }
})
