import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        secure: false,
      },
      '/upbit-ws': {
        // WSS 프로토콜과 정확한 경로(websocket/v1)를 직접 지정
        target: 'wss://api.upbit.com/websocket/v1',
        ws: true,           // WebSocket 업그레이드 허용
        changeOrigin: true, // Host 헤더를 api.upbit.com 으로 변경
        secure: true,       // TLS 인증서 검증
        rewrite: path => '' // '/upbit-ws' 프리픽스는 제거
      },
      '/ws': {
        target: 'ws://localhost:8090',
        ws: true,
        changeOrigin: true,
      }
    },
  },
});
