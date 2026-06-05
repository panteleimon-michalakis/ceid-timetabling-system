import { useEffect, useMemo, useState } from 'react';
import api from '../api/client';
import { timetableService } from '../api/services';
import { generateIcal, downloadIcal } from '../utils/icalExport';
import type { Teacher, Timetable, TimetableAssignment } from '../types';

// ─── Local types ──────────────────────────────────────────────────────────────

interface TeacherCourse {
  courseId: number; courseCode: string; courseName: string;
  semester?: number; studyYear?: number; role?: string | null;
}

interface DbConstraint {
  id?: number; dayOfWeek: string; hour: number;
  constraintType: 'BLOCKED' | 'PREFERRED';
}

type CellState = 'NEUTRAL' | 'PREFERRED' | 'BLOCKED';

// ─── Constants ────────────────────────────────────────────────────────────────

const DAYS    = ['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY'];
const DAY_LBL: Record<string,string> = {
  MONDAY:'Δευτέρα', TUESDAY:'Τρίτη', WEDNESDAY:'Τετάρτη',
  THURSDAY:'Πέμπτη', FRIDAY:'Παρασκευή',
};
const DAY_SHORT: Record<string,string> = {
  MONDAY:'Δευ', TUESDAY:'Τρί', WEDNESDAY:'Τετ', THURSDAY:'Πέμ', FRIDAY:'Παρ',
};
const HOURS = [9,10,11,12,13,14,15,16,17,18,19,20];

const TYPE_LABELS: Record<string,string> = {
  PROFESSOR:'Καθηγητής', ASSOCIATE_PROFESSOR:'Αν. Καθηγητής',
  ASSISTANT_PROFESSOR:'Επ. Καθηγητής', LECTURER:'Λέκτορας',
  EDIP:'ΕΔΙΠ', ETEP:'ΕΤΕΠ', EXTERNAL:'Εξωτερικός', APPOINTED:'Εντεταλμένος',
};
const TYPE_COLORS: Record<string,string> = {
  PROFESSOR:'#3b82f6', ASSOCIATE_PROFESSOR:'#8b5cf6',
  ASSISTANT_PROFESSOR:'#06b6d4', LECTURER:'#10b981',
  EDIP:'#f59e0b', ETEP:'#f59e0b', EXTERNAL:'#64748b', APPOINTED:'#64748b',
};
const TEACHER_TYPES = Object.keys(TYPE_LABELS);

const CYCLE: Record<CellState, CellState> = {
  NEUTRAL: 'PREFERRED', PREFERRED: 'BLOCKED', BLOCKED: 'NEUTRAL',
};

function tc(type?: string | null) { return TYPE_COLORS[type ?? ''] ?? '#64748b'; }

// ─── Component ────────────────────────────────────────────────────────────────

