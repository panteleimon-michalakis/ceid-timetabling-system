import { useState } from 'react';
import type { CSSProperties } from 'react';
import { timetableService } from '../api/services';
import type { TimetableAssignment, Room, TimeSlot } from '../types';
import { getErrorMessage } from '../utils/errors';

const DAY_LABELS: Record<string, string> = {
  MONDAY: 'Δευτέρα', TUESDAY: 'Τρίτη', WEDNESDAY: 'Τετάρτη',
  THURSDAY: 'Πέμπτη', FRIDAY: 'Παρασκευή',
};

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
const HOURS = [
  '09:00', '10:00', '11:00', '12:00', '13:00', '14:00',
  '15:00', '16:00', '17:00', '18:00', '19:00', '20:00',
];

function normalizeTime(value?: string | null): string {
  if (!value) return '';
  return value.length >= 5 ? value.slice(0, 5) : value;
}

interface MoveAssignmentModalProps {
  assignment: TimetableAssignment;
  rooms: Room[];
  timeSlots: TimeSlot[];
  onMoved: () => void;
  onClose: () => void;
  onError: (msg: string) => void;
  onSuccess: (msg: string) => void;
  // Non-blocking (Feature #2): advisory warnings από επιτυχημένο (200) move.
  onWarnings?: (warnings: string[]) => void;
}

