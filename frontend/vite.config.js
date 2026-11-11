import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [react(), tailwindcss()],
  // 운영 빌드에서는 디버그용 console.log/info/debug 호출을 제거한다 (warn·error는 남김)
  esbuild: mode === 'production' ? { pure: ['console.log', 'console.info', 'console.debug'] } : {},
  build: {
    rollupOptions: {
      output: {
        // 첫 화면 번들의 85%가 React·라우터·axios다. 앱 코드와 나눠 두면 배포해도 이 부분은 캐시를 그대로 쓴다.
        // 패키지 이름을 정확히 맞춘다 (에디터·DOMPurify처럼 필요할 때 받는 패키지가 섞여 들어오지 않게)
        manualChunks(id) {
          const pkg = id.match(/node_modules[\\/]((?:@[^\\/]+[\\/])?[^\\/]+)/)?.[1]?.replace('\\', '/');
          if (['react', 'react-dom', 'scheduler', 'react-router', 'react-router-dom', 'cookie', 'set-cookie-parser'].includes(pkg)) return 'react';
          if (pkg === 'axios') return 'axios';
        },
      },
    },
  },
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