export default function Teachers() {
  const [teachers,      setTeachers]      = useState<Teacher[]>([]);
  const [selectedId,    setSelectedId]    = useState<number | null>(null);
  const [courses,       setCourses]       = useState<TeacherCourse[]>([]);
  const [activeTab,     setActiveTab]     = useState<'info'|'courses'|'availability'|'schedule'>('info');
  const [loading,       setLoading]       = useState(true);
  const [allTimetables,       setAllTimetables]       = useState<Timetable[]>([]);
  const [scheduleId,          setScheduleId]          = useState<number | null>(null);
  const [scheduleAssignments, setScheduleAssignments] = useState<TimetableAssignment[]>([]);
  const [loadingSchedule,     setLoadingSchedule]     = useState(false);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [search,        setSearch]        = useState('');

  // Edit teacher
  const [editing,  setEditing]  = useState(false);
  const [editForm, setEditForm] = useState<Partial<Teacher>>({});
  const [saving,   setSaving]   = useState(false);

  // Add teacher modal
  const [showAdd, setShowAdd]   = useState(false);
  const [addForm, setAddForm]   = useState<Partial<Teacher>>({ teacherType: 'PROFESSOR' });
  const [addSaving, setAddSaving] = useState(false);

  // Availability / constraints
  const [cellMap,     setCellMap]     = useState<Map<string, CellState>>(new Map());
  const [savedMap,    setSavedMap]    = useState<Map<string, CellState>>(new Map());
  const [savingCons,  setSavingCons]  = useState(false);

  const hasChanges = useMemo(() => {
    if (cellMap.size !== savedMap.size) return true;
    for (const [k, v] of cellMap) { if (savedMap.get(k) !== v) return true; }
    return false;
  }, [cellMap, savedMap]);

  // ── Load teachers ──────────────────────────────────────────────────────────
  useEffect(() => {
    api.get<Teacher[]>('/teachers')
      .then(r => setTeachers(r.data))
      .finally(() => setLoading(false));
    timetableService.getAll()
      .then(r => {
        const sem = (r.data as Timetable[]).filter((t: Timetable) => t.timetableType === 'SEMESTER');
        setAllTimetables(sem);
        if (sem.length > 0) setScheduleId(sem[0].id);
      });
  }, []);

  // ── Load detail when teacher selected ─────────────────────────────────────
  useEffect(() => {
    if (!selectedId) { setCourses([]); setCellMap(new Map()); setSavedMap(new Map()); return; }
    setLoadingDetail(true);
    setEditing(false);
    Promise.all([
      api.get<TeacherCourse[]>(`/teachers/${selectedId}/courses`),
      api.get<DbConstraint[]>(`/teachers/${selectedId}/constraints`),
    ]).then(([cR, conR]) => {
      setCourses(cR.data);
      const m = new Map<string, CellState>();
      for (const c of conR.data) {
        m.set(`${c.dayOfWeek}_${c.hour}`, c.constraintType as CellState);
      }
      setCellMap(new Map(m));
      setSavedMap(new Map(m));
    }).finally(() => setLoadingDetail(false));
  }, [selectedId]);

  useEffect(() => {
    if (!selectedId || !scheduleId || courses.length === 0) { setScheduleAssignments([]); return; }
    setLoadingSchedule(true);
    timetableService.getAssignments(scheduleId)
      .then(r => {
        const courseIds = new Set(courses.map(c => c.courseId));
        setScheduleAssignments((r.data as TimetableAssignment[]).filter(a => courseIds.has(a.course.id)));
      })
      .finally(() => setLoadingSchedule(false));
  }, [selectedId, scheduleId, courses]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return teachers;
    return teachers.filter(t =>
      t.name.toLowerCase().includes(q) ||
      t.email?.toLowerCase().includes(q) ||
      t.department?.toLowerCase().includes(q)
    );
  }, [teachers, search]);

  const selected = teachers.find(t => t.id === selectedId);

  // ── Actions ────────────────────────────────────────────────────────────────

  function startEdit() {
    if (!selected) return;
    setEditForm({ ...selected });
    setEditing(true);
  }

  async function saveEdit() {
    if (!selectedId) return;
    setSaving(true);
    try {
      const r = await api.put<Teacher>(`/teachers/${selectedId}`, editForm);
      setTeachers(prev => prev.map(t => t.id === selectedId ? r.data : t));
      setEditing(false);
    } finally { setSaving(false); }
  }

  async function deleteTeacher() {
    if (!selectedId || !selected) return;
    if (!confirm(`Διαγραφή καθηγητή "${selected.name}";\nΘα διαγραφούν και όλες οι αναθέσεις.`)) return;
    await api.delete(`/teachers/${selectedId}`);
    setTeachers(prev => prev.filter(t => t.id !== selectedId));
    setSelectedId(null);
  }

  async function addTeacher() {
    if (!addForm.name?.trim()) return;
    setAddSaving(true);
    try {
      const r = await api.post<Teacher>('/teachers', addForm);
      setTeachers(prev => [...prev, r.data]);
      setSelectedId(r.data.id);
      setShowAdd(false);
      setAddForm({ teacherType: 'PROFESSOR' });
    } finally { setAddSaving(false); }
  }

  function toggleCell(day: string, hour: number) {
    const key = `${day}_${hour}`;
    const cur = cellMap.get(key) ?? 'NEUTRAL';
    const next = CYCLE[cur];
    const m = new Map(cellMap);
    if (next === 'NEUTRAL') m.delete(key);
    else m.set(key, next);
    setCellMap(m);
  }

  async function saveConstraints() {
    if (!selectedId) return;
    setSavingCons(true);
    try {
      const body: DbConstraint[] = [];
      for (const [key, state] of cellMap) {
        if (state === 'NEUTRAL') continue;
        const [day, hourStr] = key.split('_');
        body.push({ dayOfWeek: day, hour: parseInt(hourStr), constraintType: state });
      }
      await api.put(`/teachers/${selectedId}/constraints`, body);
      setSavedMap(new Map(cellMap));
    } finally { setSavingCons(false); }
  }

  function resetConstraints() { setCellMap(new Map(savedMap)); }

  // ── Styles ─────────────────────────────────────────────────────────────────

  const panelStyle = {
    background: '#0d1b2e', border: '1px solid #1a2744',
    borderRadius: '12px', overflow: 'hidden', alignSelf: 'start' as const,
  };
  const inputSt = {
    width: '100%', padding: '7px 10px', background: '#080f1a',
    border: '1px solid #1a2744', borderRadius: '7px', color: '#e2e8f0',
    fontSize: '13px', boxSizing: 'border-box' as const, outline: 'none',
  };
  const btnPrimary = {
    padding: '7px 16px', border: 'none', borderRadius: '7px',
    background: '#1d4ed8', color: '#fff', fontWeight: 600, cursor: 'pointer', fontSize: '13px',
    fontFamily: "'IBM Plex Sans', sans-serif",
  };
  const btnSecondary = {
    padding: '7px 16px', border: 'none', borderRadius: '7px',
    background: '#1a2744', color: '#94a3b8', fontWeight: 500, cursor: 'pointer', fontSize: '13px',
    fontFamily: "'IBM Plex Sans', sans-serif",
  };
  const btnDanger = {
    padding: '7px 16px', border: 'none', borderRadius: '7px',
    background: 'rgba(239,68,68,0.1)', color: '#f87171', fontWeight: 500,
    cursor: 'pointer', fontSize: '13px', fontFamily: "'IBM Plex Sans', sans-serif",
  };

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div style={{
      padding: '40px 48px', background: '#080f1a',
      minHeight: 'calc(100vh - 52px)',
      fontFamily: "'IBM Plex Sans', sans-serif", color: '#e2e8f0',
      display: 'grid', gridTemplateColumns: '300px 1fr', gap: '24px',
    }}>
      <style>{`@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;400;500;600&family=JetBrains+Mono:wght@400;500&display=swap');
        input:focus, select:focus { border-color: #3b82f6 !important; }
        .cell-btn { transition: background 0.1s, border-color 0.1s; }
        .cell-btn:hover { filter: brightness(1.3); }
      `}</style>

      {/* ══ LEFT: list ══════════════════════════════════════════════════════ */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
          <div>
            <h1 style={{ fontSize: '1.4rem', fontWeight: 600, marginBottom: '2px', letterSpacing: '-0.3px' }}>Καθηγητές</h1>
            <p style={{ color: '#64748b', fontSize: '12px' }}>ΤΜΗΥΠ · Πανεπιστήμιο Πατρών</p>
          </div>
          <button onClick={() => setShowAdd(true)} style={{ ...btnPrimary, padding: '6px 12px', fontSize: '12px' }}>
            + Νέος
          </button>
        </div>

        <input value={search} onChange={e => setSearch(e.target.value)}
          placeholder="Αναζήτηση..." style={{ ...inputSt, marginBottom: '0' }} />

        {loading && <div style={{ color: '#334155', fontSize: '12px', fontFamily: 'JetBrains Mono, monospace' }}>Φόρτωση...</div>}

        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', overflowY: 'auto', maxHeight: 'calc(100vh - 260px)' }}>
          {filtered.map(t => (
            <div key={t.id} onClick={() => { setSelectedId(t.id); setActiveTab('info'); }}
              style={{
                padding: '9px 13px',
                background: selectedId === t.id ? '#1e3a5f' : '#0d1b2e',
                border: `1px solid ${selectedId === t.id ? '#3b82f6' : '#1a2744'}`,
                borderRadius: '8px', cursor: 'pointer',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '6px' }}>
                <span style={{ fontSize: '13px', fontWeight: 500, flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {t.name}
                </span>
                {t.teacherType && (
                  <span style={{
                    fontSize: '10px', padding: '2px 6px', borderRadius: '4px', flexShrink: 0,
                    background: `${tc(t.teacherType)}22`, color: tc(t.teacherType),
                    border: `1px solid ${tc(t.teacherType)}44`,
                    fontFamily: 'JetBrains Mono, monospace',
                  }}>{TYPE_LABELS[t.teacherType] ?? t.teacherType}</span>
                )}
              </div>
              {t.email && <div style={{ fontSize: '11px', color: '#475569', marginTop: '2px' }}>{t.email}</div>}
            </div>
          ))}
          {!loading && filtered.length === 0 && (
            <div style={{ color: '#334155', fontSize: '12px', textAlign: 'center', padding: '16px' }}>Δεν βρέθηκαν.</div>
          )}
        </div>
        <div style={{ fontSize: '11px', color: '#334155', fontFamily: 'JetBrains Mono, monospace' }}>
          {filtered.length}/{teachers.length}
        </div>
      </div>

      {/* ══ RIGHT: detail ═══════════════════════════════════════════════════ */}
      {!selected ? (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#334155', fontSize: '13px' }}>
          Επίλεξε καθηγητή για προβολή
        </div>
      ) : (
        <div style={panelStyle}>
          {/* Header */}
          <div style={{ padding: '20px 26px', borderBottom: '1px solid #1a2744', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <h2 style={{ fontSize: '1.2rem', fontWeight: 600, marginBottom: '7px', letterSpacing: '-0.3px' }}>
                {editing ? (
                  <input value={editForm.name ?? ''} onChange={e => setEditForm(f => ({...f, name: e.target.value}))}
                    style={{ ...inputSt, fontSize: '1.1rem', fontWeight: 600, width: '280px' }} />
                ) : selected.name}
              </h2>
              <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', alignItems: 'center' }}>
                {selected.teacherType && !editing && (
                  <span style={{
                    fontSize: '11px', padding: '3px 10px', borderRadius: '4px',
                    background: `${tc(selected.teacherType)}22`, color: tc(selected.teacherType),
                    border: `1px solid ${tc(selected.teacherType)}44`,
                    fontFamily: 'JetBrains Mono, monospace',
                  }}>{TYPE_LABELS[selected.teacherType] ?? selected.teacherType}</span>
                )}
                {editing && (
                  <select value={editForm.teacherType ?? ''} onChange={e => setEditForm(f => ({...f, teacherType: e.target.value}))}
                    style={{ ...inputSt, width: 'auto', padding: '4px 8px' }}>
                    {TEACHER_TYPES.map(t => <option key={t} value={t}>{TYPE_LABELS[t]}</option>)}
                  </select>
                )}
              </div>
            </div>
            <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
              {!editing ? (
                <>
                  <button onClick={startEdit} style={btnSecondary}>✏ Επεξεργασία</button>
                  <button onClick={deleteTeacher} style={btnDanger}>🗑 Διαγραφή</button>
                </>
              ) : (
                <>
                  <button onClick={saveEdit} disabled={saving} style={btnPrimary}>
                    {saving ? 'Αποθήκευση...' : '✓ Αποθήκευση'}
                  </button>
                  <button onClick={() => setEditing(false)} style={btnSecondary}>Ακύρωση</button>
                </>
              )}
            </div>
          </div>

          {/* Tabs */}
          <div style={{ display: 'flex', borderBottom: '1px solid #1a2744' }}>
            {(['info','courses','availability','schedule'] as const).map(tab => (
              <button key={tab} onClick={() => setActiveTab(tab)} style={{
                padding: '10px 20px', border: 'none', background: 'transparent',
                color: activeTab === tab ? '#e2e8f0' : '#475569', cursor: 'pointer',
                fontSize: '13px', fontWeight: activeTab === tab ? 600 : 400,
                borderBottom: `2px solid ${activeTab === tab ? '#3b82f6' : 'transparent'}`,
                fontFamily: "'IBM Plex Sans', sans-serif",
              }}>
                {tab === 'info' ? 'Στοιχεία'
                  : tab === 'courses' ? `Μαθήματα (${courses.length})`
                  : tab === 'availability' ? 'Διαθεσιμότητα'
                  : '📅 Πρόγραμμα'}
              </button>
            ))}
          </div>

          {/* Content */}
          <div style={{ padding: '22px 26px' }}>
            {loadingDetail && <div style={{ color: '#334155', fontSize: '12px', fontFamily: 'JetBrains Mono, monospace' }}>Φόρτωση...</div>}

            {/* ── INFO ── */}
            {!loadingDetail && activeTab === 'info' && (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                {[
                  { label: 'Email', field: 'email' as const, value: selected.email ?? '—' },
                  { label: 'Τμήμα', field: 'department' as const, value: selected.department ?? '—' },
                  { label: 'Short name', field: 'shortName' as const, value: selected.shortName ?? '—' },
                  { label: 'Σημειώσεις', field: 'notes' as const, value: (selected.notes?.startsWith('Αυτόματη') ? '—' : selected.notes) ?? '—' },
                ].map(({ label, field, value }) => (
                  <div key={label} style={{ background: '#111e33', borderRadius: '8px', padding: '12px 14px' }}>
                    <div style={{ fontSize: '10px', color: '#475569', marginBottom: '5px', textTransform: 'uppercase', letterSpacing: '0.8px', fontFamily: 'JetBrains Mono, monospace' }}>
                      {label}
                    </div>
                    {editing ? (
                      <input
                        value={(editForm as any)[field] ?? ''}
                        onChange={e => setEditForm(f => ({...f, [field]: e.target.value}))}
                        style={{ ...inputSt, marginBottom: 0 }}
                      />
                    ) : (
                      <div style={{ fontSize: '13px', color: '#e2e8f0' }}>{value}</div>
                    )}
                  </div>
                ))}
              </div>
            )}

            {/* ── COURSES ── */}
            {!loadingDetail && activeTab === 'courses' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                {courses.length === 0 && <div style={{ color: '#334155', fontSize: '13px' }}>Δεν βρέθηκαν μαθήματα.</div>}
                {courses.map(c => (
                  <div key={c.courseId} style={{
                    background: '#111e33', borderRadius: '8px', padding: '11px 14px',
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  }}>
                    <div>
                      <div style={{ fontSize: '13px', fontWeight: 500 }}>{c.courseName}</div>
                      <div style={{ fontSize: '11px', color: '#475569', fontFamily: 'JetBrains Mono, monospace', marginTop: '3px' }}>
                        {c.courseCode} · Εξ.{c.semester} · {c.studyYear}ο έτος
                      </div>
                    </div>
                    {c.role && (
                      <span style={{ fontSize: '10px', padding: '2px 8px', borderRadius: '4px', background: '#1a2744', color: '#64748b', fontFamily: 'JetBrains Mono, monospace' }}>
                        {c.role}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            )}

            {/* ── AVAILABILITY ── */}
            {!loadingDetail && activeTab === 'availability' && (
              <div>
                {/* Legend + save */}
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px', flexWrap: 'wrap', gap: '8px' }}>
                  <div style={{ display: 'flex', gap: '16px' }}>
                    {[
                      { bg: '#7f1d1d', border: '#ef4444', label: 'Blocked' },
                      { bg: '#052e16', border: '#22c55e', label: 'Preferred' },
                      { bg: '#111e33', border: '#1a2744', label: 'Ουδέτερο' },
                    ].map(({ bg, border, label }) => (
                      <div key={label} style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                        <div style={{ width: '14px', height: '14px', borderRadius: '3px', background: bg, border: `1px solid ${border}` }} />
                        <span style={{ fontSize: '12px', color: '#94a3b8' }}>{label}</span>
                      </div>
                    ))}
                    <span style={{ fontSize: '11px', color: '#334155', alignSelf: 'center' }}>Κλικ για εναλλαγή</span>
                  </div>
                  <div style={{ display: 'flex', gap: '6px' }}>
                    {hasChanges && (
                      <button onClick={resetConstraints} style={btnSecondary}>Αναίρεση</button>
                    )}
                    <button onClick={saveConstraints} disabled={savingCons || !hasChanges}
                      style={{ ...btnPrimary, opacity: hasChanges ? 1 : 0.4 }}>
                      {savingCons ? 'Αποθήκευση...' : '✓ Αποθήκευση αλλαγών'}
                    </button>
                  </div>
                </div>

                {/* Grid */}
                <div style={{ overflowX: 'auto' }}>
                  <table style={{ borderCollapse: 'collapse' }}>
                    <thead>
                      <tr>
                        <th style={{ padding: '6px 10px', fontSize: '11px', color: '#475569', textAlign: 'left', fontFamily: 'JetBrains Mono, monospace', fontWeight: 400, minWidth: '50px' }}>
                          Ώρα
                        </th>
                        {DAYS.map(d => (
                          <th key={d} style={{ padding: '6px 10px', fontSize: '12px', color: '#94a3b8', textAlign: 'center', fontWeight: 500 }}>
                            {DAY_SHORT[d]}
                            <div style={{ fontSize: '10px', color: '#475569', fontWeight: 400 }}>
                              {DAY_LBL[d].slice(3)}
                            </div>
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {HOURS.map(h => (
                        <tr key={h}>
                          <td style={{ padding: '3px 10px', fontSize: '11px', color: '#475569', fontFamily: 'JetBrains Mono, monospace' }}>
                            {String(h).padStart(2,'0')}:00
                          </td>
                          {DAYS.map(d => {
                            const key = `${d}_${h}`;
                            const state = cellMap.get(key) ?? 'NEUTRAL';
                            const changed = state !== (savedMap.get(key) ?? 'NEUTRAL');
                            return (
                              <td key={d} style={{ padding: '3px 5px', textAlign: 'center' }}>
                                <div
                                  className="cell-btn"
                                  onClick={() => toggleCell(d, h)}
                                  title={`${DAY_LBL[d]} ${h}:00 — ${state}${changed ? ' (μη αποθηκευμένο)' : ''}`}
                                  style={{
                                    width: '34px', height: '22px', borderRadius: '5px',
                                    margin: '0 auto', cursor: 'pointer',
                                    background:
                                      state === 'BLOCKED'   ? '#7f1d1d' :
                                      state === 'PREFERRED' ? '#052e16' : '#111e33',
                                    border: `1px solid ${
                                      state === 'BLOCKED'   ? '#ef4444' :
                                      state === 'PREFERRED' ? '#22c55e' : '#1a2744'
                                    }`,
                                    outline: changed ? '2px solid #fbbf24' : 'none',
                                    outlineOffset: '1px',
                                  }}
                                />
                              </td>
                            );
                          })}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Summary */}
                <div style={{ marginTop: '12px', fontSize: '11px', color: '#334155', fontFamily: 'JetBrains Mono, monospace' }}>
                  {Array.from(cellMap.values()).filter(v => v === 'BLOCKED').length} blocked ·{' '}
                  {Array.from(cellMap.values()).filter(v => v === 'PREFERRED').length} preferred
                  {hasChanges && <span style={{ color: '#fbbf24', marginLeft: '8px' }}>● Μη αποθηκευμένες αλλαγές</span>}
                </div>
              </div>
            )}

            {/* ── SCHEDULE ── */}
            {!loadingDetail && activeTab === 'schedule' && (
              <div>
                <div style={{ display:'flex', alignItems:'center', gap:'10px', marginBottom:'18px' }}>
                  <span style={{ fontSize:'11px', color:'#475569', fontFamily:'JetBrains Mono,monospace', textTransform:'uppercase', letterSpacing:'0.8px' }}>Πρόγραμμα</span>
                  <select value={scheduleId ?? ''} onChange={e => setScheduleId(Number(e.target.value))}
                    style={{ padding:'6px 10px', background:'#080f1a', border:'1px solid #1a2744', borderRadius:'7px', color:'#e2e8f0', fontSize:'13px', cursor:'pointer' }}>
                    {allTimetables.map(t => <option key={t.id} value={t.id}>{t.name} ({t.semesterType === 'FALL' ? 'Χειμ' : 'Εαρ'})</option>)}
                  </select>
                  <span style={{ fontSize:'11px', color:'#334155', fontFamily:'JetBrains Mono,monospace' }}>{loadingSchedule ? 'Φόρτωση...' : `${scheduleAssignments.length} ώρες/εβδ`}</span>
                  {scheduleAssignments.length > 0 && scheduleId && (() => {
                    const tt = allTimetables.find(t => t.id === scheduleId);
                    if (!tt) return null;
                    return (
                      <button onClick={() => downloadIcal(
                        `ceid-${(selected?.name ?? 'teacher').replace(/[\s.]+/g, '-')}.ics`,
                        generateIcal(scheduleAssignments, tt, `Πρόγραμμα ${selected?.name ?? ''}`)
                      )} style={{
                        padding:'5px 12px', border:'none', borderRadius:'6px', background:'#1d4ed8',
                        color:'#fff', fontSize:'11px', fontWeight:600, cursor:'pointer',
                        fontFamily:"'IBM Plex Sans',sans-serif",
                      }}>📥 iCal</button>
                    );
                  })()}
                </div>
                {scheduleAssignments.length > 0 && (() => {
                  const lec = scheduleAssignments.filter(a => a.assignmentType === 'LECTURE').length;
                  const tut = scheduleAssignments.filter(a => a.assignmentType === 'TUTORIAL').length;
                  const lab = scheduleAssignments.filter(a => a.assignmentType === 'LAB').length;
                  return (
                    <div style={{ display:'grid', gridTemplateColumns:'repeat(4,1fr)', gap:'10px', marginBottom:'20px' }}>
                      {[{label:'Θεωρία',val:lec,color:'#3b82f6'},{label:'Φροντιστήριο',val:tut,color:'#22c55e'},{label:'Εργαστήριο',val:lab,color:'#f59e0b'},{label:'Σύνολο',val:lec+tut+lab,color:'#94a3b8'}].map(s => (
                        <div key={s.label} style={{ background:'#111e33', borderRadius:'8px', padding:'12px 14px', borderTop:`2px solid ${s.color}` }}>
                          <div style={{ fontFamily:'JetBrains Mono,monospace', fontSize:'22px', fontWeight:600, color:s.color }}>{s.val}</div>
                          <div style={{ fontSize:'11px', color:'#64748b', marginTop:'3px' }}>{s.label} ώρ/εβδ</div>
                        </div>
                      ))}
                    </div>
                  );
                })()}
                {scheduleAssignments.length === 0 && !loadingSchedule && (
                  <div style={{ color:'#334155', fontSize:'13px', textAlign:'center', padding:'32px' }}>Δεν υπάρχουν αναθέσεις για αυτόν τον καθηγητή σε αυτό το πρόγραμμα.</div>
                )}
                {scheduleAssignments.length > 0 && (
                  <div style={{ overflowX:'auto' }}>
                    <table style={{ borderCollapse:'collapse', width:'100%', minWidth:'620px' }}>
                      <thead><tr>
                        <th style={{ padding:'7px 10px', fontSize:'10px', color:'#475569', fontFamily:'JetBrains Mono,monospace', fontWeight:400, textAlign:'left', width:'52px' }}>Ώρα</th>
                        {DAYS.map(d => (<th key={d} style={{ padding:'7px 10px', fontSize:'12px', color:'#94a3b8', textAlign:'center', fontWeight:500 }}>{DAY_SHORT[d]}<div style={{ fontSize:'10px', color:'#334155', fontWeight:400 }}>{DAY_LBL[d].slice(3)}</div></th>))}
                      </tr></thead>
                      <tbody>
                        {HOURS.map(h => (
                          <tr key={h}>
                            <td style={{ padding:'3px 10px 3px 0', fontSize:'11px', color:'#334155', fontFamily:'JetBrains Mono,monospace', verticalAlign:'top', paddingTop:'6px' }}>{String(h).padStart(2,'0')}:00</td>
                            {DAYS.map(d => {
                              const items = scheduleAssignments.filter(a => a.timeSlot?.dayOfWeek === d && a.timeSlot?.startTime?.startsWith(String(h).padStart(2,'0')));
                              return (
                                <td key={d} style={{ padding:'3px 4px', verticalAlign:'top', borderTop:'1px solid #0f1f38', minWidth:'110px' }}>
                                  {items.map(a => {
                                    const color = a.assignmentType==='LECTURE'?'#3b82f6':a.assignmentType==='TUTORIAL'?'#22c55e':'#f59e0b';
                                    const bg = a.assignmentType==='LECTURE'?'#1e3a5f':a.assignmentType==='TUTORIAL'?'#14532d':'#451a03';
                                    const lbl = a.assignmentType==='LECTURE'?'Θ':a.assignmentType==='TUTORIAL'?'Φ':'Ε';
                                    return (
                                      <div key={a.id} style={{ background:bg, border:`1px solid ${color}`, borderRadius:'5px', padding:'4px 6px', marginBottom:'2px' }}>
                                        <div style={{ display:'flex', justifyContent:'space-between' }}>
                                          <span style={{ fontSize:'9px', fontFamily:'JetBrains Mono,monospace', color, fontWeight:700 }}>{a.course?.code}</span>
                                          <span style={{ fontSize:'9px', background:`${color}33`, color, borderRadius:'3px', padding:'0 4px', fontWeight:700 }}>{lbl}</span>
                                        </div>
                                        <div style={{ fontSize:'10px', color:'#cbd5e1', lineHeight:1.2 }}>{(a.course?.name?.length??0)>22?a.course.name.slice(0,21)+'…':a.course?.name}</div>
                                        <div style={{ fontSize:'9px', color:'#475569', fontFamily:'JetBrains Mono,monospace', marginTop:'2px' }}>{a.room?.code}</div>
                                      </div>
                                    );
                                  })}
                                </td>
                              );
                            })}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {/* ══ ADD TEACHER MODAL ═══════════════════════════════════════════════ */}
      {showAdd && (
        <div onClick={() => setShowAdd(false)} style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.75)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
        }}>
          <div onClick={e => e.stopPropagation()} style={{
            background: '#0d1b2e', border: '1px solid #1a2744',
            borderRadius: '12px', padding: '28px', width: '440px',
          }}>
            <h2 style={{ fontSize: '1.1rem', fontWeight: 600, marginBottom: '20px' }}>Νέος Καθηγητής</h2>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {[
                { label: 'Ονοματεπώνυμο *', field: 'name', placeholder: 'π.χ. Κ. Βλάχος' },
                { label: 'Email', field: 'email', placeholder: 'email@upatras.gr' },
                { label: 'Τμήμα', field: 'department', placeholder: 'ΤΜΗΥΠ' },
              ].map(({ label, field, placeholder }) => (
                <div key={field}>
                  <label style={{ fontSize: '11px', color: '#64748b', display: 'block', marginBottom: '4px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                    {label}
                  </label>
                  <input
                    value={(addForm as any)[field] ?? ''}
                    onChange={e => setAddForm(f => ({...f, [field]: e.target.value}))}
                    placeholder={placeholder}
                    style={inputSt}
                  />
                </div>
              ))}

              <div>
                <label style={{ fontSize: '11px', color: '#64748b', display: 'block', marginBottom: '4px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                  Βαθμίδα
                </label>
                <select value={addForm.teacherType ?? 'PROFESSOR'}
                  onChange={e => setAddForm(f => ({...f, teacherType: e.target.value}))}
                  style={{ ...inputSt }}>
                  {TEACHER_TYPES.map(t => <option key={t} value={t}>{TYPE_LABELS[t]}</option>)}
                </select>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end', marginTop: '20px' }}>
              <button onClick={() => setShowAdd(false)} style={btnSecondary}>Ακύρωση</button>
              <button onClick={addTeacher} disabled={addSaving || !addForm.name?.trim()} style={{ ...btnPrimary, opacity: addForm.name?.trim() ? 1 : 0.5 }}>
                {addSaving ? 'Δημιουργία...' : 'Δημιουργία'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
