import { useEffect, useMemo, useState } from 'react';
import { generateIcal, generateExamIcal, downloadIcal } from '../utils/icalExport';
import type { Timetable, Assignment } from './studentTimetableTypes';

// ─── Constants ────────────────────────────────────────────────────────────────

const DAYS = ['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY'];
const DAY_LBL: Record<string,string> = {
  MONDAY:'Δευτέρα', TUESDAY:'Τρίτη', WEDNESDAY:'Τετάρτη',
  THURSDAY:'Πέμπτη', FRIDAY:'Παρασκευή',
};
const HOURS = [9,10,11,12,13,14,15,16,17,18,19,20];

const TYPE_COLOR: Record<string, { bg: string; border: string; label: string }> = {
  LECTURE:  { bg: '#1e3a5f', border: '#3b82f6', label: 'Θ' },
  TUTORIAL: { bg: '#14532d', border: '#22c55e', label: 'Φ' },
  LAB:      { bg: '#451a03', border: '#f59e0b', label: 'Ε' },
  EXAM:     { bg: '#2d1b69', border: '#8b5cf6', label: 'Εξ' },
};

const SEM_LABEL: Record<string,string> = {
  FALL: 'Χειμερινό', SPRING: 'Εαρινό', SEPTEMBER: 'Σεπτέμβριος',
};

const YEAR_COLORS = ['#3b82f6','#22c55e','#a855f7','#f59e0b','#ef4444'];

const PERSONAL_KEY = (id: number) => `ceid-personal-courses-${id}`;

function normalizeTime(t: string): string {
  if (!t) return '09:00';
  const [h, m] = t.split(':');
  return `${String(parseInt(h)).padStart(2,'0')}:${m ?? '00'}`;
}
function slotKey(day: string, hour: number) { return `${day}_${hour}`; }

function formatDate(dateStr: string): string {
  try {
    const d = new Date(dateStr + 'T00:00:00');
    const day = d.toLocaleDateString('el-GR', { weekday: 'short' });
    const num = d.getDate();
    const mon = d.getMonth() + 1;
    return `${day} ${num}/${mon}`;
  } catch { return dateStr; }
}

// ─── Component ────────────────────────────────────────────────────────────────

interface Props {
  timetables: Timetable[];
  assignments: Assignment[];
  loading: boolean;
  selectedTtId: number | null;
  onSelectTt: (id: number) => void;
}

