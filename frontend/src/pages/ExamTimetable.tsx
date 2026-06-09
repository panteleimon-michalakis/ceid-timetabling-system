import { useEffect, useMemo, useState } from 'react';
import type { CSSProperties } from 'react';
import { courseService, roomService, timeSlotService, timetableService } from '../api/services';
import type {
  Course, PlacementOption, PlacementOptionsResponse,
  Room, TimeSlot, Timetable,
  TimetableAssignment, TimetableProgress, TimetableValidationReport,
} from '../types';
import TimetableSelector from '../components/TimetableSelector';

// ─── Constants ────────────────────────────────────────────────────────────────

const EXAM_HOURS = ['09:00','10:00','11:00','12:00','13:00','14:00','15:00','16:00','17:00','18:00','19:00','20:00'];
const EXAM_DURATIONS = [
  { label: '1 ώρα',    value: 60  },
  { label: '1.5 ώρες', value: 90  },
  { label: '2 ώρες',   value: 120 },
  { label: '3 ώρες',   value: 180 },
  { label: '4 ώρες',   value: 240 },
];
const YEAR_COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899'];

// ─── Helpers ──────────────────────────────────────────────────────────────────

function normalizeTime(v?: string | null) {
  if (!v) return '';
  return v.length >= 5 ? v.slice(0, 5) : v;
}

function formatDateHeader(dateStr: string) {
  try {
    const d = new Date(dateStr + 'T00:00:00');
    const day = d.toLocaleDateString('el-GR', { weekday: 'short' });
    const num = d.getDate();
    const mon = d.getMonth() + 1;
    return `${day} ${num}/${mon}`;
  } catch { return dateStr; }
}

function getEligibleCourses(courses: Course[], timetable: Timetable | undefined) {
  if (!timetable) return [];
  const sem = timetable.semesterType;
  return courses
    .filter(c => {
      if (c.active === false) return false;
      if (c.visibleInTimetable === false) return false;
      if (sem === 'SEPTEMBER') return true;
      if ((c.semesterType as string) === 'BOTH') return true;
      if (sem === 'FALL' && (c.semesterType as string) === 'FALL') return true;
      if (sem === 'SPRING' && (c.semesterType as string) === 'SPRING') return true;
      return false;
    })
    .sort((a, b) => a.semester - b.semester || a.name.localeCompare(b.name, 'el'));
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function StatCard({ label, value, color }: { label: string; value: number | string; color: string }) {
  return (
    <div style={{ padding: '1rem', background: '#0d1b2e', border: '1px solid #1a2744', borderTop: `2px solid ${color}`, borderRadius: '10px' }}>
      <div style={{ fontSize: '22px', fontWeight: 600, color, fontFamily: 'JetBrains Mono, monospace' }}>{value}</div>
      <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>{label}</div>
    </div>
  );
}

function ExamCard({
  assignment, onDelete, onMove, onDragStart, onDragEnd, disabled,
}: {
  assignment: TimetableAssignment;
  onDelete: () => void;
  onMove: () => void;
  onDragStart: () => void;
  onDragEnd: () => void;
  disabled: boolean;
}) {
  const color = YEAR_COLORS[(assignment.course?.studyYear ?? 1) - 1] ?? '#3b82f6';
  return (
    <div
      onClick={e => e.stopPropagation()}
      draggable={true}
      onDragStart={e => { e.stopPropagation(); onDragStart(); }}
      onDragEnd={e => { e.stopPropagation(); onDragEnd(); }}
      style={{ background: '#0a1628', borderLeft: `3px solid ${color}`, borderRadius: '6px', padding: '6px 8px', color: '#e2e8f0', cursor:         'grab' }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: '4px' }}>
        <div style={{ fontWeight: 700, fontSize: '11px', fontFamily: 'JetBrains Mono, monospace', color }}>
          {assignment.course?.code}
        </div>
        <div style={{ display: 'flex', gap: '3px' }}>
          <button
            onClick={e => { e.stopPropagation(); onMove(); }}
            disabled={disabled}
            title="Μετακίνηση"
            style={{ border: 'none', borderRadius: '4px', background: 'rgba(59,130,246,0.2)', color: '#93c5fd', cursor: 'pointer', padding: '1px 5px', fontSize: '11px' }}
          >⇄</button>
          <button
            onClick={e => { e.stopPropagation(); onDelete(); }}
            disabled={disabled}
            title="Διαγραφή"
            style={{ border: 'none', borderRadius: '4px', background: 'rgba(239,68,68,0.15)', color: '#f87171', cursor: 'pointer', padding: '1px 5px', fontSize: '11px' }}
          >×</button>
        </div>
      </div>
      <div style={{ fontSize: '11px', lineHeight: 1.2, marginTop: '2px', color: '#cbd5e1' }}>{assignment.course?.name}</div>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '10px', marginTop: '3px', color: '#475569' }}>
        <span style={{ fontFamily: 'JetBrains Mono, monospace' }}>{assignment.room?.code}</span>
        <span style={{ fontFamily: 'JetBrains Mono, monospace' }}>
          {assignment.examDurationMinutes ? `${assignment.examDurationMinutes/60}h` : '3h'}
        </span>
      </div>
    </div>
  );
}

