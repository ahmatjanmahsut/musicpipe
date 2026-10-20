import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// MusicPipe 管理控制台构建配置
// server（musicpipe-appservice）会把 web/dist 静态托管到 /，dev 时通过 proxy 联调 :7800
export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: 'dist',
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:7800',
        changeOrigin: true,
      },
    },
  },
})
