import { useEffect, useMemo, useState } from 'react';
import type { CSSProperties } from 'react';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';

// ─── Τύποι ────────────────────────────────────────────────────────────────────

interface UserRow {
  id: number;
  username: string;
  fullName: string;
  email?: string | null;
  role: 'ADMIN' | 'TEACHER' | 'STUDENT';
  sector?: string | null;
  teacherId?: number | null;
  active: boolean;
  createdAt?: string | null;
}

interface TeacherOption {
  id: number;
  name: string;
}

// ─── Σταθερές εμφάνισης (ίδιο design language με την υπόλοιπη εφαρμογή) ───────

const ROLE_CONFIG: Record<UserRow['role'], { label: string; color: string }> = {
  ADMIN:   { label: 'ADMIN',   color: '#3b82f6' },
  TEACHER: { label: 'TEACHER', color: '#10b981' },
  STUDENT: { label: 'STUDENT', color: '#8b5cf6' },
};

const ROLE_FILTERS = ['ALL', 'ADMIN', 'TEACHER', 'STUDENT'] as const;
type RoleFilter = typeof ROLE_FILTERS[number];

const card: CSSProperties = {
  background: '#0d1b2e',
  border: '1px solid #1a2744',
  borderRadius: 10,
};

const inputStyle: CSSProperties = {
  width: '100%', padding: '8px 12px', borderRadius: 8,
  border: '1px solid #1a2744', background: '#080f1a',
  color: '#e2e8f0', fontSize: 13, boxSizing: 'border-box',
  fontFamily: "'IBM Plex Sans', sans-serif", outline: 'none',
};

const labelStyle: CSSProperties = {
  display: 'block', fontSize: 11, color: '#94a3b8', marginBottom: 5,
};

const btnPrimary: CSSProperties = {
  padding: '8px 16px', borderRadius: 8, border: '1px solid #2563eb',
  background: '#1d4ed8', color: '#fff', fontSize: 13, fontWeight: 600,
  cursor: 'pointer', fontFamily: "'IBM Plex Sans', sans-serif",
};

const btnGhost: CSSProperties = {
  padding: '5px 10px', borderRadius: 6, border: '1px solid #1a2744',
  background: 'transparent', color: '#94a3b8', fontSize: 11,
  cursor: 'pointer', fontFamily: "'IBM Plex Sans', sans-serif",
};

function getErrorMessage(err: unknown): string {
  const anyErr = err as { response?: { data?: { error?: string } } };
  return anyErr?.response?.data?.error ?? 'Κάτι πήγε στραβά. Δοκίμασε ξανά.';
}

const EMPTY_FORM = {
  username: '', password: '', fullName: '', email: '',
  role: 'STUDENT' as UserRow['role'], sector: '', teacherId: '' as string,
};

// ─── Σελίδα ───────────────────────────────────────────────────────────────────

