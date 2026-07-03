import { describe, it, expect, afterEach, beforeEach } from 'vitest';
import { render, cleanup, waitFor } from '@testing-library/react';
import App from './App';

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
