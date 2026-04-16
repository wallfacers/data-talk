import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import tailwindcss from '@tailwindcss/vite'
import { TanStackRouterVite } from '@tanstack/router-vite-plugin'
import path from 'node:path'

const host = process.env.TAURI_DEV_HOST

export default defineConfig({
  plugins: [
    TanStackRouterVite({
      routesDirectory: './src/routes',
      generatedRouteTree: './src/routeTree.gen.ts',
    }),
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  clearScreen: false,
  server: {
    port: 1420,
    strictPort: true,
    host: host || '0.0.0.0',
    hmr: host ? { protocol: 'ws', host, port: 1421 } : undefined,
    watch: { ignored: ['**/src-tauri/**'] },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  envPrefix: ['VITE_', 'TAURI_ENV_*'],
})