export default function Users() {
  const { user: currentUser } = useAuth();

  const [users, setUsers] = useState<UserRow[]>([]);
  const [teachers, setTeachers] = useState<TeacherOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [roleFilter, setRoleFilter] = useState<RoleFilter>('ALL');
  const [search, setSearch] = useState('');

  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [saving, setSaving] = useState(false);

  // ── Φόρτωση δεδομένων ──────────────────────────────────────────────────────
  async function load() {
    setLoading(true);
    setError('');
    try {
      const [usersRes, teachersRes] = await Promise.all([
        api.get<UserRow[]>('/users'),
        api.get<TeacherOption[]>('/teachers'),
      ]);
      setUsers(usersRes.data);
      setTeachers(teachersRes.data.map(t => ({ id: t.id, name: t.name })));
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  function flashSuccess(msg: string) {
    setSuccess(msg);
    setTimeout(() => setSuccess(''), 3000);
  }

  // ── Δημιουργία χρήστη ──────────────────────────────────────────────────────
  async function handleCreate() {
    setError('');
    if (!form.username.trim() || !form.password || !form.fullName.trim()) {
      setError('Συμπλήρωσε username, κωδικό και ονοματεπώνυμο.');
      return;
    }
    if (form.password.length < 6) {
      setError('Ο κωδικός πρέπει να έχει τουλάχιστον 6 χαρακτήρες.');
      return;
    }
    setSaving(true);
    try {
      await api.post('/users', {
        username: form.username.trim(),
        password: form.password,
        fullName: form.fullName.trim(),
        email: form.email.trim() || null,
        role: form.role,
        sector: form.sector.trim() || null,
        teacherId: form.role === 'TEACHER' && form.teacherId ? Number(form.teacherId) : null,
      });
      setForm({ ...EMPTY_FORM });
      setShowCreate(false);
      flashSuccess('Ο λογαριασμός δημιουργήθηκε.');
      await load();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  // ── Ενέργειες γραμμής ──────────────────────────────────────────────────────
  async function toggleActive(u: UserRow) {
    setError('');
    try {
      await api.put(`/users/${u.id}`, { active: !u.active });
      flashSuccess(u.active ? `Ο χρήστης ${u.username} απενεργοποιήθηκε.` : `Ο χρήστης ${u.username} ενεργοποιήθηκε.`);
      await load();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  async function resetPassword(u: UserRow) {
    const next = window.prompt(`Νέος κωδικός για τον χρήστη «${u.username}» (τουλάχιστον 6 χαρακτήρες):`);
    if (next === null) return;
    if (next.length < 6) {
      setError('Ο κωδικός πρέπει να έχει τουλάχιστον 6 χαρακτήρες.');
      return;
    }
    setError('');
    try {
      await api.put(`/users/${u.id}/password`, { password: next });
      flashSuccess(`Ο κωδικός του ${u.username} ενημερώθηκε.`);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  async function removeUser(u: UserRow) {
    if (!window.confirm(`Σίγουρα θέλεις να διαγράψεις τον λογαριασμό «${u.username}»;\n\nΣυμβουλή: αν ο χρήστης έχει ιστορικό στο σύστημα, προτίμησε απενεργοποίηση.`)) return;
    setError('');
    try {
      await api.delete(`/users/${u.id}`);
      flashSuccess(`Ο λογαριασμός ${u.username} διαγράφηκε.`);
      await load();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  async function linkTeacher(u: UserRow, teacherId: string) {
    setError('');
    try {
      await api.put(`/users/${u.id}`, { teacherId: teacherId ? Number(teacherId) : null });
      flashSuccess(`Η σύνδεση καθηγητή για τον ${u.username} ενημερώθηκε.`);
      await load();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  // ── Φιλτράρισμα ────────────────────────────────────────────────────────────
  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return users
      .filter(u => roleFilter === 'ALL' || u.role === roleFilter)
      .filter(u =>
        !q ||
        u.username.toLowerCase().includes(q) ||
        u.fullName.toLowerCase().includes(q) ||
        (u.email ?? '').toLowerCase().includes(q)
      )
      .sort((a, b) => a.username.localeCompare(b.username, 'el'));
  }, [users, roleFilter, search]);

  const counts = useMemo(() => ({
    ALL: users.length,
    ADMIN: users.filter(u => u.role === 'ADMIN').length,
    TEACHER: users.filter(u => u.role === 'TEACHER').length,
    STUDENT: users.filter(u => u.role === 'STUDENT').length,
  }), [users]);

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div style={{ maxWidth: 1100, margin: '0 auto', padding: '28px 20px 60px', fontFamily: "'IBM Plex Sans', sans-serif" }}>

      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20, flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h1 style={{ margin: 0, fontSize: 20, fontWeight: 700, color: '#f1f5f9' }}>Διαχείριση Χρηστών</h1>
          <p style={{ margin: '4px 0 0', fontSize: 12, color: '#64748b' }}>
            Δημιουργία και διαχείριση λογαριασμών καθηγητών και φοιτητών
          </p>
        </div>
        <button style={btnPrimary} onClick={() => { setShowCreate(v => !v); setError(''); }}>
          {showCreate ? 'Άκυρο' : '+ Νέος λογαριασμός'}
        </button>
      </div>

      {/* Μηνύματα */}
      {error && (
        <div style={{ ...card, borderColor: '#7f1d1d', background: '#7f1d1d22', padding: '10px 14px', marginBottom: 14, fontSize: 13, color: '#fca5a5' }}>
          {error}
        </div>
      )}
      {success && (
        <div style={{ ...card, borderColor: '#14532d', background: '#14532d22', padding: '10px 14px', marginBottom: 14, fontSize: 13, color: '#86efac' }}>
          {success}
        </div>
      )}

      {/* Φόρμα δημιουργίας */}
      {showCreate && (
        <div style={{ ...card, padding: 18, marginBottom: 20 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: '#e2e8f0', marginBottom: 14 }}>Νέος λογαριασμός</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
            <div>
              <label style={labelStyle}>Username *</label>
              <input style={inputStyle} value={form.username}
                     onChange={e => setForm(f => ({ ...f, username: e.target.value }))} placeholder="π.χ. ppapadopoulos" />
            </div>
            <div>
              <label style={labelStyle}>Κωδικός * (min 6)</label>
              <input style={inputStyle} type="password" value={form.password}
                     onChange={e => setForm(f => ({ ...f, password: e.target.value }))} placeholder="••••••" />
            </div>
            <div>
              <label style={labelStyle}>Ονοματεπώνυμο *</label>
              <input style={inputStyle} value={form.fullName}
                     onChange={e => setForm(f => ({ ...f, fullName: e.target.value }))} placeholder="π.χ. Παπαδόπουλος Ιωάννης" />
            </div>
            <div>
              <label style={labelStyle}>Email</label>
              <input style={inputStyle} value={form.email}
                     onChange={e => setForm(f => ({ ...f, email: e.target.value }))} placeholder="user@upatras.gr" />
            </div>
            <div>
              <label style={labelStyle}>Ρόλος *</label>
              <select style={inputStyle} value={form.role}
                      onChange={e => setForm(f => ({ ...f, role: e.target.value as UserRow['role'] }))}>
                <option value="STUDENT">STUDENT — Φοιτητής</option>
                <option value="TEACHER">TEACHER — Καθηγητής</option>
                <option value="ADMIN">ADMIN — Διαχειριστής</option>
              </select>
            </div>
            {form.role === 'TEACHER' && (
              <>
                <div>
                  <label style={labelStyle}>Σύνδεση με καθηγητή (για διαθεσιμότητα)</label>
                  <select style={inputStyle} value={form.teacherId}
                          onChange={e => setForm(f => ({ ...f, teacherId: e.target.value }))}>
                    <option value="">— Χωρίς σύνδεση —</option>
                    {teachers.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                  </select>
                </div>
                <div>
                  <label style={labelStyle}>Τομέας</label>
                  <select style={inputStyle} value={form.sector}
                          onChange={e => setForm(f => ({ ...f, sector: e.target.value }))}>
                    <option value="">—</option>
                    <option value="ΕΘ">ΕΘ — Εφαρμογών &amp; Θεμελιώσεων</option>
                    <option value="ΛΥ">ΛΥ — Λογισμικού</option>
                    <option value="ΥΑ">ΥΑ — Υλικού &amp; Αρχιτεκτονικής</option>
                  </select>
                </div>
              </>
            )}
          </div>
          <div style={{ marginTop: 14, display: 'flex', gap: 10 }}>
            <button style={{ ...btnPrimary, opacity: saving ? 0.6 : 1 }} disabled={saving} onClick={handleCreate}>
              {saving ? 'Αποθήκευση…' : 'Δημιουργία'}
            </button>
          </div>
        </div>
      )}

      {/* Φίλτρα */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 14, flexWrap: 'wrap', alignItems: 'center' }}>
        {ROLE_FILTERS.map(rf => {
          const active = roleFilter === rf;
          const color = rf === 'ALL' ? '#64748b' : ROLE_CONFIG[rf].color;
          return (
            <button key={rf} onClick={() => setRoleFilter(rf)} style={{
              ...btnGhost,
              borderColor: active ? color : '#1a2744',
              color: active ? color : '#94a3b8',
              background: active ? `${color}15` : 'transparent',
              fontWeight: active ? 700 : 400,
            }}>
              {rf === 'ALL' ? 'Όλοι' : ROLE_CONFIG[rf].label} ({counts[rf]})
            </button>
          );
        })}
        <input
          style={{ ...inputStyle, width: 220, marginLeft: 'auto' }}
          placeholder="Αναζήτηση…"
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </div>

      {/* Πίνακας */}
      <div style={{ ...card, overflow: 'hidden' }}>
        {loading ? (
          <div style={{ padding: 30, textAlign: 'center', color: '#64748b', fontSize: 13 }}>Φόρτωση…</div>
        ) : filtered.length === 0 ? (
          <div style={{ padding: 30, textAlign: 'center', color: '#64748b', fontSize: 13 }}>Δεν βρέθηκαν χρήστες.</div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #1a2744' }}>
                {['Χρήστης', 'Ρόλος', 'Email', 'Σύνδεση καθηγητή', 'Κατάσταση', 'Ενέργειες'].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '10px 14px', fontSize: 10, color: '#64748b', fontWeight: 600, letterSpacing: '0.6px', textTransform: 'uppercase' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map(u => {
                const rc = ROLE_CONFIG[u.role];
                const isSelf = currentUser?.username === u.username;
                return (
                  <tr key={u.id} style={{ borderBottom: '1px solid #14203a' }}>
                    <td style={{ padding: '10px 14px' }}>
                      <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 12, color: '#e2e8f0' }}>
                        {u.username}{isSelf && <span style={{ color: '#64748b', fontSize: 10 }}> (εσύ)</span>}
                      </div>
                      <div style={{ fontSize: 11, color: '#94a3b8' }}>{u.fullName}</div>
                    </td>
                    <td style={{ padding: '10px 14px' }}>
                      <span style={{
                        fontSize: 9, padding: '2px 7px', borderRadius: 3, fontWeight: 700,
                        background: `${rc.color}22`, color: rc.color, border: `1px solid ${rc.color}44`,
                        fontFamily: 'JetBrains Mono, monospace', letterSpacing: '0.5px',
                      }}>{rc.label}</span>
                      {u.sector && <span style={{ marginLeft: 6, fontSize: 10, color: '#64748b' }}>{u.sector}</span>}
                    </td>
                    <td style={{ padding: '10px 14px', color: '#94a3b8', fontSize: 12 }}>{u.email ?? '—'}</td>
                    <td style={{ padding: '10px 14px' }}>
                      {u.role === 'TEACHER' ? (
                        <select
                          style={{ ...inputStyle, width: 180, padding: '5px 8px', fontSize: 12 }}
                          value={u.teacherId ?? ''}
                          onChange={e => linkTeacher(u, e.target.value)}
                        >
                          <option value="">— Χωρίς σύνδεση —</option>
                          {teachers.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                        </select>
                      ) : <span style={{ color: '#334155', fontSize: 12 }}>—</span>}
                    </td>
                    <td style={{ padding: '10px 14px' }}>
                      <span style={{
                        fontSize: 10, padding: '2px 8px', borderRadius: 10, fontWeight: 600,
                        background: u.active ? '#14532d33' : '#7f1d1d33',
                        color: u.active ? '#4ade80' : '#f87171',
                        border: `1px solid ${u.active ? '#14532d' : '#7f1d1d'}`,
                      }}>
                        {u.active ? 'Ενεργός' : 'Ανενεργός'}
                      </span>
                    </td>
                    <td style={{ padding: '10px 14px', whiteSpace: 'nowrap' }}>
                      <button style={{ ...btnGhost, marginRight: 6, opacity: isSelf ? 0.4 : 1 }} disabled={isSelf}
                              title={isSelf ? 'Δεν μπορείς να απενεργοποιήσεις τον εαυτό σου' : ''}
                              onClick={() => toggleActive(u)}>
                        {u.active ? 'Απενεργοποίηση' : 'Ενεργοποίηση'}
                      </button>
                      <button style={{ ...btnGhost, marginRight: 6 }} onClick={() => resetPassword(u)}>
                        Reset κωδικού
                      </button>
                      <button style={{ ...btnGhost, color: '#f87171', borderColor: '#7f1d1d', opacity: isSelf ? 0.4 : 1 }} disabled={isSelf}
                              onClick={() => removeUser(u)}>
                        Διαγραφή
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}