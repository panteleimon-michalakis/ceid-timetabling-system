import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Ξεχωριστό config για τα tests (το vite.config.ts μένει ανέπαφο για το build).
// Χρησιμοποιεί το ίδιο React plugin για JSX/TSX transform + jsdom DOM environment.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: false,
    // Μόνο τα unit tests του src τρέχουν με vitest. Τα e2e/*.spec.ts είναι
    // Playwright (npm run test:e2e) και ΔΕΝ πρέπει να τα σαρώνει το vitest —
    // αλλιώς το test.describe του Playwright σκάει μέσα στο vitest runner.
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
});
