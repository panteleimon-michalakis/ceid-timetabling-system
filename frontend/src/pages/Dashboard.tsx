import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { healthService, timetableService } from '../api/services';
import type { Timetable } from '../types';

interface HealthData {
  status: string;
  application: string;
  rooms: number;
  courses: number;
  users: number;
}

const STYLES = `
@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:ital,wght@0,300;0,400;0,500;0,600;1,300&family=JetBrains+Mono:wght@400;500&display=swap');

* { box-sizing: border-box; margin: 0; padding: 0; }

.db-root {
  font-family: 'IBM Plex Sans', sans-serif;
  min-height: calc(100vh - 52px);
  background: #080f1a;
  color: #e2e8f0;
  padding: 40px 48px;
  max-width: 1200px;
}

.db-header {
  margin-bottom: 40px;
}

.db-header-eyebrow {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: #3b82f6;
  letter-spacing: 2px;
  text-transform: uppercase;
  margin-bottom: 10px;
}

.db-header-title {
  font-size: 28px;
  font-weight: 600;
  color: #f1f5f9;
  letter-spacing: -0.5px;
  line-height: 1.2;
  margin-bottom: 6px;
}

.db-header-sub {
  font-size: 14px;
  font-weight: 300;
  color: #475569;
  font-style: italic;
}

.db-stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 40px;
}

.db-stat {
  background: #0d1b2e;
  border: 1px solid #1a2744;
  border-radius: 10px;
  padding: 20px 22px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  position: relative;
  overflow: hidden;
}

.db-stat::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 2px;
  background: var(--accent);
  opacity: 0.8;
}

.db-stat-value {
  font-family: 'JetBrains Mono', monospace;
  font-size: 30px;
  font-weight: 500;
  color: var(--accent);
  line-height: 1;
}

.db-stat-label {
  font-size: 12px;
  color: #64748b;
  letter-spacing: 0.3px;
  font-weight: 500;
}

.db-section-title {
  font-size: 11px;
  font-weight: 600;
  color: #475569;
  letter-spacing: 1.5px;
  text-transform: uppercase;
  margin-bottom: 14px;
  font-family: 'JetBrains Mono', monospace;
}

.db-timetables-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
  margin-bottom: 40px;
}

.db-timetable-card {
  background: #0d1b2e;
  border: 1px solid #1a2744;
  border-radius: 10px;
  padding: 18px 20px;
  text-decoration: none;
  color: inherit;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: border-color 0.15s, background 0.15s;
  cursor: pointer;
}

.db-timetable-card:hover {
  border-color: #1e3a5f;
  background: #0f2038;
}

.db-timetable-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.db-timetable-card-name {
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
  line-height: 1.3;
  flex: 1;
}

.db-badge {
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  font-weight: 500;
  padding: 3px 8px;
  border-radius: 4px;
  white-space: nowrap;
  letter-spacing: 0.5px;
  flex-shrink: 0;
}

.db-badge-exam     { background: #1c1010; color: #f87171; border: 1px solid #7f1d1d; }
.db-badge-semester { background: #0c1a10; color: #4ade80; border: 1px solid #14532d; }
.db-badge-fall     { background: #0f1a2e; color: #60a5fa; border: 1px solid #1e3a5f; }
.db-badge-spring   { background: #12181a; color: #34d399; border: 1px solid #064e3b; }
.db-badge-sep      { background: #1a180c; color: #fbbf24; border: 1px solid #78350f; }
.db-badge-draft    { background: #151515; color: #94a3b8; border: 1px solid #334155; }
.db-badge-published{ background: #0c1a10; color: #4ade80; border: 1px solid #14532d; }

.db-timetable-card-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.db-timetable-card-year {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: #475569;
}

.db-timetable-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 1px solid #1a2744;
  font-size: 12px;
  color: #475569;
}

.db-timetable-card-action {
  font-size: 11px;
  color: #3b82f6;
  font-weight: 500;
  letter-spacing: 0.3px;
}

.db-status-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 14px 18px;
  background: #0d1b2e;
  border: 1px solid #1a2744;
  border-radius: 10px;
  font-size: 12px;
  color: #475569;
  font-family: 'JetBrains Mono', monospace;
}

.db-status-dot { width: 7px; height: 7px; border-radius: 50%; background: #22c55e; flex-shrink: 0; }
.db-status-ok  { color: #22c55e; }

.db-empty {
  text-align: center;
  padding: 40px;
  color: #334155;
  font-size: 13px;
}

.db-loading {
  padding: 40px 48px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #334155;
}

.db-error {
  padding: 40px 48px;
  color: #f87171;
  font-size: 14px;
}
`;

function semTypeBadge(t: Timetable) {
  const st = t.semesterType;
  if (st === 'FALL')      return <span className="db-badge db-badge-fall">ΧΕΙΜ</span>;
  if (st === 'SPRING')    return <span className="db-badge db-badge-spring">ΕΑΡ</span>;
  if (st === 'SEPTEMBER') return <span className="db-badge db-badge-sep">ΣΕΠΤ</span>;
  return null;
}

