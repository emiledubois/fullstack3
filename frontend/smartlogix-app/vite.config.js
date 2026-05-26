import { defineConfig } from "vite"
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
  }
})
