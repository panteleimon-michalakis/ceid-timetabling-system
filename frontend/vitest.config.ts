import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Ξεχωριστό config για τα tests (το vite.config.ts μένει ανέπαφο για το build).
// Χρησιμοποιεί το ίδιο React plugin για JSX/TSX transform + jsdom DOM environment.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: false,
  },
});