function typeBadge(t: Timetable) {
  if (t.timetableType === 'EXAM')
    return <span className="db-badge db-badge-exam">ΕΞΕΤΑΣΤΙΚΗ</span>;
  return <span className="db-badge db-badge-semester">ΕΞΑΜΗΝΟ</span>;
}

function statusBadge(t: Timetable) {
  if (t.status === 'PUBLISHED')
    return <span className="db-badge db-badge-published">ΔΗΜΟΣ</span>;
  return <span className="db-badge db-badge-draft">DRAFT</span>;
}

export default function Dashboard() {
  const [health, setHealth] = useState<HealthData | null>(null);
  const [timetables, setTimetables] = useState<Timetable[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    healthService.check()
      .then(res => setHealth(res.data))
      .catch(() => setError('Αδύνατη σύνδεση με τον server. Τρέχει το backend;'));

    timetableService.getAll()
      .then(res => setTimetables(res.data as Timetable[]))
      .catch(() => {});
  }, []);

  if (error) return (
    <div className="db-error">
      <style>{STYLES}</style>
      <p>⚠ {error}</p>
    </div>
  );

  if (!health) return (
    <div className="db-loading">
      <style>{STYLES}</style>
      Φόρτωση δεδομένων...
    </div>
  );

  const semTimetables = timetables.filter(t => t.timetableType === 'SEMESTER');
  const examTimetables = timetables.filter(t => t.timetableType === 'EXAM');

  return (
    <div className="db-root">
      <style>{STYLES}</style>

      <div className="db-header">
        <div className="db-header-eyebrow">ΤΜΗΥΠ · ΠΑΝΕΠΙΣΤΗΜΙΟ ΠΑΤΡΩΝ</div>
        <div className="db-header-title">Σύστημα Διαχείρισης Ωρολογίου</div>
        <div className="db-header-sub">Τμήμα Μηχανικών Ηλεκτρονικών Υπολογιστών και Πληροφορικής</div>
      </div>

      <div className="db-stats-row">
        <div className="db-stat" style={{'--accent': '#3b82f6'} as React.CSSProperties}>
          <div className="db-stat-value">{health.courses}</div>
          <div className="db-stat-label">Μαθήματα</div>
        </div>
        <div className="db-stat" style={{'--accent': '#10b981'} as React.CSSProperties}>
          <div className="db-stat-value">{health.rooms}</div>
          <div className="db-stat-label">Αίθουσες</div>
        </div>
        <div className="db-stat" style={{'--accent': '#8b5cf6'} as React.CSSProperties}>
          <div className="db-stat-value">{timetables.length}</div>
          <div className="db-stat-label">Προγράμματα</div>
        </div>
        <div className="db-stat" style={{'--accent': '#f59e0b'} as React.CSSProperties}>
          <div className="db-stat-value">{health.users}</div>
          <div className="db-stat-label">Χρήστες</div>
        </div>
      </div>

      {semTimetables.length > 0 && (
        <>
          <div className="db-section-title">Εξαμηνιαία Προγράμματα</div>
          <div className="db-timetables-grid">
            {semTimetables.map(t => (
              <Link key={t.id} to="/timetable" className="db-timetable-card"
                onClick={() => localStorage.setItem('selectedTimetableId', String(t.id))}>
                <div className="db-timetable-card-header">
                  <div className="db-timetable-card-name">{t.name}</div>
                  {semTypeBadge(t)}
                </div>
                <div className="db-timetable-card-meta">
                  {typeBadge(t)}
                  {statusBadge(t)}
                  <span className="db-timetable-card-year">{t.academicYear}</span>
                </div>
                <div className="db-timetable-card-footer">
                  <span>ID {t.id}</span>
                  <span className="db-timetable-card-action">Άνοιγμα →</span>
                </div>
              </Link>
            ))}
          </div>
        </>
      )}

      {examTimetables.length > 0 && (
        <>
          <div className="db-section-title">Εξεταστικές Περίοδοι</div>
          <div className="db-timetables-grid">
            {examTimetables.map(t => (
              <Link key={t.id} to="/exams" className="db-timetable-card"
                onClick={() => localStorage.setItem('selectedExamTimetableId', String(t.id))}>
                <div className="db-timetable-card-header">
                  <div className="db-timetable-card-name">{t.name}</div>
                  {semTypeBadge(t)}
                </div>
                <div className="db-timetable-card-meta">
                  {typeBadge(t)}
                  {statusBadge(t)}
                  <span className="db-timetable-card-year">{t.academicYear}</span>
                </div>
                <div className="db-timetable-card-footer">
                  <span>ID {t.id}</span>
                  <span className="db-timetable-card-action">Άνοιγμα →</span>
                </div>
              </Link>
            ))}
          </div>
        </>
      )}

      {timetables.length === 0 && (
        <div className="db-empty">Δεν βρέθηκαν αποθηκευμένα προγράμματα.</div>
      )}

      <div className="db-status-bar">
        <div className="db-status-dot" />
        <span className="db-status-ok">ONLINE</span>
        <span>·</span>
        <span>{health.application}</span>
        <span>·</span>
        <span>{health.status}</span>
      </div>
    </div>
  );
}
