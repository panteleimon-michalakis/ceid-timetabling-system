import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate   = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error,    setError]    = useState('');
  const [loading,  setLoading]  = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!username || !password) { setError('Συμπλήρωσε username και κωδικό.'); return; }
    setLoading(true);
    setError('');
    try {
      await login(username, password);
      navigate('/');
    } catch {
      setError('Λάθος username ή κωδικός.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{
      minHeight: '100vh', background: '#080f1a',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontFamily: "'IBM Plex Sans', sans-serif",
    }}>
      <style>{`@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;400;500;600&family=JetBrains+Mono:wght@400;500&display=swap');`}</style>

      <div style={{
        background: '#0d1b2e', border: '1px solid #1a2744',
        borderRadius: 14, padding: '40px 44px', width: 400,
      }}>
        {/* Logo / header */}
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{
            width: 48, height: 48, borderRadius: 12,
            background: 'linear-gradient(135deg, #1d4ed8, #3b82f6)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 22, margin: '0 auto 14px',
          }}>⊞</div>
          <div style={{ fontSize: 18, fontWeight: 600, color: '#f1f5f9', marginBottom: 4 }}>
            Ωρολόγιο ΤΜΗΥΠ
          </div>
          <div style={{ fontSize: 12, color: '#475569' }}>
            Πανεπιστήμιο Πατρών
          </div>
        </div>

        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: 16 }}>
            <label style={{ display: 'block', fontSize: 12, color: '#94a3b8', marginBottom: 6 }}>
              Username
            </label>
            <input
              value={username}
              onChange={e => setUsername(e.target.value)}
              placeholder="Εισάγετε username"
              autoComplete="username"
              style={{
                width: '100%', padding: '10px 14px', borderRadius: 8,
                border: '1px solid #1a2744', background: '#080f1a',
                color: '#e2e8f0', fontSize: 14, boxSizing: 'border-box' as const,
                fontFamily: "'IBM Plex Sans', sans-serif",
                outline: 'none',
              }}
            />
          </div>

          <div style={{ marginBottom: 24 }}>
            <label style={{ display: 'block', fontSize: 12, color: '#94a3b8', marginBottom: 6 }}>
              Κωδικός
            </label>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="••••••••"
              autoComplete="current-password"
              style={{
                width: '100%', padding: '10px 14px', borderRadius: 8,
                border: '1px solid #1a2744', background: '#080f1a',
                color: '#e2e8f0', fontSize: 14, boxSizing: 'border-box' as const,
                fontFamily: "'IBM Plex Sans', sans-serif",
                outline: 'none',
              }}
            />
          </div>

          {error && (
            <div style={{
              background: '#450a0a', border: '1px solid #7f1d1d',
              borderRadius: 8, padding: '10px 14px',
              color: '#f87171', fontSize: 13, marginBottom: 16,
            }}>
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            style={{
              width: '100%', padding: '11px', borderRadius: 8, border: 'none',
              background: loading ? '#1e3a5f' : '#1d4ed8',
              color: '#fff', fontSize: 14, fontWeight: 600,
              cursor: loading ? 'not-allowed' : 'pointer',
              fontFamily: "'IBM Plex Sans', sans-serif",
              transition: 'background 0.15s',
            }}
          >
            {loading ? 'Σύνδεση...' : 'Σύνδεση'}
          </button>
        </form>

        {/* Demo credentials panel — ορατό ΜΟΝΟ σε development mode (npm run dev).
            Κρύβεται αυτόματα στο production build (npm run build). */}
        {import.meta.env.DEV && (
          <div style={{
            marginTop: 24, padding: '12px 14px',
            background: '#080f1a', borderRadius: 8, border: '1px solid #1a2744',
          }}>
            <div style={{ fontSize: 10, color: '#475569', fontFamily: 'JetBrains Mono, monospace',
              textTransform: 'uppercase', letterSpacing: '0.8px', marginBottom: 8 }}>
              Demo Accounts (dev only)
            </div>
            {[
              { u: 'admin',   p: 'admin123',   r: 'ADMIN',   c: '#3b82f6' },
              { u: 'teacher', p: 'teacher123', r: 'TEACHER', c: '#10b981' },
              { u: 'student', p: 'student123', r: 'STUDENT', c: '#8b5cf6' },
            ].map(a => (
              <div key={a.u}
                onClick={() => { setUsername(a.u); setPassword(a.p); }}
                style={{
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  padding: '5px 0', cursor: 'pointer', borderBottom: '1px solid #0f1f38',
                }}>
                <span style={{ fontSize: 12, color: '#94a3b8', fontFamily: 'JetBrains Mono, monospace' }}>
                  {a.u} / {a.p}
                </span>
                <span style={{
                  fontSize: 9, padding: '2px 7px', borderRadius: 3,
                  background: `${a.c}22`, color: a.c, fontWeight: 600,
                  fontFamily: 'JetBrains Mono, monospace',
                }}>{a.r}</span>
              </div>
            ))}
            <div style={{ fontSize: 10, color: '#334155', marginTop: 6 }}>
              Κλικ για αυτόματη συμπλήρωση
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
