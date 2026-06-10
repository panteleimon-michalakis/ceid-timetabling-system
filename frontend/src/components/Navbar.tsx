import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const ALL_NAV_LINKS = [
  { to: '/',          label: 'Επισκόπηση',  icon: '⊞', roles: ['ADMIN','TEACHER','STUDENT'] },
  { to: '/timetable', label: 'Εβδομαδιαίο', icon: '▦', roles: ['ADMIN','TEACHER'] },
  { to: '/exams',     label: 'Εξεταστική',  icon: '◫', roles: ['ADMIN','TEACHER'] },
  { to: '/courses',   label: 'Μαθήματα',    icon: '≡', roles: ['ADMIN','TEACHER','STUDENT'] },
  { to: '/rooms',     label: 'Αίθουσες',    icon: '□', roles: ['ADMIN','TEACHER'] },
  { to: '/teachers',  label: 'Καθηγητές',   icon: '◈', roles: ['ADMIN','TEACHER'] },
  { to: '/view',      label: 'Προβολή',     icon: '⊙', roles: ['ADMIN','TEACHER','STUDENT'] },
  { to: '/users',     label: 'Χρήστες',     icon: '◉', roles: ['ADMIN'] },
];

const ROLE_CONFIG = {
  ADMIN:   { label: 'ADMIN',   color: '#3b82f6' },
  TEACHER: { label: 'TEACHER', color: '#10b981' },
  STUDENT: { label: 'STUDENT', color: '#8b5cf6' },
};

// ─── User badge ───────────────────────────────────────────────────────────────
function UserBadge() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  if (!user) return null;

  const rc = ROLE_CONFIG[user.role] ?? ROLE_CONFIG.STUDENT;

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      {/* Role badge */}
      <span style={{
        fontSize: 9, padding: '2px 7px', borderRadius: 3, fontWeight: 700,
        background: `${rc.color}22`, color: rc.color, border: `1px solid ${rc.color}44`,
        fontFamily: 'JetBrains Mono, monospace', letterSpacing: '0.5px',
      }}>{rc.label}</span>

      {/* Full name */}
      <span style={{
        fontSize: 11, color: '#94a3b8',
        maxWidth: 130, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
      }}>
        {user.fullName}
      </span>

      {/* Logout */}
      <button
        onClick={() => { logout(); navigate('/login'); }}
        style={{
          padding: '3px 10px', border: '1px solid #1a2744', borderRadius: 5,
          background: 'transparent', color: '#64748b', fontSize: 11, cursor: 'pointer',
          fontFamily: "'IBM Plex Sans', sans-serif", transition: 'color 0.15s, border-color 0.15s',
        }}
        onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.color = '#f87171'; (e.currentTarget as HTMLButtonElement).style.borderColor = '#7f1d1d'; }}
        onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.color = '#64748b'; (e.currentTarget as HTMLButtonElement).style.borderColor = '#1a2744'; }}
      >
        Αποσύνδεση
      </button>
    </div>
  );
}

// ─── Navbar ───────────────────────────────────────────────────────────────────
export default function Navbar() {
  const location = useLocation();
  const { user } = useAuth();
  const NAV_LINKS = ALL_NAV_LINKS.filter(l =>
    !user || l.roles.includes(user.role)
  );

  return (
    <>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600&family=JetBrains+Mono:wght@400;500&display=swap');
        .ceid-nav{font-family:'IBM Plex Sans',sans-serif;background:#080f1a;border-bottom:1px solid #1a2744;display:flex;align-items:stretch;height:52px;position:sticky;top:0;z-index:100;user-select:none;}
        .ceid-nav-brand{display:flex;align-items:center;gap:10px;padding:0 20px;border-right:1px solid #1a2744;text-decoration:none;min-width:196px;}
        .ceid-nav-brand-logo{width:28px;height:28px;background:linear-gradient(135deg,#1d4ed8,#3b82f6);border-radius:6px;display:flex;align-items:center;justify-content:center;font-family:'JetBrains Mono',monospace;font-size:11px;font-weight:500;color:#fff;flex-shrink:0;}
        .ceid-nav-brand-title{font-size:13px;font-weight:600;color:#f1f5f9;line-height:1;letter-spacing:-0.2px;}
        .ceid-nav-brand-subtitle{font-size:10px;color:#475569;line-height:1;}
        .ceid-nav-links{display:flex;align-items:stretch;flex:1;padding:0 8px;overflow-x:auto;}
        .ceid-nav-link{display:flex;align-items:center;gap:7px;padding:0 13px;text-decoration:none;font-size:13px;font-weight:500;color:#64748b;position:relative;transition:color 0.15s;white-space:nowrap;}
        .ceid-nav-link:hover{color:#94a3b8;}
        .ceid-nav-link.active{color:#e2e8f0;}
        .ceid-nav-link.active::after{content:'';position:absolute;bottom:0;left:13px;right:13px;height:2px;background:#3b82f6;border-radius:2px 2px 0 0;}
        .ceid-nav-right{display:flex;align-items:center;padding:0 16px;border-left:1px solid #1a2744;gap:12px;flex-shrink:0;}
        .ceid-nav-dot{width:6px;height:6px;border-radius:50%;background:#22c55e;}
      `}</style>

      <nav className="ceid-nav">
        {/* Brand */}
        <Link to="/" className="ceid-nav-brand">
          <div className="ceid-nav-brand-logo">ΠΠ</div>
          <div>
            <div className="ceid-nav-brand-title">Ωρολόγιο ΤΜΗΥΠ</div>
            <div className="ceid-nav-brand-subtitle">Πανεπιστήμιο Πατρών</div>
          </div>
        </Link>

        {/* Links */}
        <div className="ceid-nav-links">
          {NAV_LINKS.map(link => (
            <Link key={link.to} to={link.to}
              className={`ceid-nav-link${
                location.pathname === link.to ||
                (link.to !== '/' && location.pathname.startsWith(link.to))
                  ? ' active' : ''
              }`}>
              <i style={{ fontStyle: 'normal', fontSize: 14, opacity: 0.7 }}>{link.icon}</i>
              {link.label}
            </Link>
          ))}
        </div>

        {/* Right: status + user */}
        <div className="ceid-nav-right">
          <div style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 11,
            color: '#475569', fontFamily: 'JetBrains Mono, monospace' }}>
            <div className="ceid-nav-dot" />
            <span style={{ color: '#22c55e' }}>API</span>
          </div>
          <UserBadge />
        </div>
      </nav>
    </>
  );
}
