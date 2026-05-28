import { Link, useLocation } from 'react-router-dom';

const NAV_LINKS = [
  { to: '/',           label: 'Επισκόπηση',  icon: '⊞' },
  { to: '/timetable',  label: 'Εβδομαδιαίο', icon: '▦' },
  { to: '/exams',      label: 'Εξεταστική',  icon: '◫' },
  { to: '/courses',    label: 'Μαθήματα',    icon: '≡' },
  { to: '/rooms',      label: 'Αίθουσες',    icon: '□' },
  { to: '/teachers',   label: 'Καθηγητές',   icon: '◈' },
  { to: '/view',       label: 'Προβολή',      icon: '⊙' },
];

export default function Navbar() {
  const location = useLocation();

  return (
    <>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600&family=JetBrains+Mono:wght@400;500&display=swap');

        .ceid-nav {
          font-family: 'IBM Plex Sans', sans-serif;
          background: #080f1a;
          border-bottom: 1px solid #1a2744;
          display: flex;
          align-items: stretch;
          height: 52px;
          padding: 0;
          position: sticky;
          top: 0;
          z-index: 100;
          user-select: none;
        }

        .ceid-nav-brand {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 0 20px;
          border-right: 1px solid #1a2744;
          text-decoration: none;
          min-width: 200px;
        }

        .ceid-nav-brand-logo {
          width: 28px;
          height: 28px;
          background: linear-gradient(135deg, #1d4ed8 0%, #3b82f6 100%);
          border-radius: 6px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-family: 'JetBrains Mono', monospace;
          font-size: 11px;
          font-weight: 500;
          color: #fff;
          letter-spacing: -0.5px;
          flex-shrink: 0;
        }

        .ceid-nav-brand-text {
          display: flex;
          flex-direction: column;
          gap: 1px;
        }

        .ceid-nav-brand-title {
          font-size: 13px;
          font-weight: 600;
          color: #f1f5f9;
          line-height: 1;
          letter-spacing: -0.2px;
        }

        .ceid-nav-brand-subtitle {
          font-size: 10px;
          font-weight: 400;
          color: #475569;
          line-height: 1;
          letter-spacing: 0.3px;
        }

        .ceid-nav-links {
          display: flex;
          align-items: stretch;
          flex: 1;
          padding: 0 8px;
        }

        .ceid-nav-link {
          display: flex;
          align-items: center;
          gap: 7px;
          padding: 0 14px;
          text-decoration: none;
          font-size: 13px;
          font-weight: 500;
          color: #64748b;
          position: relative;
          transition: color 0.15s;
          letter-spacing: 0.1px;
          white-space: nowrap;
        }

        .ceid-nav-link:hover {
          color: #94a3b8;
        }

        .ceid-nav-link.active {
          color: #e2e8f0;
        }

        .ceid-nav-link.active::after {
          content: '';
          position: absolute;
          bottom: 0;
          left: 14px;
          right: 14px;
          height: 2px;
          background: #3b82f6;
          border-radius: 2px 2px 0 0;
        }

        .ceid-nav-link-icon {
          font-size: 14px;
          opacity: 0.7;
          font-style: normal;
        }

        .ceid-nav-right {
          display: flex;
          align-items: center;
          padding: 0 16px;
          border-left: 1px solid #1a2744;
          gap: 8px;
        }

        .ceid-nav-status {
          display: flex;
          align-items: center;
          gap: 5px;
          font-size: 11px;
          color: #475569;
          font-family: 'JetBrains Mono', monospace;
        }

        .ceid-nav-status-dot {
          width: 6px;
          height: 6px;
          border-radius: 50%;
          background: #22c55e;
          flex-shrink: 0;
        }
      `}</style>

      <nav className="ceid-nav">
        <Link to="/" className="ceid-nav-brand">
          <div className="ceid-nav-brand-logo">ΠΠ</div>
          <div className="ceid-nav-brand-text">
            <span className="ceid-nav-brand-title">Ωρολόγιο ΤΜΗΥΠ</span>
            <span className="ceid-nav-brand-subtitle">Πανεπιστήμιο Πατρών</span>
          </div>
        </Link>

        <div className="ceid-nav-links">
          {NAV_LINKS.map((link) => (
            <Link
              key={link.to}
              to={link.to}
              className={`ceid-nav-link${
                location.pathname === link.to ||
                (link.to !== '/' && location.pathname.startsWith(link.to))
                  ? ' active'
                  : ''
              }`}
            >
              <i className="ceid-nav-link-icon">{link.icon}</i>
              {link.label}
            </Link>
          ))}
        </div>

        <div className="ceid-nav-right">
          <div className="ceid-nav-status">
            <div className="ceid-nav-status-dot" />
            API
          </div>
        </div>
      </nav>
    </>
  );
}
