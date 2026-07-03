import { createContext, useContext, useState } from 'react';
import type { ReactNode } from 'react';
import api from '../api/client';

export interface AuthUser {
  username: string;
  fullName: string;
  role: 'ADMIN' | 'TEACHER' | 'STUDENT';
}

interface AuthContextType {
  user: AuthUser | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

// E-2 fix: διάβασμα localStorage ΣΥΓΧΡΟΝΑ μέσα στο useState initializer αντί για
// useEffect — έτσι το isAuthenticated είναι σωστό ΑΠΟ ΤΟ ΠΡΩΤΟ render (καμία SSR
// ανησυχία, καθαρά client-side SPA). Η προηγούμενη async υδάτωση (effect μετά το
// πρώτο commit) άφηνε ένα παράθυρο όπου ένα full-reload σε protected route έβλεπε
// isAuthenticated=false στο πρώτο render → PrivateRoute redirect σε /login →
// αφού ολοκληρωνόταν η υδάτωση, η ίδια η route /login έβλεπε πλέον
// isAuthenticated=true → δεύτερο redirect σε /dashboard, χάνοντας το αρχικό path.
function loadStoredUser(): AuthUser | null {
  const token    = localStorage.getItem('jwt');
  const userData = localStorage.getItem('user');
  if (!token || !userData) return null;
  try {
    const parsed = JSON.parse(userData) as AuthUser;
    api.defaults.headers.common['Authorization'] = `Bearer ${token}`;
    return parsed;
  } catch {
    localStorage.removeItem('jwt');
    localStorage.removeItem('user');
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(loadStoredUser);

  function logout() {
    localStorage.removeItem('jwt');
    localStorage.removeItem('user');
    delete api.defaults.headers.common['Authorization'];
    setUser(null);
  }

  async function login(username: string, password: string) {
    const res = await api.post('/auth/login', { username, password });
    const { token, ...userData } = res.data;
    localStorage.setItem('jwt',  token);
    localStorage.setItem('user', JSON.stringify(userData));
    api.defaults.headers.common['Authorization'] = `Bearer ${token}`;
    setUser(userData as AuthUser);
  }

  return (
    <AuthContext.Provider value={{ user, login, logout, isAuthenticated: !!user }}>
      {children}
    </AuthContext.Provider>
  );
}

// Ο κανόνας react-refresh/only-export-components αφορά αποκλειστικά το Fast Refresh
// (HMR) στο dev — δεν έχει καμία επίδραση στο runtime/production build. Το context
// file εξάγει σκόπιμα και το hook μαζί με τον provider (καθιερωμένο pattern).
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}