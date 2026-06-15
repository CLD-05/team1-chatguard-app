import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// 단위 테스트 전용 설정. 빌드(vite.config.js)와 분리해 Tailwind 등 빌드 플러그인의 영향을 받지 않는다.
export default defineConfig({
  plugins: [react()],
  test: {
    include: ['src/**/*.{test,spec}.{js,jsx}'],
    environment: 'jsdom',
    globals: true,
  },
})
