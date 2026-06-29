import { createContext, useContext, useState, useEffect } from 'react';
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

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);

  function logout() {
    localStorage.removeItem('jwt');
    localStorage.removeItem('user');
    delete api.defaults.headers.common['Authorization'];
    setUser(null);
  }

  useEffect(() => {
    const token    = localStorage.getItem('jwt');
    const userData = localStorage.getItem('user');
    if (token && userData) {
      try {
        // Επαναφορά συνεδρίας από localStorage στο mount (συγχρονισμός με εξωτερικό store).
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setUser(JSON.parse(userData));
        api.defaults.headers.common['Authorization'] = `Bearer ${token}`;
      } catch { logout(); }
    }
  }, []);

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