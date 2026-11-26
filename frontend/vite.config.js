import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发服务器：5173；/api 请求代理到 airline-cs 后端（8081），免 CORS
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
