import { defineConfig } from "vitest/config"
import react from "@vitejs/plugin-react"

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Todas las peticiones /api/* se redirigen al gateway
      // Esto hace que api.js use "/api" como baseURL (relativa)
      // sin necesidad de especificar "http://localhost:8080"
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
      }
    }
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setup.js",
    css: false,
    coverage: {
      provider: "v8",
      thresholds: {
        lines: 91,
        branches: 70,
        functions: 86,
        statements: 90,
      },
    },
  },
})