export default function MoveAssignmentModal({
  assignment,
  rooms,
  timeSlots,
  onMoved,
  onClose,
  onError,
  onSuccess,
  onWarnings,
}: MoveAssignmentModalProps) {
  const currentDay = assignment.timeSlot?.dayOfWeek || '';
  const currentHour = normalizeTime(assignment.timeSlot?.startTime);
  const currentRoomId = assignment.room?.id || 0;

  const [selectedDay, setSelectedDay] = useState(currentDay);
  const [selectedHour, setSelectedHour] = useState(currentHour);
  const [selectedRoomId, setSelectedRoomId] = useState<number>(currentRoomId);
  const [saving, setSaving] = useState(false);

  const hasChanged = selectedDay !== currentDay
    || selectedHour !== currentHour
    || selectedRoomId !== currentRoomId;

  function getSlotId(day: string, hour: string) {
    const slot = timeSlots.find((s) =>
      s.dayOfWeek === day
      && normalizeTime(s.startTime) === hour
      && (!s.slotType || s.slotType === 'SEMESTER')
    );
    return slot?.id;
  }

  async function handleMove() {
    if (!hasChanged) {
      onClose();
      return;
    }

    const slotId = getSlotId(selectedDay, selectedHour);
    if (!slotId) {
      onError('Δεν βρέθηκε χρονοθυρίδα για αυτή την ώρα.');
      return;
    }

    setSaving(true);

    try {
      const result = await timetableService.moveAssignment(assignment.id, {
        timeSlotId: slotId,
        roomId: selectedRoomId,
      });

      onSuccess(`Το μάθημα ${assignment.course?.name} μετακινήθηκε.`);
      onWarnings?.(result.warnings ?? []);
      onMoved();
      onClose();
    } catch (err) {
      onError(getErrorMessage(err, 'Σφάλμα κατά τη μετακίνηση.'));
    } finally {
      setSaving(false);
    }
  }

  const assignmentTypeLabels: Record<string, string> = {
    LECTURE: 'Θεωρία', TUTORIAL: 'Φροντιστήριο', LAB: 'Εργαστήριο', EXAM: 'Εξέταση',
  };

  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={modalStyle} onClick={(e) => e.stopPropagation()}>
        <h2 style={{ fontSize: '1.15rem', marginBottom: '0.5rem' }}>Μετακίνηση μαθήματος</h2>

        {/* Course info */}
        <div style={{ background: '#1e293b', padding: '0.75rem', borderRadius: '8px', marginBottom: '1rem' }}>
          <div style={{ fontWeight: 700 }}>{assignment.course?.name}</div>
          <div style={{ color: '#94a3b8', fontSize: '0.8rem', marginTop: '0.2rem' }}>
            {assignment.course?.code} · {assignmentTypeLabels[assignment.assignmentType] || assignment.assignmentType}
          </div>
          <div style={{ color: '#64748b', fontSize: '0.8rem', marginTop: '0.2rem' }}>
            Τρέχουσα θέση: {DAY_LABELS[currentDay] || currentDay} {currentHour} · {assignment.room?.code}
          </div>
        </div>

        {/* Day selector */}
        <label style={labelStyle}>Ημέρα</label>
        <div style={{ display: 'flex', gap: '0.4rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
          {DAYS.map((day) => (
            <button
              key={day}
              onClick={() => setSelectedDay(day)}
              style={{
                padding: '0.45rem 0.7rem',
                borderRadius: '6px',
                border: 'none',
                cursor: 'pointer',
                background: selectedDay === day ? '#2563eb' : '#334155',
                color: '#fff',
                fontWeight: selectedDay === day ? 700 : 400,
                fontSize: '0.85rem',
              }}
            >
              {DAY_LABELS[day]}
            </button>
          ))}
        </div>

        {/* Hour selector */}
        <label style={labelStyle}>Ώρα</label>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '0.4rem', marginBottom: '1rem' }}>
          {HOURS.map((hour) => (
            <button
              key={hour}
              onClick={() => setSelectedHour(hour)}
              style={{
                padding: '0.4rem',
                borderRadius: '6px',
                border: 'none',
                cursor: 'pointer',
                background: selectedHour === hour ? '#2563eb' : '#334155',
                color: '#fff',
                fontWeight: selectedHour === hour ? 700 : 400,
                fontSize: '0.85rem',
              }}
            >
              {hour}
            </button>
          ))}
        </div>

        {/* Room selector */}
        <label style={labelStyle}>Αίθουσα</label>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '0.4rem', marginBottom: '1.2rem' }}>
          {rooms
            .filter((r) => r.availableForSemester !== false)
            .map((room) => (
              <button
                key={room.id}
                onClick={() => setSelectedRoomId(room.id)}
                style={{
                  padding: '0.5rem',
                  borderRadius: '8px',
                  cursor: 'pointer',
                  textAlign: 'center',
                  background: selectedRoomId === room.id ? '#3b82f633' : '#0f172a',
                  border: selectedRoomId === room.id ? '2px solid #3b82f6' : '2px solid #334155',
                  color: '#fff',
                }}
              >
                <div style={{ fontWeight: 700 }}>{room.code}</div>
                <div style={{ fontSize: '0.7rem', color: '#94a3b8' }}>Χωρ: {room.capacity}</div>
              </button>
            ))}
        </div>

        {/* Change indicator */}
        {hasChanged && (
          <div style={{ fontSize: '0.85rem', color: '#fbbf24', marginBottom: '0.75rem' }}>
            Νέα θέση: {DAY_LABELS[selectedDay]} {selectedHour} · {rooms.find((r) => r.id === selectedRoomId)?.code || '?'}
          </div>
        )}

        {/* Buttons */}
        <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
          <button onClick={onClose} style={secondaryBtnStyle}>Ακύρωση</button>
          <button
            onClick={handleMove}
            disabled={saving || !hasChanged}
            style={{
              ...primaryBtnStyle,
              background: hasChanged ? '#2563eb' : '#334155',
              opacity: hasChanged ? 1 : 0.5,
            }}
          >
            {saving ? 'Μετακίνηση...' : 'Μετακίνηση'}
          </button>
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

const labelStyle: CSSProperties = {
  display: 'block', color: '#94a3b8', fontSize: '0.85rem', marginBottom: '0.3rem',
};

const primaryBtnStyle: CSSProperties = {
  padding: '0.6rem 1rem', border: 'none', borderRadius: '8px',
  background: '#2563eb', color: '#fff', fontWeight: 700, cursor: 'pointer',
};

const secondaryBtnStyle: CSSProperties = {
  padding: '0.6rem 1rem', border: 'none', borderRadius: '8px',
  background: '#334155', color: '#fff', fontWeight: 700, cursor: 'pointer',
};