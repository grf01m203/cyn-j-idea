import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// CampusVenue 学生端 Vite 配置
// 后端固定为 Spring Boot 服务（campus-venue-server，默认 http://localhost:8080）。
// - H5 开发/预览：/api 与 /ws 通过 Vite 代理转发到 Spring Boot 后端
// - 小程序端：不使用代理，走 src/config/index.js 中的 BASE_URL 常量
// 换地址/端口：VITE_API_TARGET=http://192.168.1.100:8080 npm run dev:h5
const API_TARGET = process.env.VITE_API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [uni()],
  server: {
    host: '0.0.0.0',
    // 学生端固定使用 5273，避免与同项目其他端（如管理端默认 5173）抢占端口；
    // 如果 5273 被占用，请改这里或加 --port 参数。
    port: 5273,
    strictPort: false,
    proxy: {
      '/api': {
        target: API_TARGET,
        changeOrigin: true
      },
      '/ws': {
        target: API_TARGET,
        changeOrigin: true,
        ws: true
      }
    }
  }
})
