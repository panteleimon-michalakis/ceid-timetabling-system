import { useEffect, useMemo, useState } from 'react';
import { courseService, courseTeacherService, teacherService } from '../api/services';
import DualListPicker from '../components/DualListPicker';
import type { DualListSelection } from '../components/DualListPicker';
import { useAuth } from '../context/AuthContext';
import type { Course, Teacher } from '../types';
import { TEACHER_ROLES } from '../types';

// ─── Constants ────────────────────────────────────────────────────────────────

const TYPE_CONFIG: Record<string, { label: string; color: string; bg: string }> = {
  REQUIRED:          { label: 'Υποχρεωτικό',  color: '#3b82f6', bg: '#1e3a5f22' },
  REQUIRED_ELECTIVE: { label: 'Επιλογής',      color: '#f59e0b', bg: '#78350f22' },
  GENERAL_EDUCATION: { label: 'Γεν. Παιδείας', color: '#10b981', bg: '#05311e22' },
  EXTERNAL:          { label: 'Εξωτερικό',     color: '#8b5cf6', bg: '#3b0d8022' },
};

const SEM_TYPE: Record<string, string> = { FALL: 'Χειμ', SPRING: 'Εαρ', BOTH: 'Αμφ' };

const COURSE_TYPES   = ['REQUIRED','REQUIRED_ELECTIVE','GENERAL_EDUCATION','EXTERNAL'];
const SECTORS        = ['ΕΒ','ΛΥ','ΥΑ'];

// ─── Styles ───────────────────────────────────────────────────────────────────

const S = `
@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;400;500;600&family=JetBrains+Mono:wght@400;500&display=swap');
.cr-root{font-family:'IBM Plex Sans',sans-serif;min-height:calc(100vh - 52px);background:#080f1a;color:#e2e8f0;padding:36px 48px;}
.cr-search{padding:6px 12px;border-radius:7px;border:1px solid #1a2744;background:#0d1b2e;color:#e2e8f0;font-size:13px;font-family:'IBM Plex Sans',sans-serif;width:240px;}
.cr-search:focus{outline:none;border-color:#1d4ed8;}
.cr-btn{padding:5px 12px;border:none;border-radius:6px;cursor:pointer;font-size:12px;font-family:'IBM Plex Sans',sans-serif;font-weight:500;}
.cr-btn-active{background:#1d4ed8;color:#fff;}
.cr-btn-inactive{background:#0d1b2e;color:#64748b;border:1px solid #1a2744;}
.cr-btn-inactive:hover{color:#94a3b8;border-color:#1e3a5f;}
.cr-table-wrap{overflow-x:auto;border-radius:10px;border:1px solid #1a2744;}
.cr-table{width:100%;border-collapse:collapse;font-size:13px;}
.cr-th{padding:10px 14px;background:#0a1628;color:#475569;font-size:10px;text-transform:uppercase;letter-spacing:1px;font-family:'JetBrains Mono',monospace;font-weight:500;text-align:left;white-space:nowrap;border-bottom:1px solid #1a2744;}
.cr-tr{border-bottom:1px solid #0f1f38;transition:background 0.1s;}
.cr-tr:hover{background:#0d1b2e;}
.cr-td{padding:9px 14px;vertical-align:middle;}
.cr-code{font-family:'JetBrains Mono',monospace;font-size:11px;color:#64748b;}
.cr-badge{display:inline-block;padding:2px 8px;border-radius:4px;font-size:10px;font-weight:600;font-family:'JetBrains Mono',monospace;}
.cr-action-btn{padding:3px 8px;border:none;border-radius:4px;cursor:pointer;font-size:11px;font-family:'IBM Plex Sans',sans-serif;font-weight:500;}
.overlay{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.75);display:flex;align-items:center;justify-content:center;z-index:1000;}
.modal{background:#0d1b2e;border:1px solid #1a2744;border-radius:12px;padding:28px 32px;width:640px;max-height:88vh;overflow-y:auto;}
.modal-title{font-size:1.1rem;font-weight:600;margin-bottom:20px;color:#f1f5f9;}
.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px;}
.form-group{display:flex;flex-direction:column;gap:5px;}
.form-label{font-size:11px;color:#94a3b8;font-weight:500;}
.form-input{padding:7px 10px;border-radius:7px;border:1px solid #1a2744;background:#080f1a;color:#e2e8f0;font-size:13px;font-family:'IBM Plex Sans',sans-serif;}
.form-input:focus{outline:none;border-color:#1d4ed8;}
.form-select{padding:7px 10px;border-radius:7px;border:1px solid #1a2744;background:#080f1a;color:#e2e8f0;font-size:13px;font-family:'IBM Plex Sans',sans-serif;}
.btn-primary{padding:8px 20px;border:none;border-radius:8px;background:#1d4ed8;color:#fff;font-weight:600;cursor:pointer;font-size:13px;font-family:'IBM Plex Sans',sans-serif;}
.btn-secondary{padding:8px 20px;border:none;border-radius:8px;background:#1a2744;color:#94a3b8;font-weight:600;cursor:pointer;font-size:13px;font-family:'IBM Plex Sans',sans-serif;}
.btn-danger{padding:8px 16px;border:none;border-radius:8px;background:#450a0a;color:#f87171;font-weight:600;cursor:pointer;font-size:13px;border:1px solid #7f1d1d;}
`;

