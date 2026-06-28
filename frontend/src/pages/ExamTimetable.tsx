import { useCallback, useEffect, useMemo, useState } from 'react';
import type { CSSProperties } from 'react';
import { courseService, roomService, timeSlotService, timetableService } from '../api/services';
import type {
  Course, PlacementOption, PlacementOptionsResponse,
  Room, TimeSlot, Timetable,
  TimetableAssignment, TimetableProgress, TimetableValidationReport, ValidationIssue,
} from '../types';
import TimetableSelector from '../components/TimetableSelector';
import AssignmentDetailsModal from '../components/AssignmentDetailsModal';
import ValidationIssuesModal from '../components/ValidationIssuesModal';
import { esc, shortCode, yearColor, todayGreek, buildPrintDocument, openAndPrint, groupItems, parseTeachers, electiveBucket } from '../utils/printTimetable';
import type { PrintGroupBy } from '../utils/printTimetable';
import PrintOptionsModal from '../components/PrintOptionsModal';
import type { PrintRequest } from '../components/PrintOptionsModal';

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
// Μονό ελληνικό γράμμα ημέρας ανά Date.getDay() (0=Κυρ … 6=Σαβ).
// Η ασάφεια Τ/Τ (Τρ/Τε) & Π/Π (Πε/Πα) είναι αποδεκτή — η ημερομηνία ξεχωρίζει.
const WEEKDAY_LETTER = ['Κ', 'Δ', 'Τ', 'Τ', 'Π', 'Π', 'Σ'];

// Validation codes των οποίων το referenceId ΕΙΝΑΙ assignment id (→ επιλύονται σε
// συγκεκριμένη χρονοθυρίδα). Τα υπόλοιπα codes έχουν course id ή null — ΜΗΝ τα
// αναζητάς στο assignments-by-id (θα έδιναν λάθος/τυχαία τοποθεσία).
const ASSIGNMENT_SCOPED = new Set([
  'INVALID_ASSIGNMENT', 'SEMESTER_MISMATCH', 'LAB_ROOM_REQUIRED', 'FIRST_YEAR_ROOM',
  'REQUIRED_ROOM', 'SHARED_EXAM_ROOM', 'ROOM_CONFLICT', 'SAME_COURSE_SAME_SLOT',
  'TEACHER_CONFLICT', 'REQUIRED_YEAR_EXAM_SAME_DATE', 'REQUIRED_YEAR_CONFLICT',
  'TEACHER_BLOCKED', 'ROOM_BLOCKED',
]);

// ─── Helpers ──────────────────────────────────────────────────────────────────

function normalizeTime(v?: string | null) {
  if (!v) return '';
  return v.length >= 5 ? v.slice(0, 5) : v;
}

function formatDateHeader(dateStr: string) {
  try {
    const d = new Date(dateStr + 'T00:00:00');
    const letter = WEEKDAY_LETTER[d.getDay()];
    return `${letter} ${d.getDate()}/${d.getMonth() + 1}`;
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

function StatCard({ label, value, color, onClick }: { label: string; value: number | string; color: string; onClick?: () => void }) {
  return (
    <div
      onClick={onClick}
      title={onClick ? 'Κλικ για λεπτομέρειες' : undefined}
      style={{
        padding: '1rem', background: '#0d1b2e', border: '1px solid #1a2744',
        borderTop: `2px solid ${color}`, borderRadius: '10px',
        cursor: onClick ? 'pointer' : 'default',
      }}
    >
      <div style={{ fontSize: '22px', fontWeight: 600, color, fontFamily: 'JetBrains Mono, monospace' }}>{value}</div>
      <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>{label}</div>
    </div>
  );
}

function ExamCard({
  assignment, onShowDetails, onDragStart, onDragEnd, disabled,
}: {
  assignment: TimetableAssignment;
  onShowDetails: () => void;
  onDragStart: () => void;
  onDragEnd: () => void;
  disabled: boolean;
}) {
  const color = YEAR_COLORS[(assignment.course?.studyYear ?? 1) - 1] ?? '#3b82f6';
  const shortCode = (assignment.course?.code ?? '').replace(/^CEID_/, '');
  return (
    <div
      onClick={e => { e.stopPropagation(); onShowDetails(); }}
      draggable={true}
      onDragStart={e => { e.stopPropagation(); onDragStart(); }}
      onDragEnd={e => { e.stopPropagation(); onDragEnd(); }}
      title={assignment.course?.code}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: '0.2rem',
        background: '#0a1628', borderLeft: `3px solid ${color}`, borderRadius: '4px',
        padding: '2px 6px', color: '#e2e8f0', fontSize: '11px', fontWeight: 700,
        fontFamily: 'JetBrains Mono, monospace', cursor: 'grab', whiteSpace: 'nowrap',
        flex: '0 0 auto', maxWidth: '100%', opacity: disabled ? 0.6 : 1,
      }}
    >
      {shortCode}
      {assignment.course?.visibleInTimetable === false && (
        <span title="Σε συνεννόηση — δεν εμφανίζεται στο δημόσιο πρόγραμμα"
              style={{ fontSize: '10px' }}>🤝</span>
      )}
    </div>
  );
}

