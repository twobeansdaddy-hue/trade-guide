import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiProxyTarget = env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080'

  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true,
        },
        '/oauth2': {
          target: apiProxyTarget,
          changeOrigin: true,
        },
        '/login': {
          target: apiProxyTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
