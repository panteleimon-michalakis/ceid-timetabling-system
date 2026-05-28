import { useEffect, useMemo, useState } from 'react';
import { courseService } from '../api/services';
import type { Course } from '../types';

const STYLES = `
@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;400;500;600&family=JetBrains+Mono:wght@400;500&display=swap');
.cr-root { font-family:'IBM Plex Sans',sans-serif; min-height:calc(100vh - 52px); background:#080f1a; color:#e2e8f0; padding:36px 48px; }
.cr-header { margin-bottom:28px; }
.cr-title { font-size:1.5rem; font-weight:600; letter-spacing:-0.4px; margin-bottom:4px; }
.cr-sub { font-size:13px; color:#475569; }
.cr-filters { display:flex; flex-wrap:wrap; gap:12px; margin-bottom:20px; align-items:center; }
.cr-filter-group { display:flex; gap:4px; }
.cr-btn { padding:5px 12px; border:none; border-radius:6px; cursor:pointer; font-size:12px; font-family:'IBM Plex Sans',sans-serif; font-weight:500; transition:background 0.15s; }
.cr-btn-active { background:#1d4ed8; color:#fff; }
.cr-btn-inactive { background:#0d1b2e; color:#64748b; border:1px solid #1a2744; }
.cr-btn-inactive:hover { color:#94a3b8; border-color:#1e3a5f; }
.cr-search { padding:6px 12px; border-radius:7px; border:1px solid #1a2744; background:#0d1b2e; color:#e2e8f0; font-size:13px; font-family:'IBM Plex Sans',sans-serif; width:240px; }
.cr-search:focus { outline:none; border-color:#1d4ed8; }
.cr-table-wrap { overflow-x:auto; border-radius:10px; border:1px solid #1a2744; }
.cr-table { width:100%; border-collapse:collapse; font-size:13px; }
.cr-th { padding:10px 14px; background:#0a1628; color:#475569; font-size:10px; text-transform:uppercase; letter-spacing:1px; font-family:'JetBrains Mono',monospace; font-weight:500; text-align:left; white-space:nowrap; border-bottom:1px solid #1a2744; }
.cr-tr { border-bottom:1px solid #0f1f38; transition:background 0.1s; }
.cr-tr:hover { background:#0d1b2e; }
.cr-td { padding:9px 14px; vertical-align:middle; }
.cr-code { font-family:'JetBrains Mono',monospace; font-size:11px; color:#64748b; }
.cr-badge { display:inline-block; padding:2px 8px; border-radius:4px; font-size:10px; font-weight:600; font-family:'JetBrains Mono',monospace; letter-spacing:0.3px; }
.cr-hours { font-family:'JetBrains Mono',monospace; font-size:11px; color:#94a3b8; }
.cr-teacher { font-size:11px; color:#475569; max-width:200px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.cr-empty { text-align:center; padding:48px; color:#334155; font-size:13px; }
.cr-count { font-family:'JetBrains Mono',monospace; font-size:11px; color:#334155; }
`;

const TYPE_CONFIG: Record<string, { label: string; color: string; bg: string }> = {
  REQUIRED:           { label: 'Υποχρεωτικό',     color: '#3b82f6', bg: '#1e3a5f22' },
  REQUIRED_ELECTIVE:  { label: 'Επιλογής',         color: '#f59e0b', bg: '#78350f22' },
  GENERAL_EDUCATION:  { label: 'Γεν. Παιδείας',    color: '#10b981', bg: '#05311e22' },
  EXTERNAL:           { label: 'Εξωτερικό',         color: '#8b5cf6', bg: '#3b0d8022' },
};

const SEM_TYPE: Record<string, string> = {
  FALL: 'Χειμ', SPRING: 'Εαρ', BOTH: 'Αμφ',
};

