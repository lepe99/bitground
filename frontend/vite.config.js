import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [react(), tailwindcss()],
  // 운영 빌드에서는 디버그용 console.log/info/debug 호출을 제거한다 (warn·error는 남김)
  esbuild: mode === 'production' ? { pure: ['console.log', 'console.info', 'console.debug'] } : {},
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        secure: false,
      },
      '/ws': {
        target: 'ws://localhost:8090',
        ws: true,
        changeOrigin: true,
      }
    },
  },
}));