// ─── Empty course template ────────────────────────────────────────────────────

const EMPTY_COURSE: Partial<Course> = {
  code: '', name: '', semester: 1, studyYear: 1,
  courseType: 'REQUIRED', lectureHours: 3, tutorialHours: 2, labHours: 0,
  ects: 6, sector: 'ΕΒ', semesterType: 'FALL', expectedStudents: 0,
  teachersText: '', active: true, visibleInTimetable: true,
  preferredExamRooms: '', preferredExamHours: '',
};

// ─── Course Modal ─────────────────────────────────────────────────────────────

function CourseModal({ course, onClose, onSaved }: {
  course: Partial<Course> | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<Partial<Course>>(course ?? EMPTY_COURSE);
  const [saving, setSaving] = useState(false);
  const [error,  setError]  = useState('');
  const [allTeachers, setAllTeachers] = useState<Teacher[]>([]);
  const [teacherSel,  setTeacherSel]  = useState<DualListSelection[]>([]);
  const isNew = !form.id;

  const set = (k: keyof Course, v: unknown) => setForm(f => ({ ...f, [k]: v }));

  // fetch-on-open: όλο το universe καθηγητών + (αν edit) η τρέχουσα M2M επιλογή.
  useEffect(() => {
    let cancelled = false;
    teacherService.getAll()
      .then(ts => { if (!cancelled) setAllTeachers(ts); })
      .catch(() => {});
    if (course?.id) {
      courseTeacherService.getForCourse(course.id)
        .then(refs => { if (!cancelled) setTeacherSel(refs.map(r => ({ id: r.teacherId, role: r.role }))); })
        .catch(() => {});
    } else {
      setTeacherSel([]);
    }
    return () => { cancelled = true; };
  }, [course?.id]);

  async function handleSave() {
    if (!form.code?.trim() || !form.name?.trim()) {
      setError('Κωδικός και Όνομα είναι υποχρεωτικά.'); return;
    }
    setSaving(true);
    setError('');
    // ΣΕΙΡΑ ΥΠΟΧΡΕΩΤΙΚΗ: το M2M PUT πάντα ΤΕΛΕΥΤΑΙΟ → η derived αναπαραγωγή του
    // teachersText κερδίζει το (αμετάβλητο) payload του course update.
    const m2mBody = teacherSel.map(s => ({ teacherId: s.id, role: s.role }));
    try {
      if (isNew) {
        const res = await courseService.create(form as Course);
        const newId = res.data.id;
        // Μετέτρεψε το modal σε edit-state ώστε ένα retry να ΜΗΝ ξανα-δημιουργεί.
        setForm(f => ({ ...f, id: newId }));
        try {
          await courseTeacherService.setForCourse(newId, m2mBody);
        } catch {
          onSaved();
          setError('Το μάθημα δημιουργήθηκε, αλλά οι διδάσκοντες δεν αποθηκεύτηκαν — δοκίμασε ξανά.');
          return;
        }
      } else {
        // form.teachersText στέλνεται ΑΜΕΤΑΒΛΗΤΟ (derived· δεν επεξεργάζεται πια από τον χρήστη).
        await courseService.update(form.id!, form as Course);
        try {
          await courseTeacherService.setForCourse(form.id!, m2mBody);
        } catch {
          onSaved();
          setError('Το μάθημα ενημερώθηκε, αλλά οι διδάσκοντες δεν αποθηκεύτηκαν — δοκίμασε ξανά.');
          return;
        }
      }
      onSaved();
      onClose();
    } catch (e: any) {
      setError(e?.response?.data?.error ?? 'Σφάλμα αποθήκευσης.');
    } finally { setSaving(false); }
  }

  return (
    <div className="overlay" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <div className="modal-title">{isNew ? '+ Νέο Μάθημα' : 'Επεξεργασία Μαθήματος'}</div>

        <div className="form-grid">
          <div className="form-group">
            <label className="form-label">Κωδικός *</label>
            <input className="form-input" value={form.code ?? ''} onChange={e => set('code', e.target.value)} placeholder="π.χ. CEID_22Y101" />
          </div>
          <div className="form-group">
            <label className="form-label">Τύπος</label>
            <select className="form-select" value={form.courseType ?? 'REQUIRED'} onChange={e => set('courseType', e.target.value)}>
              {COURSE_TYPES.map(t => <option key={t} value={t}>{TYPE_CONFIG[t]?.label ?? t}</option>)}
            </select>
          </div>
          <div className="form-group" style={{ gridColumn: '1 / -1' }}>
            <label className="form-label">Όνομα *</label>
            <input className="form-input" value={form.name ?? ''} onChange={e => set('name', e.target.value)} placeholder="π.χ. Διακριτά Μαθηματικά" />
          </div>
          <div className="form-group">
            <label className="form-label">Εξάμηνο</label>
            <input className="form-input" type="number" min={1} max={10} value={form.semester ?? 1} onChange={e => set('semester', +e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label">Έτος Σπουδών</label>
            <input className="form-input" type="number" min={1} max={5} value={form.studyYear ?? 1} onChange={e => set('studyYear', +e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label">Ώρες Θεωρίας</label>
            <input className="form-input" type="number" min={0} value={form.lectureHours ?? 0} onChange={e => set('lectureHours', +e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label">Ώρες Φροντιστηρίου</label>
            <input className="form-input" type="number" min={0} value={form.tutorialHours ?? 0} onChange={e => set('tutorialHours', +e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label">Ώρες Εργαστηρίου</label>
            <input className="form-input" type="number" min={0} value={form.labHours ?? 0} onChange={e => set('labHours', +e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label">ECTS</label>
            <input className="form-input" type="number" min={0} value={form.ects ?? 0} onChange={e => set('ects', +e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label">Εξάμηνο Τύπος</label>
            <select className="form-select" value={form.semesterType ?? 'FALL'} onChange={e => set('semesterType', e.target.value)}>
              <option value="FALL">Χειμερινό</option>
              <option value="SPRING">Εαρινό</option>
              <option value="BOTH">Αμφότερα</option>
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Τομέας</label>
            <select className="form-select" value={form.sector ?? 'ΕΒ'} onChange={e => set('sector', e.target.value)}>
              {SECTORS.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Αναμενόμενοι Φοιτητές</label>
            <input className="form-input" type="number" min={0} value={form.expectedStudents ?? 0} onChange={e => set('expectedStudents', +e.target.value)} />
          </div>
          <div className="form-group" style={{ gridColumn: '1 / -1' }}>
            <label className="form-label">Διδάσκοντες</label>
            <DualListPicker
              available={allTeachers.map(t => ({ id: t.id, label: t.name, sublabel: t.shortName ?? t.department ?? '' }))}
              selected={teacherSel}
              onChange={setTeacherSel}
              roleOptions={TEACHER_ROLES}
              defaultRoleForNew={cur => (cur.length === 0 ? 'PRIMARY' : 'SECONDARY')}
              warning={sel => {
                const p = sel.filter(s => s.role === 'PRIMARY').length;
                if (p === 0) return 'Προσοχή: το μάθημα δεν έχει κύριο διδάσκοντα.';
                if (p > 1)  return 'Προσοχή: περισσότεροι από ένας κύριοι διδάσκοντες.';
                return null;
              }}
              labels={{ availableTitle: 'Διαθέσιμοι καθηγητές', selectedTitle: 'Επιλεγμένοι', emptySelected: 'Κανένας διδάσκων' }}
            />
          </div>
          <div className="form-group">
            <label className="form-label">Ενεργό</label>
            <select className="form-select" value={form.active ? 'true' : 'false'} onChange={e => set('active', e.target.value === 'true')}>
              <option value="true">Ναι</option>
              <option value="false">Όχι</option>
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Εμφάνιση στο Πρόγραμμα</label>
            <select className="form-select" value={form.visibleInTimetable ? 'true' : 'false'} onChange={e => set('visibleInTimetable', e.target.value === 'true')}>
              <option value="true">Ναι</option>
              <option value="false">Όχι</option>
            </select>
          </div>
        </div>

        <div className="form-row" style={{ marginTop: 10 }}>
          <div style={{ flex: 1 }}>
            <label className="form-label">Προτιμώμενες αίθουσες εξέτασης (CSV)</label>
            <input className="form-input" placeholder="π.χ. Β,Δ1"
              value={form.preferredExamRooms ?? ''}
              onChange={e => set('preferredExamRooms', e.target.value)} />
          </div>
          <div style={{ flex: 1 }}>
            <label className="form-label">Προτιμώμενες ώρες εξέτασης (CSV)</label>
            <input className="form-input" placeholder="π.χ. 9,12"
              value={form.preferredExamHours ?? ''}
              onChange={e => set('preferredExamHours', e.target.value)} />
          </div>
        </div>

        {error && <div style={{ color:'#f87171', fontSize:12, marginTop:12 }}>{error}</div>}

        <div style={{ display:'flex', justifyContent:'flex-end', gap:10, marginTop:20 }}>
          <button className="btn-secondary" onClick={onClose}>Ακύρωση</button>
          <button className="btn-primary" onClick={handleSave} disabled={saving}>
            {saving ? 'Αποθήκευση...' : 'Αποθήκευση'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Main Page ────────────────────────────────────────────────────────────────

export default function Courses() {
  const { user } = useAuth();
  const isAdmin  = user?.role === 'ADMIN';

  const [courses,    setCourses]    = useState<Course[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [semFilter,  setSemFilter]  = useState(0);
  const [yearFilter, setYearFilter] = useState(0);
  const [search,     setSearch]     = useState('');
  const [editing,    setEditing]    = useState<Partial<Course> | null | false>(false);
  const [toast,      setToast]      = useState('');

  function load() {
    setLoading(true);
    courseService.getAll()
      .then(r => setCourses(r.data))
      .finally(() => setLoading(false));
  }

  useEffect(() => { load(); }, []);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(''), 3000);
    return () => clearTimeout(t);
  }, [toast]);

  async function handleDelete(c: Course) {
    if (!confirm(`Διαγραφή μαθήματος "${c.name}";\nΠροσοχή: θα διαγραφούν και οι αναθέσεις του.`)) return;
    try {
      await courseService.delete(c.id);
      setToast('Το μάθημα διαγράφηκε.');
      load();
    } catch { setToast('Σφάλμα διαγραφής.'); }
  }

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return courses.filter(c => {
      if (typeFilter !== 'ALL' && c.courseType !== typeFilter) return false;
      if (semFilter  > 0 && c.semester   !== semFilter)  return false;
      if (yearFilter > 0 && c.studyYear  !== yearFilter) return false;
      if (q && !c.name.toLowerCase().includes(q) && !c.code.toLowerCase().includes(q)) return false;
      return true;
    });
  }, [courses, typeFilter, semFilter, yearFilter, search]);

  return (
    <div className="cr-root">
      <style>{S}</style>

      {/* Toast */}
      {toast && (
        <div style={{ position:'fixed', top:16, right:16, background:'#052e16', border:'1px solid #22c55e',
          borderRadius:8, padding:'12px 20px', color:'#4ade80', fontSize:13, zIndex:3000 }}>
          {toast}
        </div>
      )}

      {/* Header */}
      <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-start', marginBottom:24 }}>
        <div>
          <div style={{ fontSize:22, fontWeight:600, letterSpacing:'-0.4px', marginBottom:4 }}>Μαθήματα</div>
          <div style={{ fontSize:13, color:'#475569' }}>Κατάλογος μαθημάτων ΤΜΗΥΠ · {courses.length} συνολικά</div>
        </div>
        {isAdmin && (
          <button className="btn-primary" onClick={() => setEditing(EMPTY_COURSE)}>
            + Νέο Μάθημα
          </button>
        )}
      </div>

      {/* Filters */}
      <div style={{ display:'flex', flexWrap:'wrap', gap:10, marginBottom:18, alignItems:'center' }}>
        <div style={{ display:'flex', gap:4 }}>
          {(['ALL','REQUIRED','REQUIRED_ELECTIVE','GENERAL_EDUCATION','EXTERNAL'] as const).map(t => (
            <button key={t} className={`cr-btn ${typeFilter===t?'cr-btn-active':'cr-btn-inactive'}`}
              onClick={() => setTypeFilter(t)}>
              {t === 'ALL' ? 'Όλα' : TYPE_CONFIG[t]?.label}
            </button>
          ))}
        </div>
        <div style={{ display:'flex', gap:4 }}>
          {[0,1,2,3,4,5].map(y => (
            <button key={y} className={`cr-btn ${yearFilter===y?'cr-btn-active':'cr-btn-inactive'}`}
              onClick={() => setYearFilter(y)}>
              {y===0?'Όλα':y+'ο'}
            </button>
          ))}
        </div>
        <div style={{ display:'flex', gap:4 }}>
          <button className={`cr-btn ${semFilter===0?'cr-btn-active':'cr-btn-inactive'}`}
            onClick={() => setSemFilter(0)}>Όλα Εξ.</button>
          {[1,2,3,4,5,6,7,8,9,10].map(s => (
            <button key={s} className={`cr-btn ${semFilter===s?'cr-btn-active':'cr-btn-inactive'}`}
              onClick={() => setSemFilter(s)}>Εξ.{s}</button>
          ))}
        </div>
        <input className="cr-search" placeholder="Αναζήτηση..." value={search} onChange={e => setSearch(e.target.value)} />
        <span style={{ fontSize:11, color:'#334155', fontFamily:'JetBrains Mono,monospace' }}>{filtered.length} αποτελέσματα</span>
      </div>

      {/* Table */}
      {loading ? (
        <div style={{ color:'#334155', fontSize:13, padding:32, textAlign:'center' }}>Φόρτωση...</div>
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
                <th className="cr-th">Θ/Φ/Ε</th>
                <th className="cr-th">ECTS</th>
                <th className="cr-th">Τομέας</th>
                <th className="cr-th">Φοιτητές</th>
                <th className="cr-th">Διδάσκοντες</th>
                {isAdmin && <th className="cr-th">Ενέργειες</th>}
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 ? (
                <tr><td colSpan={isAdmin ? 12 : 11} style={{ padding:32, textAlign:'center', color:'#334155' }}>
                  Δεν βρέθηκαν μαθήματα.
                </td></tr>
              ) : filtered.map(c => {
                const tc = TYPE_CONFIG[c.courseType] ?? { label: c.courseType, color:'#64748b', bg:'#1a274422' };
                return (
                  <tr key={c.id} className="cr-tr">
                    <td className="cr-td"><span className="cr-code">{c.code}</span></td>
                    <td className="cr-td" style={{ fontWeight:500, maxWidth:260 }}>{c.name}</td>
                    <td className="cr-td" style={{ textAlign:'center' }}><span className="cr-code">{c.semester}</span></td>
                    <td className="cr-td" style={{ textAlign:'center' }}><span className="cr-code">{c.studyYear}ο</span></td>
                    <td className="cr-td">
                      <span className="cr-badge" style={{ background:tc.bg, color:tc.color }}>{tc.label}</span>
                    </td>
                    <td className="cr-td" style={{ textAlign:'center' }}>
                      <span className="cr-code">{SEM_TYPE[c.semesterType ?? ''] ?? c.semesterType}</span>
                    </td>
                    <td className="cr-td"><span className="cr-code">{c.lectureHours??0}/{c.tutorialHours??0}/{c.labHours??0}</span></td>
                    <td className="cr-td" style={{ textAlign:'center' }}><span className="cr-code">{c.ects}</span></td>
                    <td className="cr-td" style={{ textAlign:'center' }}><span className="cr-code">{c.sector??'—'}</span></td>
                    <td className="cr-td" style={{ textAlign:'center' }}><span className="cr-code">{c.expectedStudents??'—'}</span></td>
                    <td className="cr-td">
                      <span style={{ fontSize:11, color:'#475569', maxWidth:180, overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap', display:'block' }}
                        title={c.teachersText}>{c.teachersText || '—'}</span>
                    </td>
                    {isAdmin && (
                      <td className="cr-td">
                        <div style={{ display:'flex', gap:6 }}>
                          <button className="cr-action-btn"
                            style={{ background:'#1e3a5f', color:'#60a5fa' }}
                            onClick={() => setEditing(c)}>
                            Επεξ.
                          </button>
                          <button className="cr-action-btn"
                            style={{ background:'#450a0a', color:'#f87171' }}
                            onClick={() => handleDelete(c)}>
                            Διαγρ.
                          </button>
                        </div>
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Modal */}
      {editing !== false && (
        <CourseModal
          course={editing}
          onClose={() => setEditing(false)}
          onSaved={() => { load(); setToast('Το μάθημα αποθηκεύτηκε.'); }}
        />
      )}
    </div>
  );
}