export default function StudentTimetableView({
  timetables, assignments, loading, selectedTtId, onSelectTt,
}: Props) {
  const [yearFilter,      setYearFilter]      = useState<number | null>(null);
  const [mode,            setMode]            = useState<'all' | 'personal'>('all');
  const [selectedCourses, setSelectedCourses] = useState<Set<number>>(new Set());

  // Semester vs Exam split
  const semesterTimetables = useMemo(() => timetables.filter(t => t.timetableType === 'SEMESTER'), [timetables]);
  const examTimetables     = useMemo(() => timetables.filter(t => t.timetableType === 'EXAM'),     [timetables]);

  const selectedTt = timetables.find(t => t.id === selectedTtId);
  const isExamView = selectedTt?.timetableType === 'EXAM';

  // Όταν αλλάζει το επιλεγμένο πρόγραμμα: φόρτωσε αποθηκευμένα προσωπικά μαθήματα
  // + μηδένισε το φίλτρο έτους. (Τα assignments τα φέρνει ο hook.)
  useEffect(() => {
    if (selectedTtId == null) return;
    try {
      const saved = localStorage.getItem(PERSONAL_KEY(selectedTtId));
      // Συγχρονισμός state από εξωτερικό store (localStorage) όταν αλλάζει το πρόγραμμα.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setSelectedCourses(saved ? new Set(JSON.parse(saved)) : new Set());
    } catch { setSelectedCourses(new Set()); }
    setYearFilter(null);
  }, [selectedTtId]);

  function toggleCourse(courseId: number) {
    setSelectedCourses(prev => {
      const next = new Set(prev);
      if (next.has(courseId)) next.delete(courseId); else next.add(courseId);
      if (selectedTtId) {
        try { localStorage.setItem(PERSONAL_KEY(selectedTtId), JSON.stringify([...next])); } catch { /* localStorage μη διαθέσιμο — αγνοούμε */ }
      }
      return next;
    });
  }

  // ── Semester-specific derived data ────────────────────────────────────────

  const allCourses = useMemo(() => {
    const map = new Map<number, Assignment['course']>();
    assignments.forEach(a => { if (!map.has(a.course.id)) map.set(a.course.id, a.course); });
    return [...map.values()].sort((a,b) => a.studyYear - b.studyYear || a.semester - b.semester || a.name.localeCompare(b.name, 'el'));
  }, [assignments]);

  const filtered = useMemo(() => {
    return assignments.filter(a => {
      if (yearFilter !== null && a.course.studyYear !== yearFilter) return false;
      if (mode === 'personal' && !selectedCourses.has(a.course.id)) return false;
      return true;
    });
  }, [assignments, yearFilter, mode, selectedCourses]);

  const slotMap = useMemo(() => {
    const m = new Map<string, Assignment[]>();
    for (const a of filtered) {
      if (!a.timeSlot?.dayOfWeek || !a.timeSlot?.startTime) continue;
      const h = parseInt(a.timeSlot.startTime.split(':')[0]);
      const k = slotKey(a.timeSlot.dayOfWeek, h);
      if (!m.has(k)) m.set(k, []);
      m.get(k)!.push(a);
    }
    return m;
  }, [filtered]);

  const coursesByYear = useMemo(() => {
    const map = new Map<number, Assignment['course'][]>();
    allCourses.forEach(c => {
      if (!map.has(c.studyYear)) map.set(c.studyYear, []);
      map.get(c.studyYear)!.push(c);
    });
    return map;
  }, [allCourses]);

  // ── Exam-specific derived data ────────────────────────────────────────────

  const examDates = useMemo(() => {
    const s = new Set<string>();
    for (const a of assignments) {
      if (a.timeSlot?.specificDate) s.add(a.timeSlot.specificDate);
    }
    return Array.from(s).sort();
  }, [assignments]);

  const examSlotMap = useMemo(() => {
    // Map: `date_hour` → Assignment[]
    const m = new Map<string, Assignment[]>();
    for (const a of assignments) {
      if (!a.timeSlot?.specificDate || !a.timeSlot?.startTime) continue;
      if (yearFilter !== null && a.course.studyYear !== yearFilter) continue;
      if (mode === 'personal' && !selectedCourses.has(a.course.id)) continue;
      const h = parseInt(a.timeSlot.startTime.split(':')[0]);
      const k = `${a.timeSlot.specificDate}_${h}`;
      if (!m.has(k)) m.set(k, []);
      m.get(k)!.push(a);
    }
    return m;
  }, [assignments, yearFilter, mode, selectedCourses]);

  // ── Styles ─────────────────────────────────────────────────────────────────

  const card = { background: '#0d1b2e', border: '1px solid #1a2744', borderRadius: '10px' };

  function TtItem({ t }: { t: Timetable }) {
    const sel = selectedTtId === t.id;
    const isExam = t.timetableType === 'EXAM';
    return (
      <div className="tt-option" onClick={() => onSelectTt(t.id)}
        style={{
          padding: '8px 10px', borderRadius: '7px', cursor: 'pointer',
          background: sel ? '#1e3a5f' : 'transparent',
          border: `1px solid ${sel ? '#3b82f6' : 'transparent'}`,
        }}>
        <div style={{ fontSize: '12px', fontWeight: 500 }}>{t.name}</div>
        <div style={{ display: 'flex', gap: 5, marginTop: 3, alignItems: 'center' }}>
          {t.semesterType && (
            <span style={{ fontSize: '10px', color: '#475569', fontFamily: 'JetBrains Mono, monospace' }}>
              {SEM_LABEL[t.semesterType] ?? t.semesterType}
            </span>
          )}
          {isExam && t.startDate && (
            <span style={{ fontSize: '9px', color: '#8b5cf6', fontFamily: 'JetBrains Mono, monospace' }}>
              {t.startDate} → {t.endDate}
            </span>
          )}
        </div>
      </div>
    );
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div style={{
      background: '#080f1a', minHeight: 'calc(100vh - 52px)',
      fontFamily: "'IBM Plex Sans', sans-serif", color: '#e2e8f0',
      display: 'grid', gridTemplateColumns: '280px 1fr', gap: '0',
    }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;400;500;600&family=JetBrains+Mono:wght@400;500&display=swap');
        ::-webkit-scrollbar { width: 4px; height: 4px; }
        ::-webkit-scrollbar-track { background: #080f1a; }
        ::-webkit-scrollbar-thumb { background: #1a2744; border-radius: 2px; }
        .course-row:hover { background: #1a2744 !important; }
        .tt-option:hover { background: #1a2744 !important; }
        .exam-cell:hover { background: #111e33 !important; }
        @media print {
          .ceid-nav { display: none !important; }
          .sv-left-panel { display: none !important; }
          .sv-main { grid-column: 1 / -1 !important; padding: 0 !important; }
          body { background: white !important; color: black !important; }
          -webkit-print-color-adjust: exact; print-color-adjust: exact;
        }
      `}</style>

      {/* ══ LEFT PANEL ══════════════════════════════════════════════════════ */}
      <div className="sv-left-panel" style={{ borderRight: '1px solid #1a2744', display: 'flex', flexDirection: 'column', height: 'calc(100vh - 52px)', position: 'sticky', top: '52px' }}>

        {/* Header */}
        <div style={{ padding: '20px 20px 16px', borderBottom: '1px solid #1a2744' }}>
          <h1 style={{ fontSize: '1.1rem', fontWeight: 600, marginBottom: '4px', letterSpacing: '-0.3px' }}>
            Προβολή Προγράμματος
          </h1>
          <p style={{ color: '#475569', fontSize: '12px' }}>ΤΜΗΥΠ · Πανεπιστήμιο Πατρών</p>
        </div>

        {/* Timetable selector */}
        <div style={{ padding: '14px 20px', borderBottom: '1px solid #1a2744', overflowY: 'auto', maxHeight: '20vh', minHeight: '60px' }}>
          {semesterTimetables.length > 0 && (
            <>
              <div style={{ fontSize: '10px', color: '#475569', textTransform: 'uppercase', letterSpacing: '0.8px', marginBottom: '6px', fontFamily: 'JetBrains Mono, monospace' }}>
                Εβδομαδιαία
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '3px', marginBottom: '10px' }}>
                {semesterTimetables.map(t => <TtItem key={t.id} t={t} />)}
              </div>
            </>
          )}
          {examTimetables.length > 0 && (
            <>
              <div style={{ fontSize: '10px', color: '#8b5cf6', textTransform: 'uppercase', letterSpacing: '0.8px', marginBottom: '6px', fontFamily: 'JetBrains Mono, monospace' }}>
                Εξεταστικές
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
                {examTimetables.map(t => <TtItem key={t.id} t={t} />)}
              </div>
            </>
          )}
          {timetables.length === 0 && (
            <div style={{ color: '#334155', fontSize: '12px' }}>Δεν βρέθηκαν δημοσιευμένα προγράμματα.</div>
          )}
        </div>

        {/* Mode toggle */}
        <div style={{ padding: '14px 20px', borderBottom: '1px solid #1a2744' }}>
            <div style={{ display: 'flex', background: '#0d1b2e', borderRadius: '8px', padding: '3px', border: '1px solid #1a2744' }}>
              {([['all','Τμήματος'],['personal','Δικό μου']] as const).map(([m, label]) => (
                <button key={m} onClick={() => setMode(m)} style={{
                  flex: 1, padding: '6px 8px', border: 'none', borderRadius: '6px', cursor: 'pointer',
                  background: mode === m ? '#1d4ed8' : 'transparent',
                  color: mode === m ? '#fff' : '#475569',
                  fontSize: '12px', fontWeight: mode === m ? 600 : 400,
                  fontFamily: "'IBM Plex Sans', sans-serif",
                }}>{label}</button>
              ))}
            </div>
          </div>

        {/* Year filter */}
        <div style={{ padding: '14px 20px', borderBottom: '1px solid #1a2744' }}>
          <div style={{ fontSize: '10px', color: '#475569', textTransform: 'uppercase', letterSpacing: '0.8px', marginBottom: '8px', fontFamily: 'JetBrains Mono, monospace' }}>
            Φίλτρο Έτους
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
            <button onClick={() => setYearFilter(null)} style={{
              padding: '4px 10px', borderRadius: '6px', border: 'none', cursor: 'pointer', fontSize: '11px',
              background: yearFilter === null ? '#1d4ed8' : '#0d1b2e',
              color: yearFilter === null ? '#fff' : '#64748b',
              fontFamily: "'IBM Plex Sans', sans-serif",
            }}>Όλα</button>
            {[1,2,3,4,5].map(y => (
              <button key={y} onClick={() => setYearFilter(y === yearFilter ? null : y)} style={{
                padding: '4px 10px', borderRadius: '6px', border: 'none', cursor: 'pointer', fontSize: '11px',
                background: yearFilter === y ? YEAR_COLORS[y-1] : '#0d1b2e',
                color: yearFilter === y ? '#fff' : '#64748b',
                fontFamily: "'IBM Plex Sans', sans-serif",
              }}>{y}ο</button>
            ))}
          </div>
        </div>

        {/* Personal course picker */}
        {mode === 'personal' && (
          <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
            <div style={{ padding: '12px 20px 8px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div style={{ fontSize: '10px', color: '#475569', textTransform: 'uppercase', letterSpacing: '0.8px', fontFamily: 'JetBrains Mono, monospace' }}>
                Μαθήματα ({selectedCourses.size}/{allCourses.length})
              </div>
              {selectedCourses.size > 0 && (
                <button onClick={() => {
                  setSelectedCourses(new Set());
                  if (selectedTtId) try { localStorage.removeItem(PERSONAL_KEY(selectedTtId)); } catch { /* localStorage μη διαθέσιμο — αγνοούμε */ }
                }} style={{
                  fontSize: '10px', color: '#475569', background: 'none', border: 'none',
                  cursor: 'pointer', fontFamily: "'IBM Plex Sans', sans-serif",
                }}>Καθαρισμός</button>
              )}
            </div>
            <div style={{ flex: 1, overflowY: 'auto', padding: '0 12px 12px' }}>
              {[...coursesByYear.entries()].sort(([a],[b]) => a-b).map(([year, courses]) => (
                <div key={year} style={{ marginBottom: '8px' }}>
                  <div style={{
                    fontSize: '11px', fontWeight: 700, marginBottom: '6px', marginTop: '4px',
                    padding: '5px 10px', borderRadius: '6px',
                    fontFamily: 'JetBrains Mono, monospace',
                    background: `${YEAR_COLORS[year-1]}18`,
                    color: YEAR_COLORS[year-1],
                    borderLeft: `3px solid ${YEAR_COLORS[year-1]}`,
                    letterSpacing: '0.5px',
                  }}>
                    {year}ο Έτος
                  </div>
                  {courses.map(c => (
                    <div key={c.id} className="course-row" onClick={() => toggleCourse(c.id)}
                      style={{
                        display: 'flex', alignItems: 'center', gap: '10px',
                        padding: '8px 10px', borderRadius: '7px', cursor: 'pointer',
                        marginBottom: '3px',
                        background: selectedCourses.has(c.id) ? '#1e3a5f' : '#0d1b2e',
                        border: `1px solid ${selectedCourses.has(c.id) ? '#3b82f6' : '#1a2744'}`,
                        borderLeft: `3px solid ${selectedCourses.has(c.id) ? YEAR_COLORS[c.studyYear-1] : '#1a2744'}`,
                        transition: 'border-color 0.1s, background 0.1s',
                      }}>
                      <div style={{
                        width: '18px', height: '18px', borderRadius: '4px', flexShrink: 0,
                        background: selectedCourses.has(c.id) ? '#1d4ed8' : '#111e33',
                        border: `2px solid ${selectedCourses.has(c.id) ? '#3b82f6' : '#334155'}`,
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                      }}>
                        {selectedCourses.has(c.id) && <span style={{ color: '#fff', fontSize: '11px', lineHeight: 1, fontWeight: 700 }}>✓</span>}
                      </div>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontSize: '12px', fontWeight: 500, lineHeight: 1.4,
                          color: selectedCourses.has(c.id) ? '#e2e8f0' : '#94a3b8',
                          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {c.name}
                        </div>
                        <div style={{ fontSize: '10px', color: '#475569', fontFamily: 'JetBrains Mono, monospace', marginTop: '2px' }}>
                          {c.code} · Εξ.{c.semester}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Stats + export */}
        <div style={{ padding: '12px 20px', borderTop: '1px solid #1a2744', marginTop: 'auto' }}>
          {!isExamView ? (
            <>
              <div style={{ fontSize: '11px', color: '#334155', fontFamily: 'JetBrains Mono, monospace' }}>
                {filtered.length} ώρες · {mode === 'personal' ? `${selectedCourses.size} μαθήματα` : 'όλα τα μαθήματα'}
              </div>
              {selectedTt && filtered.length > 0 && (
                <button
                  onClick={() => window.print()}
                  style={{
                    marginTop: '8px', width: '100%', padding: '7px', border: 'none',
                    borderRadius: '7px', background: '#0f766e', color: '#fff',
                    fontSize: '12px', fontWeight: 600, cursor: 'pointer',
                    fontFamily: "'IBM Plex Sans', sans-serif",
                  }}
                >🖨 Εκτύπωση</button>
              )}
              {selectedTt && filtered.length > 0 && (
                <button
                  onClick={() => {
                    const name = mode === 'personal' ? 'Προσωπικό Πρόγραμμα' : (selectedTt.name ?? 'Πρόγραμμα');
                    downloadIcal(`ceid-${name.replace(/\s+/g, '-')}.ics`, generateIcal(filtered, selectedTt, name));
                  }}
                  style={{
                    marginTop: '8px', width: '100%', padding: '7px', border: 'none',
                    borderRadius: '7px', background: '#1d4ed8', color: '#fff',
                    fontSize: '12px', fontWeight: 600, cursor: 'pointer',
                    fontFamily: "'IBM Plex Sans', sans-serif",
                  }}
                >📥 Εξαγωγή iCal</button>
              )}
              <div style={{ display: 'flex', gap: '10px', marginTop: '6px' }}>
                {Object.entries(TYPE_COLOR).filter(([t]) => t !== 'EXAM').map(([type, { border, label }]) => (
                  <div key={type} style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <div style={{ width: '8px', height: '8px', borderRadius: '2px', background: border }} />
                    <span style={{ fontSize: '10px', color: '#475569' }}>
                      {label === 'Θ' ? 'Θεωρία' : label === 'Φ' ? 'Φροντ.' : 'Εργαστ.'}
                    </span>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <>
              <div style={{ fontSize: '11px', color: '#334155', fontFamily: 'JetBrains Mono, monospace' }}>
                {assignments.filter(a =>
                  (yearFilter === null || a.course.studyYear === yearFilter) &&
                  (mode !== 'personal' || selectedCourses.has(a.course.id))
                ).length} εξετάσεις
                {mode === 'personal' && ` · ${selectedCourses.size} επιλεγμένες`}
                {yearFilter !== null && ` · ${yearFilter}ο έτος`}
              </div>
              {selectedTt && assignments.length > 0 && (
                <button
                  onClick={() => {
                    const visible = assignments.filter(a =>
                      (yearFilter === null || a.course.studyYear === yearFilter) &&
                      (mode !== 'personal' || selectedCourses.has(a.course.id))
                    );
                    downloadIcal(
                      `ceid-exam-${(selectedTt.name ?? 'exam').replace(/\s+/g, '-')}.ics`,
                      generateExamIcal(visible, selectedTt.name ?? 'Εξεταστική')
                    );
                  }}
                  style={{
                    marginTop: '8px', width: '100%', padding: '7px', border: 'none',
                    borderRadius: '7px', background: '#6d28d9', color: '#fff',
                    fontSize: '12px', fontWeight: 600, cursor: 'pointer',
                    fontFamily: "'IBM Plex Sans', sans-serif",
                  }}
                >📥 Εξαγωγή iCal</button>
              )}
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '6px' }}>
                <div style={{ width: '8px', height: '8px', borderRadius: '2px', background: '#8b5cf6' }} />
                <span style={{ fontSize: '10px', color: '#475569' }}>Εξέταση</span>
              </div>
            </>
          )}
        </div>
      </div>

      {/* ══ MAIN AREA ═══════════════════════════════════════════════════════ */}
      <div className="sv-main" style={{ overflow: 'auto', padding: '24px 28px' }}>

        {/* Title */}
        <div style={{ marginBottom: '16px' }}>
          <h2 style={{ fontSize: '1.1rem', fontWeight: 600, letterSpacing: '-0.3px' }}>
            {isExamView
              ? selectedTt?.name ?? 'Εξεταστική'
              : mode === 'personal'
                  ? selectedCourses.size === 0
                      ? 'Προσωπικό Πρόγραμμα — επίλεξε μαθήματα'
                      : `Προσωπικό Πρόγραμμα (${selectedCourses.size} μαθήματα)`
                  : selectedTt?.name ?? 'Πρόγραμμα'}
          </h2>
          {loading && <span style={{ fontSize: '12px', color: '#475569', fontFamily: 'JetBrains Mono, monospace' }}>Φόρτωση...</span>}
        </div>

        {/* ── Εξεταστική view ─────────────────────────────────────────────── */}
        {isExamView && (
          <>
            {examDates.length === 0 && !loading && (
              <div style={{ ...card, padding: '48px', textAlign: 'center', color: '#334155' }}>
                <div style={{ fontSize: '2rem', marginBottom: '12px' }}>◫</div>
                <div style={{ fontSize: '14px' }}>Δεν υπάρχουν τοποθετημένες εξετάσεις σε αυτό το πρόγραμμα.</div>
              </div>
            )}
            {examDates.length > 0 && (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ borderCollapse: 'collapse', minWidth: `${80 + examDates.length * 150}px` }}>
                  <thead>
                    <tr>
                      <th style={{ width: '70px', padding: '8px 10px', fontSize: '11px', color: '#475569', textAlign: 'left', fontFamily: 'JetBrains Mono, monospace', fontWeight: 400 }}>
                        Ώρα
                      </th>
                      {examDates.map(date => (
                        <th key={date} style={{ padding: '8px 10px', fontSize: '12px', color: '#94a3b8', textAlign: 'center', fontWeight: 500, minWidth: '150px' }}>
                          {formatDate(date)}
                          <div style={{ fontSize: '9px', color: '#334155', fontWeight: 400, fontFamily: 'JetBrains Mono, monospace', marginTop: '2px' }}>
                            {date}
                          </div>
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {HOURS.map(h => {
                      const hasContent = examDates.some(d => (examSlotMap.get(`${d}_${h}`) ?? []).length > 0);
                      return (
                        <tr key={h}>
                          <td style={{ padding: '3px 10px 3px 0', fontSize: '11px', color: '#334155', fontFamily: 'JetBrains Mono, monospace', verticalAlign: 'top', paddingTop: '6px', whiteSpace: 'nowrap' }}>
                            {String(h).padStart(2,'0')}:00
                          </td>
                          {examDates.map(date => {
                            const items = examSlotMap.get(`${date}_${h}`) ?? [];
                            return (
                              <td key={date} className="exam-cell" style={{
                                padding: '3px 4px', verticalAlign: 'top',
                                borderTop: '1px solid #0f1f38',
                                background: items.length > 0 ? 'transparent' : hasContent ? '#080f1a' : 'transparent',
                              }}>
                                {items.map(a => {
                                  const yc = YEAR_COLORS[a.course.studyYear - 1] ?? '#8b5cf6';
                                  const durationH = a.examDurationMinutes ? a.examDurationMinutes / 60 : 3;
                                  return (
                                    <div key={a.id} style={{
                                      background: '#1a0f40',
                                      border: '1px solid #8b5cf6',
                                      borderLeft: `3px solid ${yc}`,
                                      borderRadius: '6px', padding: '5px 7px', marginBottom: '3px',
                                    }}>
                                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2px' }}>
                                        <span style={{ fontSize: '9px', fontWeight: 700, fontFamily: 'JetBrains Mono, monospace', color: yc }}>
                                          {a.course.code}
                                        </span>
                                        <span style={{ fontSize: '9px', padding: '1px 5px', borderRadius: '3px', background: `${yc}33`, color: yc, fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>
                                          {a.course.studyYear}ο
                                        </span>
                                      </div>
                                      <div style={{ fontSize: '10px', fontWeight: 500, lineHeight: 1.3, marginBottom: '3px', color: '#e2e8f0' }}>
                                        {a.course.name.length > 30 ? a.course.name.slice(0,29) + '…' : a.course.name}
                                      </div>
                                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '9px', color: '#64748b', fontFamily: 'JetBrains Mono, monospace' }}>
                                        <span>{a.room.code}</span>
                                        <span>{durationH}h · {normalizeTime(a.timeSlot.startTime)}</span>
                                      </div>
                                    </div>
                                  );
                                })}
                              </td>
                            );
                          })}
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}

        {/* ── Εβδομαδιαία view ─────────────────────────────────────────────── */}
        {!isExamView && (
          <>
            {mode === 'personal' && selectedCourses.size === 0 ? (
              <div style={{ ...card, padding: '48px', textAlign: 'center', color: '#334155' }}>
                <div style={{ fontSize: '2rem', marginBottom: '12px' }}>◈</div>
                <div style={{ fontSize: '14px', marginBottom: '6px' }}>Επίλεξε μαθήματα από την αριστερή λίστα</div>
                <div style={{ fontSize: '12px', color: '#1a2744' }}>Το προσωπικό σου πρόγραμμα θα αποθηκευτεί αυτόματα</div>
              </div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ borderCollapse: 'collapse', width: '100%', minWidth: '700px' }}>
                  <thead>
                    <tr>
                      <th style={{ width: '56px', padding: '8px 10px', fontSize: '11px', color: '#475569', textAlign: 'left', fontFamily: 'JetBrains Mono, monospace', fontWeight: 400 }}>
                        Ώρα
                      </th>
                      {DAYS.map(d => (
                        <th key={d} style={{ padding: '8px 10px', fontSize: '13px', color: '#94a3b8', textAlign: 'center', fontWeight: 500 }}>
                          {DAY_LBL[d].slice(0,3)}
                          <div style={{ fontSize: '10px', color: '#334155', fontWeight: 400 }}>
                            {DAY_LBL[d].slice(3)}
                          </div>
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {HOURS.map(h => (
                      <tr key={h}>
                        <td style={{ padding: '3px 10px 3px 0', fontSize: '11px', color: '#334155', fontFamily: 'JetBrains Mono, monospace', verticalAlign: 'top', paddingTop: '6px', whiteSpace: 'nowrap' }}>
                          {String(h).padStart(2,'0')}:00
                        </td>
                        {DAYS.map(d => {
                          const items = slotMap.get(slotKey(d, h)) ?? [];
                          return (
                            <td key={d} style={{
                              padding: '3px 4px', verticalAlign: 'top', minWidth: '130px',
                              borderTop: '1px solid #0f1f38',
                            }}>
                              {items.length > 0 && (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
                                  {items.map(a => {
                                    const tc = TYPE_COLOR[a.assignmentType] ?? TYPE_COLOR.LECTURE;
                                    const yc = YEAR_COLORS[a.course.studyYear - 1] ?? '#64748b';
                                    return (
                                      <div key={a.id} style={{
                                        background: tc.bg,
                                        border: `1px solid ${tc.border}`,
                                        borderLeft: `3px solid ${yc}`,
                                        borderRadius: '6px', padding: '5px 7px',
                                      }}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2px' }}>
                                          <span style={{ fontSize: '9px', fontWeight: 700, fontFamily: 'JetBrains Mono, monospace', color: tc.border }}>
                                            {a.course.code}
                                          </span>
                                          <span style={{
                                            fontSize: '9px', padding: '1px 5px', borderRadius: '3px',
                                            background: `${tc.border}33`, color: tc.border,
                                            fontFamily: 'JetBrains Mono, monospace', fontWeight: 700,
                                          }}>{tc.label}</span>
                                        </div>
                                        <div style={{ fontSize: '10px', fontWeight: 500, lineHeight: 1.3, marginBottom: '3px', color: '#e2e8f0' }}>
                                          {a.course.name.length > 28 ? a.course.name.slice(0,27) + '…' : a.course.name}
                                        </div>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '9px', color: '#64748b', fontFamily: 'JetBrains Mono, monospace' }}>
                                          <span>{a.room.code}</span>
                                          <span>{a.timeSlot.startTime}–{a.timeSlot.endTime}</span>
                                        </div>
                                      </div>
                                    );
                                  })}
                                </div>
                              )}
                            </td>
                          );
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