function ExamMoveModal({
  assignment, rooms, examTimeSlots, availableDates, onMoved, onClose, onError, onSuccess,
}: {
  assignment: TimetableAssignment;
  rooms: Room[];
  examTimeSlots: TimeSlot[];
  availableDates: string[];
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
  const [detailsAssignment, setDetailsAssignment] = useState<TimetableAssignment | null>(null);
  const [draggingAssignment,setDraggingAssignment]= useState<TimetableAssignment | null>(null);
  const [issuesModal,       setIssuesModal]       = useState<'ERROR' | 'WARNING' | null>(null);
  const [printOpen,         setPrintOpen]         = useState(false);

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

  // Διαθέσιμες οντότητες ανά διάσταση ομαδοποίησης (από τα τρέχοντα visible assignments).
  const printAvailable = useMemo<Record<PrintGroupBy, { key: string; label: string }[]>>(() => {
    const visible = assignments.filter((a) => a.course?.visibleInTimetable !== false);
    const semMap = new Map<string, string>();          // key `sem-${n}` (REQUIRED μόνο) → label
    const roomMap = new Map<string, string>();
    const teacherSet = new Set<string>();
    const electiveMap = new Map<string, { label: string; sortKey: string }>();  // buckets επιλογής (fall/spring/λοιπά)
    for (const a of visible) {
      if (a.course?.courseType === 'REQUIRED') {
        if (a.course.semester != null) semMap.set(`sem-${a.course.semester}`, `${a.course.semester}ο Εξάμηνο`);
      } else {
        const b = electiveBucket(a.course?.semesterType);
        electiveMap.set(b.key, { label: b.title, sortKey: b.sortKey });
      }
      if (a.room?.id != null) roomMap.set(String(a.room.id), a.room.code);
      for (const name of parseTeachers(a.course?.teachersText)) teacherSet.add(name);
    }
    const semester = Array.from(semMap.entries())
      .sort((x, y) => Number(x[0].slice(4)) - Number(y[0].slice(4)))
      .map(([key, label]) => ({ key, label }));
    // Buckets επιλογής μετά τα εξάμηνα, με σειρά Χειμ→Εαρ→λοιπά (sortKey 97/98/99).
    for (const [key, v] of Array.from(electiveMap.entries()).sort((x, y) => x[1].sortKey.localeCompare(y[1].sortKey))) {
      semester.push({ key, label: v.label });
    }
    return {
      semester,
      room: Array.from(roomMap.entries()).sort((x, y) => x[1].localeCompare(y[1], 'el')).map(([key, label]) => ({ key, label })),
      teacher: Array.from(teacherSet).sort((x, y) => x.localeCompare(y, 'el')).map((name) => ({ key: name, label: name })),
    };
  }, [assignments]);

  // Πρώτες ημερομηνίες κάθε εβδομάδας: κενό ≥ 2 ημερών (π.χ. Σαββατοκύριακο) = νέα εβδομάδα.
  const weekStartDates = useMemo(() => {
    const set = new Set<string>();
    examDates.forEach((d, i) => {
      if (i === 0) return; // η πρώτη στήλη είναι ήδη στην άκρη — χωρίς separator
      const prev = new Date(examDates[i - 1] + 'T00:00:00').getTime();
      const cur = new Date(d + 'T00:00:00').getTime();
      if ((cur - prev) / 86400000 >= 2) set.add(d);
    });
    return set;
  }, [examDates]);

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

  // «Πότε;» resolver για το ValidationIssuesModal — μόνο για assignment-scoped codes.
  const byId = useMemo(() => new Map(assignments.map(a => [a.id, a])), [assignments]);

  const resolveLocation = useCallback((issue: ValidationIssue): string | null => {
    if (issue.referenceId == null || !ASSIGNMENT_SCOPED.has(issue.code)) return null;
    const a = byId.get(issue.referenceId);
    if (!a?.timeSlot) return null;
    const t = normalizeTime(a.timeSlot.startTime);
    return a.timeSlot.specificDate
      ? `${formatDateHeader(a.timeSlot.specificDate)} ${t}`
      : `${a.timeSlot.dayOfWeek ?? ''} ${t}`.trim();
  }, [byId]);

  function getExamSlotId(date: string, hour: string): number | undefined {
    return examTimeSlots.find(ts =>
      ts.specificDate === date && normalizeTime(ts.startTime) === hour
    )?.id;
  }

  /** Επιστρέφει το slot id για (date, hour) — αν δεν υπάρχει, το δημιουργεί on-demand. */
  async function resolveOrCreateSlotId(date: string, hour: string): Promise<number | undefined> {
    const existing = getExamSlotId(date, hour);
    if (existing) return existing;
    try {
      const res = await timetableService.findOrCreateExamSlot(date, parseInt(hour, 10));
      setExamTimeSlots(prev => [...prev, res.data as unknown as TimeSlot]);
      return res.data.id;
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Αδυναμία δημιουργίας χρονοθυρίδας.');
      return undefined;
    }
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
    const slotId = await resolveOrCreateSlotId(addDate, addHour);
    if (!slotId) return;
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
        courseId: selectedCourseId,
        roomId: opt.room.id,
        timeSlotId: opt.timeSlot.id,
        assignmentType: 'EXAM',
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
    const slotId = await resolveOrCreateSlotId(date, hour);
    if (!slotId) { setDraggingAssignment(null); return; }
    // Κανόνας τμήματος: στην εξεταστική επιτρέπεται μοίρασμα αίθουσας,
    // οπότε η μετακίνηση κρατά την αίθουσα της εξέτασης.
    let roomId = draggingAssignment.room?.id ?? 0;
    if (!roomId) {
      const fallback = rooms.find(r => r.availableForExams !== false);
      if (fallback) roomId = fallback.id;
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
      const res = await timetableService.solve(selectedId, 60);
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

  function printExamSchedule(req: PrintRequest) {
    if (!selectedTimetable) return;
    const tt = selectedTimetable;
    // Μαθήματα «σε συνεννόηση» δεν τυπώνονται στο επίσημο πρόγραμμα.
    const printable = assignments.filter(a => a.course?.visibleInTimetable !== false);
    const assignedDates = new Set(
      printable.map(a => a.timeSlot?.specificDate).filter(Boolean) as string[]
    );
    const globalDates = examDates.filter(d => assignedDates.has(d));
    if (globalDates.length === 0) { alert('Δεν υπάρχουν τοποθετημένες εξετάσεις.'); return; }

    const fmtDM = (d: string): string => {
      const dt = new Date(d + 'T00:00:00');
      return `${dt.getDate()}/${dt.getMonth() + 1}`;
    };
    // Διγράμματα ημερών μόνο για το print header (ξεκάθαρα Τρ/Τε, Πε/Πα — αντί διφορούμενα μονά γράμματα).
    const DAY2 = ['Κυ', 'Δε', 'Τρ', 'Τε', 'Πε', 'Πα', 'Σα']; // Date.getDay(): 0=Κυρ … 6=Σαβ
    const day2Of = (d: string): string => DAY2[new Date(d + 'T00:00:00').getDay()] ?? '';
    const durHours = (a: TimetableAssignment): number => Math.max(1, Math.round((a.examDurationMinutes ?? 180) / 60));

    // Εβδομάδες ως column-groups — από ΟΛΑ τα visible exam dates (global) → σταθερές στήλες/εβδομάδες
    // σε κάθε σελίδα-group. Boundary νέας εβδομάδας = weekStartDates (gap ≥ 2 ημερών).
    type Week = { label: string; dates: string[] };
    const weeks: Week[] = [];
    for (const d of globalDates) {
      if (weeks.length === 0 || weekStartDates.has(d)) weeks.push({ label: '', dates: [d] });
      else weeks[weeks.length - 1].dates.push(d);
    }
    weeks.forEach(w => { w.label = `${fmtDM(w.dates[0])}-${fmtDM(w.dates[w.dates.length - 1])}`; });
    const allDates = weeks.flatMap(w => w.dates);
    const allDateSet = new Set(allDates);

    // rowHours: συνεχόμενο εύρος min..max που καλύπτει ΟΛΕΣ τις ώρες-διάρκειες (start..start+dur-1),
    // ώστε το duration-rowspan να μη γεφυρώνει χάσμα και να χωράνε όλες οι καλυπτόμενες ώρες.
    const occupied = new Set<number>();
    for (const a of printable) {
      const date = a.timeSlot?.specificDate;
      if (!date || !allDateSet.has(date)) continue;
      const sh = parseInt(a.timeSlot?.startTime?.split(':')[0] ?? '', 10);
      if (Number.isNaN(sh)) continue;
      for (let k = 0; k < durHours(a); k++) occupied.add(sh + k);
    }
    if (occupied.size === 0) { alert('Δεν υπάρχουν τοποθετημένες εξετάσεις.'); return; }
    const minH = Math.min(...occupied);
    const maxH = Math.max(...occupied);
    const rowHours: number[] = [];
    for (let h = minH; h <= maxH; h++) rowHours.push(h);

    const tdBase = 'border:1px solid #cbd5e1;padding:2px 3px;vertical-align:top;';

    // Περιεχόμενο κελιού (aSc, πυκνό 20-στηλο): κωδικός & όνομα σε ΞΕΧΩΡΙΣΤΕΣ γραμμές + wrap →
    // κείμενο μένει μέσα στο κελί (fix overflow). Τιμά showSemesterBadge.
    function cellContent(a: TimetableAssignment, dur: number): string {
      const semBadge = req.showSemesterBadge ? ` · Εξ.${esc(a.course.semester)}` : '';
      return `<div style="font-size:7pt;line-height:1.2;overflow-wrap:break-word;">
          <div style="font-weight:700;white-space:nowrap;">${esc(shortCode(a.course.code))}</div>
          <div style="margin-top:1px;">${esc(a.course.name)}</div>
          <div style="font-size:6.5pt;color:#475569;margin-top:2px;">${esc(a.room?.code ?? '')} · ${dur}h${semBadge}</div>
        </div>`;
    }

    // 4-εβδομάδων grid με duration-rowspan: κάθε εξέταση πιάνει examDurationMinutes/60 διαδοχικές γραμμές.
    function buildExamGrid(items: TimetableAssignment[]): string {
      if (!items.some(a => a.timeSlot?.specificDate && allDateSet.has(a.timeSlot.specificDate))) return '';
      const covered = new Map<string, Set<number>>();
      allDates.forEach(d => covered.set(d, new Set<number>()));
      const cellOf = (date: string, hourInt: number) =>
        items.filter(a => a.timeSlot?.specificDate === date && parseInt(a.timeSlot?.startTime?.split(':')[0] ?? '', 10) === hourInt);

      const rows = rowHours.map(hourInt => {
        const tds = allDates.map(date => {
          const cov = covered.get(date)!;
          if (cov.has(hourInt)) return ''; // καλυμμένο από rowspan από πάνω → χωρίς <td>
          const cellItems = cellOf(date, hourInt);
          if (cellItems.length === 1) {
            const a = cellItems[0];
            const dur = durHours(a);
            const run = Math.min(dur, maxH - hourInt + 1); // μην ξεπερνάς το τέλος του grid
            for (let k = 1; k < run; k++) cov.add(hourInt + k);
            const stripe = req.colorByYear ? `border-left:3px solid ${yearColor(a.course.studyYear)};` : '';
            const rs = run > 1 ? ` rowspan="${run}"` : '';
            return `<td${rs} style="${tdBase}${stripe}">${cellContent(a, dur)}</td>`;
          }
          if (cellItems.length === 0) return `<td style="${tdBase}"></td>`;
          // Παράλληλες εξετάσεις ίδιο date+hour → stacked, rowspan 1 (degrade gracefully).
          const stacked = cellItems.map(a => `<div style="margin-bottom:3px;">${cellContent(a, durHours(a))}</div>`).join('');
          return `<td style="${tdBase}">${stacked}</td>`;
        }).join('');
        return `<tr><td style="${tdBase}background:#f8fafc;font-size:7pt;color:#475569;white-space:nowrap;text-align:center;">${hourInt}-${hourInt + 1}</td>${tds}</tr>`;
      }).join('');

      const headTh = 'border:1px solid #cbd5e1;background:#f1f5f9;color:#0f172a;font-weight:600;padding:3px 4px;text-align:center;';
      // Two-level header: πάνω = ετικέτες εβδομάδων (colspan)· κάτω = ημερομηνίες (γράμμα ημέρας + d/m).
      const header = `<tr>
        <th rowspan="2" style="${headTh}font-size:7pt;width:42px;">Ώρα</th>
        ${weeks.map(w => `<th colspan="${w.dates.length}" style="${headTh}font-size:7pt;">${esc(w.label)}</th>`).join('')}
      </tr>
      <tr>
        ${allDates.map(d => `<th style="${headTh}font-size:7pt;font-weight:500;">${day2Of(d)} ${fmtDM(d)}</th>`).join('')}
      </tr>`;

      return `
      <table style="border-collapse:collapse;width:100%;table-layout:fixed;">
        <thead>${header}</thead>
        <tbody>${rows}</tbody>
      </table>`;
    }

    // Ομαδοποίηση: keys μόνο για το req.groupBy, φιλτραρισμένα στα req.selectedKeys.
    // Στο semester mode: REQUIRED → ανά εξάμηνο· υπόλοιπα → ΜΙΑ σελίδα «Μαθήματα Επιλογής» (τελευταία).
    const selected = new Set(req.selectedKeys);
    const keysOf = (a: TimetableAssignment): { key: string; title: string; sortKey: string }[] => {
      if (req.groupBy === 'semester') {
        if (a.course.courseType === 'REQUIRED') {
          const key = `sem-${a.course.semester}`;
          return selected.has(key) ? [{ key, title: `${a.course.semester}ο Εξάμηνο`, sortKey: String(a.course.semester).padStart(2, '0') }] : [];
        }
        const b = electiveBucket(a.course.semesterType);  // Χειμερινού/Εαρινού/λοιπά
        return selected.has(b.key) ? [b] : [];
      }
      if (req.groupBy === 'room') {
        if (!a.room || !selected.has(String(a.room.id))) return [];
        return [{ key: String(a.room.id), title: `Αίθουσα ${a.room.code}`, sortKey: a.room.code }];
      }
      // TODO Φ-directions: add 'direction' groupBy όταν φτιαχτεί το Direction entity
      return parseTeachers(a.course.teachersText)
        .filter(name => selected.has(name))
        .map(name => ({ key: name, title: `Καθ. ${name}`, sortKey: name }));
    };
    const groups = groupItems(printable, keysOf);
    const renderable = groups.map(g => ({ g, html: buildExamGrid(g.items) })).filter(x => x.html);
    if (renderable.length === 0) { alert('Δεν υπάρχουν τοποθετημένες εξετάσεις.'); return; }

    // Μία οντότητα ανά σελίδα (aSc-style: κεντρικός τίτλος, καθαρό grid, footer).
    const bodyHtml = renderable.map(({ g, html }, idx) => {
      const years = req.colorByYear ? Array.from(new Set(g.items.map(a => a.course.studyYear))).sort((x, y) => x - y) : [];
      const legend = years.length
        ? `<div style="display:flex;gap:12px;justify-content:center;font-size:7pt;color:#64748b;margin-top:6px;">${years.map(y => `<span style="display:inline-flex;align-items:center;gap:3px;"><span style="display:inline-block;width:8px;height:8px;border-radius:2px;background:${yearColor(y)};"></span>${y}ο Έτος</span>`).join('')}</div>`
        : '';
      return `
      <div style="${idx < renderable.length - 1 ? 'page-break-after:always;' : ''}">
        <div style="text-align:center;font-size:18pt;font-weight:400;margin:4px 0 2px;">${esc(g.title)}</div>
        <div style="text-align:center;font-size:8pt;color:#64748b;margin-bottom:8px;">Εξεταστική Περίοδος — ${esc(tt.name)} · ΤΜΗΥΠ Πανεπιστήμιο Πατρών</div>
        ${html}${legend}
        <div style="font-size:7pt;color:#94a3b8;margin-top:6px;">Δημιουργία Προγράμματος: ${todayGreek()}</div>
      </div>`;
    }).join('');

    const html = buildPrintDocument({
      title: `Εξεταστική — ${tt.name}`,
      headerHtml: '',
      bodyHtml,
      h1FontSizePt: 12,
      legendGapPx: 10,
      legendWrap: false,
    });
    openAndPrint(html);
  }

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
              {solving ? 'Εκτελείται...' : '⚡ Timefold'}
            </button>
            <button
              onClick={async () => {
                if (!selectedId) return;
                if (!window.confirm('Σίγουρα θέλεις να αφαιρέσεις ΟΛΕΣ τις αναθέσεις του προγράμματος; Η ενέργεια δεν αναιρείται.')) return;
                try {
                  await timetableService.clearAssignments(selectedId);
                  await reloadTimetableData();
                  setMessage('Όλες οι αναθέσεις αφαιρέθηκαν.');
                } catch (err: any) {
                  setError(err?.response?.data?.error || 'Σφάλμα καθαρισμού αναθέσεων.');
                }
              }}
              disabled={solving || saving || assignments.length === 0}
              style={{ ...solverBtn, background: assignments.length > 0 ? '#b91c1c' : '#334155', border: 'none' }}
            >
              🗑 Καθαρισμός
            </button>
            <button
              onClick={() => setPrintOpen(true)}
              disabled={assignments.length === 0}
              style={{ ...solverBtn, background: assignments.length > 0 ? '#0f766e' : '#334155', border: 'none' }}
            >
              🖨 Εκτύπωση
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
            <StatCard label="Errors"   value={validation?.errorCount ?? 0}   color={(validation?.errorCount ?? 0) > 0 ? '#ef4444' : '#22c55e'} onClick={() => setIssuesModal('ERROR')} />
            <StatCard label="Warnings" value={validation?.warningCount ?? 0} color={(validation?.warningCount ?? 0) > 0 ? '#f59e0b' : '#22c55e'} onClick={() => setIssuesModal('WARNING')} />
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
          <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '1.5rem', alignItems: 'start' }}>

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
                  <table style={{ width: '100%', minWidth: `${64 + examDates.length * 96}px`, borderCollapse: 'collapse' }}>
                    <thead>
                      <tr>
                        <th style={thStyle}>Ώρα</th>
                        {examDates.map(date => (
                          <th
                            key={date}
                            style={weekStartDates.has(date) ? { ...thStyle, borderLeft: '3px solid #2563eb' } : thStyle}
                          >{formatDateHeader(date)}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {EXAM_HOURS.map(hour => (
                        <tr key={hour}>
                          <td style={{ ...tdStyle, width: '56px', fontFamily: 'JetBrains Mono, monospace', fontSize: '12px', color: '#94a3b8', fontWeight: 600 }}>
                            {hour}
                          </td>
                          {examDates.map(date => {
                            const cellAssignments = getAssignmentsAt(date, hour);
                            const hint = activeHintMap.get(`${date}-${hour}`);
                            const slotExists = !!getExamSlotId(date, hour);
                            return (
                              <td
                                key={date}
                                onClick={() => !draggingAssignment && openAddModal(date, hour)}
                                onDragOver={e => { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; }}
                                onDrop={e => { e.preventDefault(); handleExamDrop(date, hour); }}
                                title={
                                  hint === 'allowed' ? 'Επιτρεπτή τοποθέτηση' :
                                  hint === 'blocked' ? 'Μη επιτρεπτή τοποθέτηση' :
                                  slotExists ? 'Κλικ για προσθήκη' : 'Κλικ για προσθήκη (θα δημιουργηθεί χρονοθυρίδα)'
                                }
                                style={{
                                  ...tdStyle,
                                  cursor: 'pointer',
                                  background:
                                    hint === 'allowed' ? '#052e16' :
                                    hint === 'blocked' ? '#1c0a0a' :
                                    cellAssignments.length > 0 ? '#0d1b2e' : '#080f1a',
                                  borderLeft:
                                    hint === 'allowed' ? '3px solid #22c55e' :
                                    hint === 'blocked' ? '3px solid #7f1d1d' :
                                    weekStartDates.has(date) ? '3px solid #2563eb' :
                                    '1px solid #1a2744',
                                }}
                              >
                                {!slotExists ? (
                                  <span style={{ color: '#16263d', fontSize: '11px' }}>+ Προσθήκη</span>
                                ) : cellAssignments.length === 0 ? (
                                  <span style={{ color: '#1e3a5f', fontSize: '11px' }}>+ Προσθήκη</span>
                                ) : (
                                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '3px', alignItems: 'flex-start' }}>
                                    {cellAssignments.map(a => (
                                      <ExamCard
                                        key={a.id}
                                        assignment={a}
                                        onShowDetails={() => setDetailsAssignment(a)}
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

            {/* Right Panel → ρέει κάτω full-width (mirror εβδομαδιαίου), panels δίπλα-δίπλα */}
            <aside style={{ display: 'flex', flexDirection: 'row', flexWrap: 'wrap', gap: '1rem', alignItems: 'flex-start' }}>

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

              {/* Validation errors (mirror weekly «Πρώτα σφάλματα») */}
              {validation && validation.errors.length > 0 && (
                <section style={panelStyle}>
                  <h3 style={panelTitle}>Πρώτα σφάλματα ({validation.errorCount})</h3>
                  <div style={{ maxHeight: '200px', overflowY: 'auto' }}>
                    {validation.errors.slice(0, 8).map((e, i) => (
                      <div key={i} style={{ color: '#f87171', fontSize: '11px', marginBottom: '4px', lineHeight: 1.35 }}>{e.message}</div>
                    ))}
                  </div>
                </section>
              )}

              {/* Validation warnings */}
              {validation && validation.warnings.length > 0 && (
                <section style={panelStyle}>
                  <h3 style={panelTitle}>Πρώτα προειδοποιήσεις ({validation.warningCount})</h3>
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

      {/* Details modal (shared με το εβδομαδιαίο) — chip click → λεπτομέρειες + actions */}
      <AssignmentDetailsModal
        assignment={detailsAssignment}
        onClose={() => setDetailsAssignment(null)}
        onMove={() => { if (detailsAssignment) setMovingAssignment(detailsAssignment); setDetailsAssignment(null); }}
        onDelete={() => { if (detailsAssignment) removeAssignment(detailsAssignment.id); setDetailsAssignment(null); }}
        disabled={saving}
      />

      {/* Move modal */}
      {movingAssignment && (
        <ExamMoveModal
          assignment={movingAssignment}
          rooms={rooms}
          examTimeSlots={examTimeSlots}
          availableDates={examDates}
          onMoved={async () => { setMovingAssignment(null); await reloadTimetableData(); }}
          onClose={() => setMovingAssignment(null)}
          onError={setError}
          onSuccess={setMessage}
        />
      )}

      {/* Issues modal (shared με το εβδομαδιαίο) — κλικ σε Errors/Warnings StatCard */}
      <ValidationIssuesModal
        severity={issuesModal}
        issues={issuesModal === 'ERROR' ? (validation?.errors ?? []) : issuesModal === 'WARNING' ? (validation?.warnings ?? []) : []}
        onClose={() => setIssuesModal(null)}
        getLocation={resolveLocation}
      />

      <PrintOptionsModal
        open={printOpen}
        onClose={() => setPrintOpen(false)}
        showTypeToggle={false}
        available={printAvailable}
        onPrint={(req) => printExamSchedule(req)}
      />
    </div>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const thStyle: CSSProperties = {
  padding: '6px 6px', background: '#0a1628', color: '#94a3b8',
  borderBottom: '1px solid #1a2744', textAlign: 'center',
  fontSize: '11px', fontFamily: 'JetBrains Mono, monospace', whiteSpace: 'nowrap',
};

const tdStyle: CSSProperties = {
  padding: '4px', verticalAlign: 'top',
  borderBottom: '1px solid #1a2744', borderRight: '1px solid #1a2744',
  minHeight: '72px', minWidth: '88px',
};

const panelStyle: CSSProperties = {
  background: '#0d1b2e', border: '1px solid #1a2744', borderRadius: '10px', padding: '1rem',
  flex: '1 1 320px',
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
