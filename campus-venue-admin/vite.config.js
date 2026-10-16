import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
// 后端固定为 Spring Boot 服务（campus-venue-server，默认 http://localhost:8080）。
// 需要换地址/端口时用环境变量覆盖：VITE_API_TARGET=http://192.168.1.100:8080 npm run dev
const API_TARGET = process.env.VITE_API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    // 监听所有网卡：局域网内其它设备（手机/平板/另一台电脑）可通过 http://<本机IP>:5173 并发访问
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    proxy: {
      // 契约：前端一律通过 Vite 代理 /api 访问 Spring Boot 后端，禁止硬编码域名
      '/api': {
        target: API_TARGET,
        changeOrigin: true
      },
      // 消息中心 WebSocket（WS /ws/notice?token=xxx）
      '/ws': {
        target: API_TARGET,
        changeOrigin: true,
        ws: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1600
  }
})