function ExamMoveModal({
  assignment, rooms, examTimeSlots, onMoved, onClose, onError, onSuccess,
}: {
  assignment: TimetableAssignment;
  rooms: Room[];
  examTimeSlots: TimeSlot[];
  onMoved: () => void;
  onClose: () => void;
  onError: (msg: string) => void;
  onSuccess: (msg: string) => void;
}) {
  const currentDate = assignment.timeSlot?.specificDate ?? '';
  const currentHour = normalizeTime(assignment.timeSlot?.startTime);
  const currentRoomId = assignment.room?.id ?? 0;

  const [selDate, setSelDate] = useState(currentDate);
  const [selHour, setSelHour] = useState(currentHour);
  const [selRoomId, setSelRoomId] = useState(currentRoomId);
  const [saving, setSaving] = useState(false);

  const availableDates = useMemo(() => {
    const s = new Set<string>();
    for (const ts of examTimeSlots) { if (ts.specificDate) s.add(ts.specificDate); }
    return Array.from(s).sort();
  }, [examTimeSlots]);

  const hasChanged = selDate !== currentDate || selHour !== currentHour || selRoomId !== currentRoomId;

  function getSlotId(date: string, hour: string) {
    return examTimeSlots.find(ts => ts.specificDate === date && normalizeTime(ts.startTime) === hour)?.id;
  }

  async function handleMove() {
    if (!hasChanged) { onClose(); return; }
    const slotId = getSlotId(selDate, selHour);
    if (!slotId) { onError('Δεν βρέθηκε χρονοθυρίδα για αυτή την ημερομηνία/ώρα.'); return; }
    setSaving(true);
    try {
      await timetableService.moveAssignment(assignment.id, { timeSlotId: slotId, roomId: selRoomId });
      onSuccess(`Το μάθημα ${assignment.course?.name} μετακινήθηκε.`);
      onMoved();
      onClose();
    } catch (err: any) {
      onError(err?.response?.data?.error || err?.message || 'Σφάλμα μετακίνησης.');
    } finally { setSaving(false); }
  }

  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={modalStyle} onClick={e => e.stopPropagation()}>
        <h2 style={{ fontSize: '1.15rem', marginBottom: '0.5rem' }}>Μετακίνηση εξέτασης</h2>
        <div style={{ background: '#1a2744', padding: '10px', borderRadius: '8px', marginBottom: '1rem', fontSize: '13px' }}>
          <strong>{assignment.course?.name}</strong>
          <div style={{ color: '#64748b', fontSize: '11px', marginTop: '2px' }}>
            Τρέχουσα: {currentDate ? formatDateHeader(currentDate) : '—'} {currentHour} — {assignment.room?.code}
          </div>
        </div>

        <label style={labelStyle}>Ημερομηνία</label>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '5px', marginBottom: '1rem', maxHeight: '130px', overflowY: 'auto', padding: '2px' }}>
          {availableDates.map(d => (
            <button key={d} onClick={() => setSelDate(d)} style={{
              padding: '4px 10px', borderRadius: '6px', border: 'none', cursor: 'pointer', fontSize: '12px',
              background: selDate === d ? '#1d4ed8' : '#1a2744',
              color: selDate === d ? '#fff' : '#94a3b8',
              fontWeight: selDate === d ? 600 : 400,
            }}>{formatDateHeader(d)}</button>
          ))}
        </div>

        <label style={labelStyle}>Ώρα</label>
        <div style={{ display: 'flex', gap: '8px', marginBottom: '1rem' }}>
          {EXAM_HOURS.map(h => (
            <button key={h} onClick={() => setSelHour(h)} style={{
              flex: 1, padding: '8px', borderRadius: '6px', border: 'none', cursor: 'pointer',
              background: selHour === h ? '#1d4ed8' : '#1a2744',
              color: '#fff', fontWeight: selHour === h ? 700 : 400, fontSize: '13px',
              fontFamily: 'JetBrains Mono, monospace',
            }}>{h}</button>
          ))}
        </div>

        <label style={labelStyle}>Αίθουσα</label>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '6px', marginBottom: '1rem' }}>
          {rooms.filter(r => r.availableForExams !== false).map(r => (
            <button key={r.id} onClick={() => setSelRoomId(r.id)} style={{
              padding: '8px', borderRadius: '8px', cursor: 'pointer', textAlign: 'center',
              background: selRoomId === r.id ? '#1e3a5f' : '#0d1b2e',
              border: selRoomId === r.id ? '2px solid #3b82f6' : '2px solid #1a2744',
              color: '#fff',
            }}>
              <div style={{ fontWeight: 700, fontSize: '12px' }}>{r.code}</div>
              <div style={{ fontSize: '10px', color: '#64748b' }}>Χωρ: {r.capacity}</div>
            </button>
          ))}
        </div>

        {hasChanged && (
          <div style={{ fontSize: '12px', color: '#fbbf24', marginBottom: '0.75rem' }}>
            Νέα θέση: {formatDateHeader(selDate)} {selHour} — {rooms.find(r => r.id === selRoomId)?.code ?? '?'}
          </div>
        )}

        <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
          <button onClick={onClose} style={secondaryBtn}>Ακύρωση</button>
          <button onClick={handleMove} disabled={saving || !hasChanged} style={{ ...primaryBtn, opacity: hasChanged ? 1 : 0.5 }}>
            {saving ? 'Αποθήκευση...' : 'Μετακίνηση'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function ExamTimetable() {
  const [timetables, setTimetables]         = useState<Timetable[]>([]);
  const [selectedId, setSelectedId]         = useState<number | null>(null);
  const [assignments, setAssignments]       = useState<TimetableAssignment[]>([]);
  const [courses, setCourses]               = useState<Course[]>([]);
  const [rooms, setRooms]                   = useState<Room[]>([]);
  const [examTimeSlots, setExamTimeSlots]   = useState<TimeSlot[]>([]);
  const [progress, setProgress]             = useState<TimetableProgress | null>(null);
  const [validation, setValidation]         = useState<TimetableValidationReport | null>(null);
  const [placementOptions, setPlacementOptions] = useState<PlacementOptionsResponse | null>(null);

  const [selectedCourseId, setSelectedCourseId] = useState<number | ''>('');
  const [courseSearch, setCourseSearch]     = useState('');
  const [selectedRoomId, setSelectedRoomId] = useState<number | ''>('');

  const [showAddModal, setShowAddModal]     = useState(false);
  const [addDate, setAddDate]               = useState('');
  const [addHour,      setAddHour]      = useState('');
  const [examDuration, setExamDuration] = useState(180);
  const [movingAssignment,  setMovingAssignment]  = useState<TimetableAssignment | null>(null);
  const [draggingAssignment,setDraggingAssignment]= useState<TimetableAssignment | null>(null);

  const [loading, setLoading]               = useState(false);
  const [loadingOptions, setLoadingOptions] = useState(false);
  const [saving, setSaving]                 = useState(false);
  const [solving, setSolving]               = useState(false);
  const [error, setError]                   = useState<string | null>(null);
  const [message, setMessage]               = useState<string | null>(null);

  // Load initial data
  useEffect(() => {
    Promise.all([
      timetableService.getAll(),
      courseService.getAll(),
      roomService.getAll(),
      timeSlotService.getExamSlots(),
    ]).then(([ttRes, cRes, rRes, tsRes]) => {
      setTimetables((ttRes.data as Timetable[]).filter(t => t.timetableType === 'EXAM'));
      setCourses(cRes.data);
      setRooms(rRes.data);
      setExamTimeSlots(tsRes.data);
    }).catch(() => setError('Αδύνατη σύνδεση με τον server.'));
  }, []);

  // Load timetable data when selection changes
  useEffect(() => {
    if (!selectedId) {
      setAssignments([]); setProgress(null); setValidation(null); setPlacementOptions(null);
      return;
    }
    setLoading(true);
    Promise.all([
      timetableService.getAssignments(selectedId),
      timetableService.getProgress(selectedId),
      timetableService.validate(selectedId),
    ]).then(([aRes, pRes, vRes]) => {
      setAssignments(aRes.data);
      setProgress(pRes.data);
      setValidation(vRes.data);
    }).catch(() => setError('Σφάλμα φόρτωσης δεδομένων.'))
      .finally(() => setLoading(false));
  }, [selectedId]);

  // Auto-clear toasts
  useEffect(() => {
    if (!error && !message) return;
    const t = setTimeout(() => { setError(null); setMessage(null); }, error ? 7000 : 4000);
    return () => clearTimeout(t);
  }, [error, message]);

  const selectedTimetable = useMemo(() =>
    timetables.find(t => t.id === selectedId), [timetables, selectedId]);

  const examDates = useMemo(() => {
    const s = new Set<string>();
    const start = selectedTimetable?.startDate;
    const end   = selectedTimetable?.endDate;
    for (const ts of examTimeSlots) {
      if (!ts.specificDate) continue;
      if (start && ts.specificDate < start) continue;
      if (end   && ts.specificDate > end)   continue;
      s.add(ts.specificDate);
    }
    return Array.from(s).sort();
  }, [examTimeSlots, selectedTimetable]);

  const eligibleCourses = useMemo(() =>
    getEligibleCourses(courses, selectedTimetable), [courses, selectedTimetable]);

  const filteredCourses = useMemo(() => {
    const q = courseSearch.trim().toLowerCase();
    if (!q) return eligibleCourses;
    return eligibleCourses.filter(c =>
      c.name.toLowerCase().includes(q) || c.code.toLowerCase().includes(q)
    );
  }, [eligibleCourses, courseSearch]);

  // Slot hint map from placement options
  const slotHintMap = useMemo(() => {
    const map = new Map<string, 'allowed' | 'blocked'>();
    if (!placementOptions) return map;
    for (const opt of placementOptions.options) {
      const date = opt.timeSlot.specificDate;
      const hour = normalizeTime(opt.timeSlot.startTime);
      if (!date || !hour) continue;
      const key = `${date}-${hour}`;
      if (opt.allowed) { if (map.get(key) !== 'allowed') map.set(key, 'allowed'); }
      else { if (!map.has(key)) map.set(key, 'blocked'); }
    }
    return map;
  }, [placementOptions]);

  function getExamSlotId(date: string, hour: string): number | undefined {
    return examTimeSlots.find(ts =>
      ts.specificDate === date && normalizeTime(ts.startTime) === hour
    )?.id;
  }

  function getAssignmentsAt(date: string, hour: string): TimetableAssignment[] {
    return assignments.filter(a =>
      a.timeSlot.specificDate === date && normalizeTime(a.timeSlot.startTime) === hour
    );
  }

  async function reloadTimetableData() {
    if (!selectedId) return;
    const [aRes, pRes, vRes] = await Promise.all([
      timetableService.getAssignments(selectedId),
      timetableService.getProgress(selectedId),
      timetableService.validate(selectedId),
    ]);
    setAssignments(aRes.data);
    setProgress(pRes.data);
    setValidation(vRes.data);
  }

  function openAddModal(date: string, hour: string) {
    if (!selectedId) return;
    setAddDate(date);
    setAddHour(hour);
    setSelectedRoomId('');
    setShowAddModal(true);
  }

  async function submitAdd() {
    if (!selectedId || !selectedCourseId || !selectedRoomId) return;
    const slotId = getExamSlotId(addDate, addHour);
    if (!slotId) { setError('Δεν βρέθηκε χρονοθυρίδα.'); return; }
    setSaving(true);
    try {
      await timetableService.addAssignment(selectedId, {
        courseId: selectedCourseId, roomId: selectedRoomId,
        timeSlotId: slotId, assignmentType: 'EXAM',
        examDurationMinutes: examDuration,
      });
      setShowAddModal(false);
      setPlacementOptions(null);
      setMessage('Η ανάθεση προστέθηκε επιτυχώς.');
      await reloadTimetableData();
    } catch (err: any) {
      setError(err?.response?.data?.error || err?.message || 'Σφάλμα αποθήκευσης.');
    } finally { setSaving(false); }
  }

  async function removeAssignment(id: number) {
    if (!selectedId || !confirm('Διαγραφή εξέτασης από το πρόγραμμα;')) return;
    setSaving(true);
    try {
      await timetableService.removeAssignment(id);
      setMessage('Η ανάθεση διαγράφηκε.');
      setPlacementOptions(null);
      await reloadTimetableData();
    } catch (err: any) {
      setError(err?.response?.data?.error || err?.message || 'Σφάλμα διαγραφής.');
    } finally { setSaving(false); }
  }

  async function loadPlacementOptions() {
    if (!selectedId || !selectedCourseId) return;
    setLoadingOptions(true);
    try {
      const res = await timetableService.getPlacementOptions(selectedId, selectedCourseId, 'EXAM');
      setPlacementOptions(res.data);
    } catch (err: any) {
      setError(err?.response?.data?.error || err?.message || 'Σφάλμα φόρτωσης επιλογών.');
    } finally { setLoadingOptions(false); }
  }

  async function addPlacement(opt: PlacementOption) {
    if (!selectedId || !selectedCourseId) return;
    setSaving(true);
    try {
      await timetableService.addAssignment(selectedId, {
        courseId: selectedCourseId, roomId: selectedRoomId,
        timeSlotId: slotId, assignmentType: 'EXAM',
        examDurationMinutes: examDuration,
      });
      setPlacementOptions(null);
      setMessage('Η προτεινόμενη ανάθεση προστέθηκε.');
      await reloadTimetableData();
    } catch (err: any) {
      setError(err?.response?.data?.error || err?.message || 'Σφάλμα.');
    } finally { setSaving(false); }
  }

  async function handleExamDrop(date: string, hour: string) {
    if (!draggingAssignment || !selectedId) return;
    const slotId = getExamSlotId(date, hour);
    if (!slotId) return;
    const usedRooms = new Set(getAssignmentsAt(date, hour)
      .filter(a => a.id !== draggingAssignment.id).map(a => a.room?.id));
    let roomId = draggingAssignment.room?.id ?? 0;
    if (usedRooms.has(roomId)) {
      const available = rooms.find(r => !usedRooms.has(r.id) && r.availableForExams !== false);
      if (available) roomId = available.id;
    }
    setSaving(true);
    const moved = draggingAssignment;
    setDraggingAssignment(null);
    try {
      await timetableService.moveAssignment(moved.id, { timeSlotId: slotId, roomId });
      setMessage(`${moved.course?.name} μετακινήθηκε → ${date} ${hour}`);
      await reloadTimetableData();
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Σφάλμα μετακίνησης.');
    } finally { setSaving(false); }
  }

  async function handleSolve() {
    if (!selectedId) return;
    if (!confirm('Εκτέλεση Solver;\n\nΘα αντικαταστήσει τις αυτόματες αναθέσεις.\nΟι χειροκίνητες θα παραμείνουν.\nΔιάρκεια: ~30 δευτερόλεπτα.')) return;
    setSolving(true);
    try {
      const res = await timetableService.solve(selectedId, 30);
      const d = res.data as any;
      setMessage(`Solver: ${d.totalPlaced} εξετάσεις τοποθετήθηκαν | Hard: ${d.hardScore} | Soft: ${d.softScore}`);
      setPlacementOptions(null);
      await reloadTimetableData();
    } catch (err: any) {
      setError(err?.response?.data?.error || err?.message || 'Σφάλμα solver.');
    } finally { setSolving(false); }
  }

  // Instant drag hints for exam grid
  const examDragHintMap = useMemo(() => {
    const map = new Map<string, 'allowed' | 'blocked'>();
    if (!draggingAssignment) return map;

    const dragYear     = draggingAssignment.course.studyYear;
    const dragCourseId = draggingAssignment.course.id;
    const dragType     = draggingAssignment.course.courseType;
    const dragId       = draggingAssignment.id;

    // Βρες ποιες ημέρες είναι blocked
    const blockedDates = new Set<string>();
    for (const a of assignments) {
      if (a.id === dragId || !a.timeSlot?.specificDate) continue;
      const date = a.timeSlot.specificDate;
      // Ίδιο μάθημα οποιαδήποτε ημέρα → blocked
      if (a.course.id === dragCourseId) {
        blockedDates.add(date);
        continue;
      }
      // Ίδιο έτος + και τα δύο υποχρεωτικά → blocked ολόκληρη η μέρα
      if (a.course.studyYear === dragYear &&
          a.course.courseType === 'REQUIRED' &&
          dragType === 'REQUIRED') {
        blockedDates.add(date);
      }
    }

    for (const ts of examTimeSlots) {
      if (!ts.specificDate || !ts.startTime) continue;
      const key = `${ts.specificDate}-${normalizeTime(ts.startTime)}`;
      map.set(key, blockedDates.has(ts.specificDate) ? 'blocked' : 'allowed');
    }
    return map;
  }, [draggingAssignment, assignments, examTimeSlots]);

  const activeHintMap = draggingAssignment ? examDragHintMap : slotHintMap;

  const allowedOptions = placementOptions?.options.filter(o => o.allowed) ?? [];
  const blockedOptions  = placementOptions?.options.filter(o => !o.allowed) ?? [];

  // ─── Render ─────────────────────────────────────────────────────────────────

  return (
    <div style={{ padding: '40px 48px', background: '#080f1a', minHeight: 'calc(100vh - 52px)', fontFamily: "'IBM Plex Sans', sans-serif", color: '#e2e8f0' }}>
      <style>{`@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;400;500;600&family=JetBrains+Mono:wght@400;500&display=swap');`}</style>

      {/* Toasts */}
      {error && (
        <div style={toastError}>
          <strong>Σφάλμα</strong>
          <div style={{ marginTop: '0.35rem' }}>{error}</div>
          <button onClick={() => setError(null)} style={toastClose}>×</button>
        </div>
      )}
      {message && (
        <div style={toastSuccess}>
          <strong>Επιτυχία</strong>
          <div style={{ marginTop: '0.35rem' }}>{message}</div>
          <button onClick={() => setMessage(null)} style={toastClose}>×</button>
        </div>
      )}

      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.5rem' }}>
        <div>
          <h1 style={{ fontSize: '1.6rem', fontWeight: 600, letterSpacing: '-0.5px', marginBottom: '0.35rem' }}>Εξεταστική Περίοδος</h1>
          <p style={{ color: '#64748b', fontSize: '14px' }}>Επιλογή εξεταστικού προγράμματος, manual τοποθέτηση και εκτέλεση solver.</p>
        </div>
        {selectedId && (
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              onClick={async () => {
                try {
                  await timetableService.generateExamSlotsForTimetable(selectedId);
                  const tsRes = await timeSlotService.getExamSlots();
                  setExamTimeSlots(tsRes.data);
                  setMessage('Exam slots δημιουργήθηκαν! Ανανεώθηκε το grid.');
                } catch (err: any) {
                  setError(err?.response?.data?.error || 'Σφάλμα δημιουργίας slots.');
                }
              }}
              disabled={solving || saving}
              style={{ ...solverBtn, background: '#059669' }}
            >
              📅 Δημιουργία Slots
            </button>
            <button onClick={handleSolve} disabled={solving || saving} style={solverBtn}>
              {solving ? 'Εκτελείται...' : '⚡ CPSolver'}
            </button>
          </div>
        )}
      </div>

      {/* Selector */}
      <TimetableSelector
        timetables={timetables}
        selectedTimetableId={selectedId}
        onSelect={setSelectedId}
        onCreated={(t: any) => { setTimetables(prev => [...prev, t]); setSelectedId(t.id); }}
        onDeleted={(id: number) => { setTimetables(prev => prev.filter(t => t.id !== id)); if (selectedId === id) setSelectedId(null); }}
        disabled={saving || solving}
        progress={progress}
        timetableType="EXAM"
      />

      {/* Empty state */}
      {!selectedId && (
        <div style={{ textAlign: 'center', padding: '60px', color: '#334155', fontSize: '13px' }}>
          Επίλεξε εξεταστικό πρόγραμμα για προβολή.
        </div>
      )}

      {selectedId && loading && (
        <div style={{ textAlign: 'center', padding: '60px', color: '#334155', fontFamily: 'JetBrains Mono, monospace', fontSize: '12px' }}>
          Φόρτωση...
        </div>
      )}

      {selectedId && !loading && (
        <>
          {/* Stats */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '1rem', margin: '1.5rem 0' }}>
            <StatCard label="Τοποθετημένες εξετάσεις" value={assignments.length} color="#3b82f6" />
            <StatCard label="Πρόοδος" value={`${progress?.percentage ?? 0}%`} color="#10b981" />
            <StatCard label="Errors"   value={validation?.errorCount ?? 0}   color={(validation?.errorCount ?? 0) > 0 ? '#ef4444' : '#22c55e'} />
            <StatCard label="Warnings" value={validation?.warningCount ?? 0} color={(validation?.warningCount ?? 0) > 0 ? '#f59e0b' : '#22c55e'} />
          </div>

          {/* ── Μπάρα προόδου εξεταστικής ─────────────────────────────── */}
          {progress && (
            <div style={{
              background: '#0d1b2e', border: '1px solid #1a2744',
              borderRadius: 10, padding: '14px 18px', marginBottom: '1.5rem',
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <span style={{ fontSize: 12, color: '#94a3b8', fontFamily: 'JetBrains Mono, monospace' }}>
                  {progress.completedCourses} / {progress.totalCourses} εξετάσεις τοποθετημένες
                </span>
                <span style={{
                  fontSize: 13, fontWeight: 700, fontFamily: 'JetBrains Mono, monospace',
                  color: progress.percentage >= 100 ? '#22c55e'
                       : (validation?.errorCount ?? 0) > 0 ? '#ef4444' : '#3b82f6',
                }}>
                  {Math.round(progress.percentage)}%
                </span>
              </div>
              <div style={{ height: 8, background: '#1a2744', borderRadius: 4, overflow: 'hidden' }}>
                <div style={{
                  height: '100%',
                  width: `${Math.min(progress.percentage, 100)}%`,
                  background: progress.percentage >= 100 ? '#22c55e'
                             : (validation?.errorCount ?? 0) > 0 ? '#ef4444' : '#3b82f6',
                  borderRadius: 4, transition: 'width 0.8s ease',
                }} />
              </div>
              {progress.missingCourses.length > 0 && (
                <div style={{
                  fontSize: 10, color: '#f59e0b', marginTop: 7,
                  fontFamily: 'JetBrains Mono, monospace',
                }}>
                  ● {progress.missingCourses.length} μαθήματα χωρίς τοποθετημένη εξέταση
                </div>
              )}
            </div>
          )}

          {/* Grid + Right Panel */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: '1.5rem', alignItems: 'start' }}>

            {/* Grid */}
            <section style={{ background: '#0d1b2e', border: '1px solid #1a2744', borderRadius: '12px', overflow: 'hidden' }}>
              {examDates.length === 0 && (
                <div style={{ padding: '40px', textAlign: 'center', color: '#334155', fontSize: '13px' }}>
                  Δεν υπάρχουν ημερομηνίες εξεταστικής.<br />
                  <span style={{ color: '#475569', fontSize: '12px' }}>Δημιούργησε exam time slots από το backend (generate-exam-slots).</span>
                </div>
              )}
              {examDates.length > 0 && (
                <div style={{ overflowX: 'auto' }}>
                  <table style={{ width: '100%', minWidth: `${100 + examDates.length * 145}px`, borderCollapse: 'collapse' }}>
                    <thead>
                      <tr>
                        <th style={thStyle}>Ώρα</th>
                        {examDates.map(date => (
                          <th key={date} style={thStyle}>{formatDateHeader(date)}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {EXAM_HOURS.map(hour => (
                        <tr key={hour}>
                          <td style={{ ...tdStyle, width: '90px', fontFamily: 'JetBrains Mono, monospace', fontSize: '12px', color: '#94a3b8', fontWeight: 600 }}>
                            {hour}
                          </td>
                          {examDates.map(date => {
                            const cellAssignments = getAssignmentsAt(date, hour);
                            const hint = activeHintMap.get(`${date}-${hour}`);
                            const slotExists = !!getExamSlotId(date, hour);
                            return (
                              <td
                                key={date}
                                onClick={() => !draggingAssignment && slotExists && openAddModal(date, hour)}
                                onDragOver={e => { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; }}
                                onDrop={e => { e.preventDefault(); handleExamDrop(date, hour); }}
                                title={
                                  hint === 'allowed' ? 'Επιτρεπτή τοποθέτηση' :
                                  hint === 'blocked' ? 'Μη επιτρεπτή τοποθέτηση' :
                                  slotExists ? 'Κλικ για προσθήκη' : ''
                                }
                                style={{
                                  ...tdStyle,
                                  cursor: slotExists ? 'pointer' : 'default',
                                  background:
                                    hint === 'allowed' ? '#052e16' :
                                    hint === 'blocked' ? '#1c0a0a' :
                                    cellAssignments.length > 0 ? '#0d1b2e' : '#080f1a',
                                  borderLeft:
                                    hint === 'allowed' ? '3px solid #22c55e' :
                                    hint === 'blocked' ? '3px solid #7f1d1d' :
                                    '1px solid #1a2744',
                                }}
                              >
                                {!slotExists ? (
                                  <span style={{ color: '#1e293b', fontSize: '10px' }}>—</span>
                                ) : cellAssignments.length === 0 ? (
                                  <span style={{ color: '#1e3a5f', fontSize: '11px' }}>+ Προσθήκη</span>
                                ) : (
                                  <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                                    {cellAssignments.map(a => (
                                      <ExamCard
                                        key={a.id}
                                        assignment={a}
                                        onDelete={() => removeAssignment(a.id)}
                                        onMove={() => setMovingAssignment(a)}
                                        onDragStart={() => setDraggingAssignment(a)}
                                        onDragEnd={() => setDraggingAssignment(null)}
                                        disabled={saving}
                                      />
                                    ))}
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
            </section>

            {/* Right Panel */}
            <aside style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>

              {/* ── Αδιάθετες εξετάσεις ──────────────────────────────── */}
              {progress && progress.missingCourses.length > 0 && (
                <section style={panelStyle}>
                  <h3 style={panelTitle}>
                    Αδιάθετες εξετάσεις
                    <span style={{
                      marginLeft: 8, fontFamily: 'JetBrains Mono, monospace',
                      fontSize: 11, color: '#f59e0b',
                    }}>
                      ({progress.missingCourses.length})
                    </span>
                  </h3>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 4, maxHeight: 240, overflowY: 'auto' }}>
                    {progress.missingCourses.map(c => (
                      <div
                        key={c.courseId}
                        onClick={() => {
                          setSelectedCourseId(c.courseId);
                          setCourseSearch(c.name);
                          setPlacementOptions(null);
                        }}
                        style={{
                          background: selectedCourseId === c.courseId ? '#1e3a5f' : '#111e33',
                          borderLeft: `3px solid ${selectedCourseId === c.courseId ? '#3b82f6' : '#f59e0b'}`,
                          borderRadius: 6, padding: '7px 10px', cursor: 'pointer',
                          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                          transition: 'background 0.1s',
                        }}
                      >
                        <div>
                          <div style={{ fontSize: 11, fontWeight: 500, color: '#e2e8f0' }}>
                            {c.name}
                          </div>
                          <div style={{
                            fontSize: 10, fontFamily: 'JetBrains Mono, monospace',
                            color: '#475569', marginTop: 2,
                          }}>
                            {c.code} · Εξ.{c.semester} · {c.studyYear}ο έτος
                          </div>
                        </div>
                        <span style={{
                          fontSize: 9, padding: '2px 6px', borderRadius: 3, flexShrink: 0,
                          background: '#451a0322', color: '#f59e0b',
                          border: '1px solid #78350f',
                          fontFamily: 'JetBrains Mono, monospace', fontWeight: 700,
                        }}>
                          ΕΚΚΡΕΜΕΊ
                        </span>
                      </div>
                    ))}
                  </div>
                  <div style={{ fontSize: 10, color: '#334155', marginTop: 8 }}>
                    Κλικ → επιλογή + «Βρες προτεινόμενες θέσεις»
                  </div>
                </section>
              )}

              {/* Course + find options */}
              <section style={panelStyle}>
                <h3 style={panelTitle}>Προσθήκη εξέτασης</h3>
                <label style={labelStyle}>Μάθημα</label>
                <input
                  value={courseSearch}
                  onChange={e => setCourseSearch(e.target.value)}
                  placeholder="Αναζήτηση ονόματος ή κωδικού..."
                  style={inputStyle}
                />
                <div style={{ maxHeight: '200px', overflowY: 'auto', border: '1px solid #1a2744', borderRadius: '8px', marginBottom: '0.75rem' }}>
                  {filteredCourses.slice(0, 25).map(c => (
                    <div
                      key={c.id}
                      onClick={() => { setSelectedCourseId(c.id); setCourseSearch(c.name); setPlacementOptions(null); }}
                      style={{
                        padding: '8px 10px', cursor: 'pointer',
                        background: selectedCourseId === c.id ? '#1e3a5f' : 'transparent',
                        borderLeft: selectedCourseId === c.id ? '3px solid #3b82f6' : '3px solid transparent',
                        borderBottom: '1px solid #1a2744',
                      }}
                    >
                      <div style={{ fontSize: '12px', fontWeight: 600 }}>{c.name}</div>
                      <div style={{ fontSize: '10px', color: '#475569', fontFamily: 'JetBrains Mono, monospace' }}>
                        {c.code} · Εξ.{c.semester}
                      </div>
                    </div>
                  ))}
                  {filteredCourses.length === 0 && (
                    <div style={{ padding: '1rem', color: '#475569', fontSize: '12px', textAlign: 'center' }}>Δεν βρέθηκαν μαθήματα.</div>
                  )}
                </div>
                <button
                  onClick={loadPlacementOptions}
                  disabled={loadingOptions || saving || !selectedCourseId}
                  style={{ ...primaryBtn, width: '100%' }}
                >
                  {loadingOptions ? 'Φόρτωση...' : 'Βρες προτεινόμενες θέσεις'}
                </button>
              </section>

              {/* Placement options */}
              {placementOptions && (
                <section style={panelStyle}>
                  <h3 style={panelTitle}>Προτάσεις ({placementOptions.allowedOptions}/{placementOptions.totalOptions})</h3>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '380px', overflowY: 'auto' }}>
                    {allowedOptions.slice(0, 10).map(opt => (
                      <div key={`${opt.room.id}-${opt.timeSlot.id}`} style={{ background: '#111e33', border: '1px solid #1a2744', borderRadius: '8px', padding: '10px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                          <div>
                            <div style={{ fontSize: '12px', fontWeight: 600 }}>
                              {opt.timeSlot.specificDate ? formatDateHeader(opt.timeSlot.specificDate) : opt.timeSlot.dayOfWeek} {normalizeTime(opt.timeSlot.startTime)}
                            </div>
                            <div style={{ fontSize: '11px', color: '#64748b' }}>{opt.room.name} ({opt.room.code})</div>
                          </div>
                          <div style={{ color: '#22c55e', fontWeight: 700, fontSize: '13px' }}>{opt.score}</div>
                        </div>
                        <ul style={{ margin: '4px 0 8px 14px', color: '#94a3b8', fontSize: '10px', lineHeight: 1.35 }}>
                          {opt.reasons.slice(0, 2).map((r, i) => <li key={i}>{r}</li>)}
                        </ul>
                        <button onClick={() => addPlacement(opt)} disabled={saving} style={{ ...primaryBtn, width: '100%', padding: '5px', fontSize: '12px' }}>
                          Τοποθέτηση
                        </button>
                      </div>
                    ))}
                    {allowedOptions.length === 0 && (
                      <div style={{ color: '#f87171', fontSize: '12px' }}>Δεν βρέθηκαν επιτρεπτές θέσεις.</div>
                    )}
                  </div>
                </section>
              )}

              {/* Blocked preview */}
              {placementOptions && blockedOptions.length > 0 && (
                <section style={panelStyle}>
                  <h3 style={panelTitle}>Πρώτα blocked παραδείγματα</h3>
                  {blockedOptions.slice(0, 3).map(opt => (
                    <div key={`${opt.room.id}-${opt.timeSlot.id}`} style={{ marginBottom: '6px', fontSize: '11px' }}>
                      <div style={{ color: '#f87171' }}>
                        {opt.timeSlot.specificDate ? formatDateHeader(opt.timeSlot.specificDate) : opt.timeSlot.dayOfWeek} {normalizeTime(opt.timeSlot.startTime)} — {opt.room.code}
                      </div>
                      <div style={{ color: '#64748b' }}>{opt.reasons[0]}</div>
                    </div>
                  ))}
                </section>
              )}

              {/* Validation warnings */}
              {validation && validation.warnings.length > 0 && (
                <section style={panelStyle}>
                  <h3 style={panelTitle}>Warnings ({validation.warningCount})</h3>
                  <div style={{ maxHeight: '200px', overflowY: 'auto' }}>
                    {validation.warnings.slice(0, 8).map((w, i) => (
                      <div key={i} style={{ color: '#fbbf24', fontSize: '11px', marginBottom: '4px', lineHeight: 1.35 }}>{w.message}</div>
                    ))}
                  </div>
                </section>
              )}
            </aside>
          </div>
        </>
      )}

      {/* Add modal */}
      {showAddModal && (
        <div style={overlayStyle} onClick={() => setShowAddModal(false)}>
          <div style={modalStyle} onClick={e => e.stopPropagation()}>
            <h2 style={{ fontSize: '1.2rem', marginBottom: '0.35rem' }}>Προσθήκη εξέτασης</h2>
            <p style={{ color: '#64748b', fontSize: '13px', marginBottom: '1rem' }}>
              {formatDateHeader(addDate)} — {addHour}
            </p>

            <label style={labelStyle}>Μάθημα</label>
            <input value={courseSearch} onChange={e => setCourseSearch(e.target.value)} placeholder="Αναζήτηση..." style={inputStyle} />
            <div style={{ maxHeight: '180px', overflowY: 'auto', border: '1px solid #1a2744', borderRadius: '8px', marginBottom: '0.75rem' }}>
              {filteredCourses.slice(0, 30).map(c => (
                <div
                  key={c.id}
                  onClick={() => { setSelectedCourseId(c.id); setCourseSearch(c.name); }}
                  style={{
                    padding: '8px 10px', cursor: 'pointer',
                    background: selectedCourseId === c.id ? '#1e3a5f' : 'transparent',
                    borderLeft: selectedCourseId === c.id ? '3px solid #3b82f6' : '3px solid transparent',
                    borderBottom: '1px solid #1a2744',
                  }}
                >
                  <div style={{ fontSize: '12px', fontWeight: 600 }}>{c.name}</div>
                  <div style={{ fontSize: '10px', color: '#475569' }}>{c.code} · Εξ.{c.semester}</div>
                </div>
              ))}
            </div>

           <label style={labelStyle}>Διάρκεια εξέτασης</label>
            <div style={{ display: 'flex', gap: '6px', marginBottom: '1rem', flexWrap: 'wrap' }}>
              {EXAM_DURATIONS.map(d => (
                <button key={d.value} onClick={() => setExamDuration(d.value)} style={{
                  padding: '6px 12px', borderRadius: '6px', border: 'none', cursor: 'pointer', fontSize: '12px',
                  background: examDuration === d.value ? '#1d4ed8' : '#1a2744',
                  color: examDuration === d.value ? '#fff' : '#94a3b8',
                  fontWeight: examDuration === d.value ? 700 : 400,
                }}>{d.label}</button>
              ))}
            </div>

            <label style={labelStyle}>Αίθουσα</label>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '6px', marginBottom: '1rem' }}>
              {rooms.filter(r => r.availableForExams !== false).map(r => (
                <button key={r.id} onClick={() => setSelectedRoomId(r.id)} style={{
                  padding: '8px', borderRadius: '8px', cursor: 'pointer', textAlign: 'center',
                  background: selectedRoomId === r.id ? '#1e3a5f' : '#0f172a',
                  border: selectedRoomId === r.id ? '2px solid #3b82f6' : '2px solid #1a2744',
                  color: '#fff',
                }}>
                  <div style={{ fontWeight: 700, fontSize: '13px' }}>{r.code}</div>
                  <div style={{ fontSize: '10px', color: '#64748b' }}>Χωρ: {r.capacity}</div>
                </button>
              ))}
            </div>

            <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
              <button onClick={() => setShowAddModal(false)} style={secondaryBtn}>Ακύρωση</button>
              <button onClick={submitAdd} disabled={saving || !selectedCourseId || !selectedRoomId}
                style={{ ...successBtn, opacity: selectedCourseId && selectedRoomId ? 1 : 0.5 }}>
                {saving ? 'Αποθήκευση...' : 'Προσθήκη'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Move modal */}
      {movingAssignment && (
        <ExamMoveModal
          assignment={movingAssignment}
          rooms={rooms}
          examTimeSlots={examTimeSlots}
          onMoved={async () => { setMovingAssignment(null); await reloadTimetableData(); }}
          onClose={() => setMovingAssignment(null)}
          onError={setError}
          onSuccess={setMessage}
        />
      )}
    </div>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const thStyle: CSSProperties = {
  padding: '10px 12px', background: '#0a1628', color: '#94a3b8',
  borderBottom: '1px solid #1a2744', textAlign: 'left',
  fontSize: '11px', fontFamily: 'JetBrains Mono, monospace', whiteSpace: 'nowrap',
};

const tdStyle: CSSProperties = {
  padding: '8px', verticalAlign: 'top',
  borderBottom: '1px solid #1a2744', borderRight: '1px solid #1a2744',
  minHeight: '72px', minWidth: '130px',
};

const panelStyle: CSSProperties = {
  background: '#0d1b2e', border: '1px solid #1a2744', borderRadius: '10px', padding: '1rem',
};

const panelTitle: CSSProperties = {
  fontSize: '11px', fontWeight: 600, color: '#64748b',
  letterSpacing: '1px', textTransform: 'uppercase',
  marginBottom: '0.75rem', fontFamily: 'JetBrains Mono, monospace',
};

const labelStyle: CSSProperties = {
  display: 'block', color: '#94a3b8', fontSize: '12px', marginBottom: '0.3rem',
};

const inputStyle: CSSProperties = {
  width: '100%', padding: '7px 10px', borderRadius: '7px',
  border: '1px solid #1a2744', background: '#080f1a', color: '#e2e8f0',
  marginBottom: '0.6rem', boxSizing: 'border-box', fontSize: '13px',
};

const primaryBtn: CSSProperties = {
  padding: '7px 14px', border: 'none', borderRadius: '7px',
  background: '#1d4ed8', color: '#fff', fontWeight: 600, cursor: 'pointer', fontSize: '13px',
};

const secondaryBtn: CSSProperties = {
  padding: '7px 14px', border: 'none', borderRadius: '7px',
  background: '#1a2744', color: '#94a3b8', fontWeight: 600, cursor: 'pointer', fontSize: '13px',
};

const successBtn: CSSProperties = {
  padding: '7px 14px', border: 'none', borderRadius: '7px',
  background: '#065f46', color: '#34d399', fontWeight: 600, cursor: 'pointer', fontSize: '13px',
};

const solverBtn: CSSProperties = {
  padding: '8px 20px', border: 'none', borderRadius: '8px',
  background: '#1d4ed8', color: '#fff', fontWeight: 600, cursor: 'pointer', fontSize: '13px',
};

const overlayStyle: CSSProperties = {
  position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
  background: 'rgba(0,0,0,0.75)',
  display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
};

const modalStyle: CSSProperties = {
  background: '#0d1b2e', border: '1px solid #1a2744',
  borderRadius: '12px', padding: '1.5rem',
  width: '540px', maxHeight: '88vh', overflowY: 'auto',
};

const toastBase: CSSProperties = {
  position: 'fixed', top: '1.25rem', right: '1.25rem',
  width: '360px', maxWidth: 'calc(100vw - 2rem)',
  padding: '1rem 2.5rem 1rem 1rem', borderRadius: '10px',
  color: '#fff', zIndex: 3000, boxShadow: '0 20px 35px rgba(0,0,0,0.35)', lineHeight: 1.35,
};
const toastError:   CSSProperties = { ...toastBase, background: '#450a0a', border: '1px solid #ef4444' };
const toastSuccess: CSSProperties = { ...toastBase, background: '#052e16', border: '1px solid #22c55e' };
const toastClose:   CSSProperties = {
  position: 'absolute', top: '0.45rem', right: '0.6rem',
  border: 'none', background: 'transparent', color: '#fff', fontSize: '1.2rem', cursor: 'pointer',
};
