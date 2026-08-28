import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    // The Firebase SDK alone puts the single-chunk bundle a bit over Vite's
    // default 500kB warning threshold — expected at this stage (Milestone 1,
    // no route-level code-splitting yet), not a sign of a real problem.
    // Revisit with dynamic imports if/when the app grows further.
    chunkSizeWarningLimit: 900,
  },
})
