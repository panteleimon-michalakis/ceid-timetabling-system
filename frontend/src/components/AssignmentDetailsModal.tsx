import type { CSSProperties } from 'react';
import type { TimetableAssignment } from '../types';

// Local copy (ίδιο pattern με MoveAssignmentModal.tsx) — αποφεύγουμε import από page
// (αρχιτεκτονικά ανάποδο) και νέο react-refresh debt. Consolidation σε shared module
// = follow-up (μαζί με τα αντίγραφα σε WeeklyTimetable + MoveAssignmentModal).
const assignmentTypeLabels: Record<string, string> = {
  LECTURE: 'Θεωρία',
  TUTORIAL: 'Φροντιστήριο',
  LAB: 'Εργαστήριο',
  EXAM: 'Εξέταση',
};

const DAY_LABELS: Record<string, string> = {
  MONDAY: 'Δευτέρα', TUESDAY: 'Τρίτη', WEDNESDAY: 'Τετάρτη',
  THURSDAY: 'Πέμπτη', FRIDAY: 'Παρασκευή',
};

const courseTypeLabels: Record<string, string> = {
  REQUIRED: 'Υποχρεωτικό',
  REQUIRED_ELECTIVE: 'Υποχρεωτικό επιλογής',
  EXTERNAL: 'Εξωτερικό',
  GENERAL_EDUCATION: 'Γενικής παιδείας',
};

function normalizeTime(value?: string | null): string {
  if (!value) return '';
  return value.length >= 5 ? value.slice(0, 5) : value;
}

interface AssignmentDetailsModalProps {
  assignment: TimetableAssignment | null;
  onClose: () => void;
}

export default function AssignmentDetailsModal({ assignment, onClose }: AssignmentDetailsModalProps) {
  if (!assignment) return null;

  const { course, room, timeSlot } = assignment;

  const teachers = (course?.teachersText ?? '')
    .split(/[,;]/)
    .map((t) => t.trim())
    .filter(Boolean)
    .join(', ') || '—';

  const typeLabel = assignmentTypeLabels[assignment.assignmentType] ?? assignment.assignmentType;
  const courseTypeLabel = course?.courseType
    ? (courseTypeLabels[course.courseType] ?? course.courseType)
    : '—';
  const slotText = timeSlot
    ? `${DAY_LABELS[timeSlot.dayOfWeek] ?? timeSlot.dayOfWeek} ${normalizeTime(timeSlot.startTime)}–${normalizeTime(timeSlot.endTime)}`
    : '—';

  const rows: Array<{ label: string; value: string | number }> = [
    { label: 'Όνομα', value: course?.name ?? '—' },
    { label: 'Διδάσκων(-οντες)', value: teachers },
    { label: 'Τύπος ώρας', value: typeLabel },
    { label: 'Αίθουσα', value: room ? `${room.code} — ${room.name}` : '—' },
    { label: 'Χωρητικότητα', value: room?.capacity ?? '—' },
    { label: 'Εκτιμ. φοιτητές', value: course?.expectedStudents ?? '—' },
    { label: 'Ώρες', value: `Θ:${course?.lectureHours ?? 0} Φ:${course?.tutorialHours ?? 0} Ε:${course?.labHours ?? 0}` },
    { label: 'Εξάμηνο / Έτος', value: `${course?.semester ?? '—'} / ${course?.studyYear ?? '—'}` },
    { label: 'Τύπος μαθήματος', value: courseTypeLabel },
    { label: 'ECTS', value: course?.ects ?? '—' },
    { label: 'Slot', value: slotText },
  ];

  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={modalStyle} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h2 style={{ fontSize: '1.2rem', margin: 0 }}>
            {course?.code}
            {course?.visibleInTimetable === false && (
              <span title="Σε συνεννόηση — δεν εμφανίζεται στο δημόσιο πρόγραμμα"
                    style={{ marginLeft: 6, fontSize: '0.85rem' }}>🤝</span>
            )}
          </h2>
          <button onClick={onClose} title="Κλείσιμο" style={closeBtnStyle}>×</button>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          {rows.map((r) => (
            <div
              key={r.label}
              style={{ display: 'flex', justifyContent: 'space-between', gap: '1rem', fontSize: '0.9rem' }}
            >
              <span style={{ color: '#94a3b8', flexShrink: 0 }}>{r.label}</span>
              <span style={{ color: '#e2e8f0', textAlign: 'right', fontWeight: 600 }}>{r.value}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

const overlayStyle: CSSProperties = {
  position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
  background: 'rgba(0,0,0,0.72)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  zIndex: 1000,
};

const modalStyle: CSSProperties = {
  background: '#111827', border: '1px solid #334155',
  borderRadius: '14px', padding: '1.5rem',
  width: '520px', maxHeight: '86vh', overflowY: 'auto',
};

const closeBtnStyle: CSSProperties = {
  border: 'none', borderRadius: '6px', background: '#334155', color: '#fff',
  cursor: 'pointer', padding: '0.2rem 0.6rem', fontSize: '1.1rem', lineHeight: 1,
};