export default function Courses() {
  const [courses, setCourses]           = useState<Course[]>([]);
  const [typeFilter, setTypeFilter]     = useState('ALL');
  const [semFilter, setSemFilter]       = useState(0);
  const [yearFilter, setYearFilter]     = useState(0);
  const [search, setSearch]             = useState('');
  const [loading, setLoading]           = useState(true);

  useEffect(() => {
    courseService.getAll()
      .then(r => setCourses(r.data))
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return courses.filter(c => {
      if (typeFilter !== 'ALL' && c.courseType !== typeFilter) return false;
      if (semFilter > 0 && c.semester !== semFilter) return false;
      if (yearFilter > 0 && c.studyYear !== yearFilter) return false;
      if (q && !c.name.toLowerCase().includes(q) && !c.code.toLowerCase().includes(q)) return false;
      return true;
    });
  }, [courses, typeFilter, semFilter, yearFilter, search]);

  return (
    <div className="cr-root">
      <style>{STYLES}</style>

      <div className="cr-header">
        <div className="cr-title">Μαθήματα</div>
        <div className="cr-sub">Κατάλογος μαθημάτων ΤΜΗΥΠ · {courses.length} συνολικά</div>
      </div>

      <div className="cr-filters">
        {/* Type filter */}
        <div className="cr-filter-group">
          {(['ALL', 'REQUIRED', 'REQUIRED_ELECTIVE', 'GENERAL_EDUCATION', 'EXTERNAL'] as const).map(t => (
            <button key={t} className={`cr-btn ${typeFilter === t ? 'cr-btn-active' : 'cr-btn-inactive'}`}
              onClick={() => setTypeFilter(t)}>
              {t === 'ALL' ? 'Όλα' : TYPE_CONFIG[t]?.label}
            </button>
          ))}
        </div>

        {/* Year filter */}
        <div className="cr-filter-group">
          {[0,1,2,3,4,5].map(y => (
            <button key={y} className={`cr-btn ${yearFilter === y ? 'cr-btn-active' : 'cr-btn-inactive'}`}
              onClick={() => setYearFilter(y)}>
              {y === 0 ? 'Όλα τα έτη' : `${y}ο`}
            </button>
          ))}
        </div>

        {/* Semester filter */}
        <div className="cr-filter-group">
          <button className={`cr-btn ${semFilter === 0 ? 'cr-btn-active' : 'cr-btn-inactive'}`}
            onClick={() => setSemFilter(0)}>Όλα Εξ.</button>
          {[1,2,3,4,5,6,7,8,9,10].map(s => (
            <button key={s} className={`cr-btn ${semFilter === s ? 'cr-btn-active' : 'cr-btn-inactive'}`}
              onClick={() => setSemFilter(s)}>Εξ.{s}</button>
          ))}
        </div>

        <input className="cr-search" placeholder="Αναζήτηση μαθήματος..."
          value={search} onChange={e => setSearch(e.target.value)} />

        <span className="cr-count">{filtered.length} αποτελέσματα</span>
      </div>

      {loading ? (
        <div className="cr-empty">Φόρτωση...</div>
      ) : (
        <div className="cr-table-wrap">
          <table className="cr-table">
            <thead>
              <tr>
                <th className="cr-th">Κωδικός</th>
                <th className="cr-th">Μάθημα</th>
                <th className="cr-th">Εξ.</th>
                <th className="cr-th">Έτος</th>
                <th className="cr-th">Τύπος</th>
                <th className="cr-th">Εξάμηνο</th>
                <th className="cr-th">Θ / Φ / Ε</th>
                <th className="cr-th">ECTS</th>
                <th className="cr-th">Τομέας</th>
                <th className="cr-th">Φοιτητές</th>
                <th className="cr-th">Διδάσκοντες</th>
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 ? (
                <tr><td colSpan={11} className="cr-empty">Δεν βρέθηκαν μαθήματα.</td></tr>
              ) : filtered.map(c => {
                const tc = TYPE_CONFIG[c.courseType] ?? { label: c.courseType, color: '#64748b', bg: '#1a274422' };
                return (
                  <tr key={c.id} className="cr-tr">
                    <td className="cr-td"><span className="cr-code">{c.code}</span></td>
                    <td className="cr-td" style={{ fontWeight: 500, maxWidth: '280px' }}>{c.name}</td>
                    <td className="cr-td" style={{ textAlign: 'center' }}>
                      <span className="cr-code">{c.semester}</span>
                    </td>
                    <td className="cr-td" style={{ textAlign: 'center' }}>
                      <span className="cr-code">{c.studyYear}ο</span>
                    </td>
                    <td className="cr-td">
                      <span className="cr-badge" style={{ background: tc.bg, color: tc.color }}>
                        {tc.label}
                      </span>
                    </td>
                    <td className="cr-td" style={{ textAlign: 'center' }}>
                      <span className="cr-code">{SEM_TYPE[c.semesterType] ?? c.semesterType}</span>
                    </td>
                    <td className="cr-td">
                      <span className="cr-hours">{c.lectureHours ?? 0} / {c.tutorialHours ?? 0} / {c.labHours ?? 0}</span>
                    </td>
                    <td className="cr-td" style={{ textAlign: 'center' }}>
                      <span className="cr-code">{c.ects}</span>
                    </td>
                    <td className="cr-td" style={{ textAlign: 'center' }}>
                      <span className="cr-code">{c.sector ?? '—'}</span>
                    </td>
                    <td className="cr-td" style={{ textAlign: 'center' }}>
                      <span className="cr-code">{c.expectedStudents ?? '—'}</span>
                    </td>
                    <td className="cr-td">
                      <span className="cr-teacher" title={c.teachersText}>{c.teachersText || '—'}</span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
