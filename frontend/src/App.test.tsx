import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest';
import { render, cleanup, waitFor } from '@testing-library/react';
import App from './App';

// Οι σελίδες πίσω από τα protected routes (π.χ. Rooms) κάνουν fetch στο mount·
// χωρίς mock αυτό είναι πραγματικό network call στο jsdom → unhandled
// AxiosError (exit code 1 στο `vitest run` ακόμα κι όταν όλα τα assertions περνάνε).
vi.mock('./api/client', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: [] })),
    post: vi.fn(() => Promise.resolve({ data: {} })),
    put: vi.fn(() => Promise.resolve({ data: {} })),
    delete: vi.fn(() => Promise.resolve({ data: {} })),
    defaults: { headers: { common: {} as Record<string, string> } },
  },
}));

// E-2 regression: μια πλήρης (φρέσκια) φόρτωση σε ήδη-authenticated session πάνω
// σε protected route έχανε το αρχικό path — η async υδάτωση (useEffect) του
// AuthContext έτρεχε ΜΕΤΑ το πρώτο render, οπότε το PrivateRoute έβλεπε
// isAuthenticated=false στο πρώτο render και έκανε redirect σε /login, μετά η ίδια
// η route /login έβλεπε isAuthenticated=true (μετά την υδάτωση) και ξανα-redirect
// σε /dashboard. Fix: συγχρονισμένη υδάτωση μέσα σε useState initializer.
describe('E-2: full page load on a protected route with a valid session', () => {
  beforeEach(() => {
    localStorage.setItem('jwt', 'fake.jwt.token');
    localStorage.setItem('user', JSON.stringify({ username: 'admin', fullName: 'Admin', role: 'ADMIN' }));
    window.history.pushState({}, '', '/rooms');
  });

  afterEach(() => {
    localStorage.clear();
    window.history.pushState({}, '', '/');
    cleanup();
  });

  it('stays on the original path instead of bouncing through /login to /dashboard', async () => {
    render(<App />);

    await waitFor(() => {
      expect(window.location.pathname).toBe('/rooms');
    });
    expect(window.location.pathname).not.toBe('/dashboard');
  });

  it('still redirects a genuinely unauthenticated visitor to /login', async () => {
    localStorage.clear();
    render(<App />);

    await waitFor(() => {
      expect(window.location.pathname).toBe('/login');
    });
  });
});
