import { useCallback, useEffect, useMemo, useState } from 'react';
import type { CSSProperties } from 'react';
import api from '../api/client';
import { courseService, roomService, timeSlotService, timetableService } from '../api/services';
import type {
  Course,
  PlacementOption,
  PlacementOptionsResponse,
  Room,
  TimeSlot,
  TimetableAssignment,
  TimetableProgress,
  TimetableValidationReport,
  ValidationIssue,
} from '../types';
import TimetableSelector from '../components/TimetableSelector';
import MoveAssignmentModal from '../components/MoveAssignmentModal';
import AssignmentDetailsModal from '../components/AssignmentDetailsModal';
import ValidationIssuesModal from '../components/ValidationIssuesModal';
import { esc, shortCode, TYPE_COLORS, ALL_HOURS, yearColor, buildPrintDocument, openAndPrint, groupItems, parseTeachers, todayGreek, electiveBucket } from '../utils/printTimetable';
import type { PrintGroupBy } from '../utils/printTimetable';
import PrintOptionsModal from '../components/PrintOptionsModal';
import type { PrintRequest } from '../components/PrintOptionsModal';

const DAYS = [
  { key: 'MONDAY', label: 'Δευτέρα' },
  { key: 'TUESDAY', label: 'Τρίτη' },
  { key: 'WEDNESDAY', label: 'Τετάρτη' },
  { key: 'THURSDAY', label: 'Πέμπτη' },
  { key: 'FRIDAY', label: 'Παρασκευή' },
];

const DAY_LABELS: Record<string, string> = {
  MONDAY: 'Δευτέρα',
  TUESDAY: 'Τρίτη',
  WEDNESDAY: 'Τετάρτη',
  THURSDAY: 'Πέμπτη',
  FRIDAY: 'Παρασκευή',
};

function dayLabel(day?: string | null): string {
  if (!day) return '';
  return DAY_LABELS[day] ?? day;
}

// Validation codes των οποίων το referenceId ΕΙΝΑΙ assignment id (→ επιλύονται σε
// συγκεκριμένη χρονοθυρίδα). Ίδιο set με το ExamTimetable. Τα υπόλοιπα codes έχουν
// course id ή null — ΜΗΝ τα αναζητάς στο assignments-by-id.
const ASSIGNMENT_SCOPED = new Set([
  'INVALID_ASSIGNMENT', 'SEMESTER_MISMATCH', 'LAB_ROOM_REQUIRED', 'FIRST_YEAR_ROOM',
  'REQUIRED_ROOM', 'SHARED_EXAM_ROOM', 'ROOM_CONFLICT', 'SAME_COURSE_SAME_SLOT',
  'TEACHER_CONFLICT', 'REQUIRED_YEAR_EXAM_SAME_DATE', 'REQUIRED_YEAR_CONFLICT',
]);

const HOURS = [
  '09:00', '10:00', '11:00', '12:00', '13:00', '14:00',
  '15:00', '16:00', '17:00', '18:00', '19:00', '20:00',
];

const ASSIGNMENT_TYPES = ['LECTURE', 'TUTORIAL', 'LAB'] as const;
type AssignmentTypeKey = typeof ASSIGNMENT_TYPES[number];

type AutoPlacedEntry = {
  courseCode: string;
  courseName: string;
  assignmentType: string;
  day: string;
  startTime: string;
  roomCode: string;
  score: number;
};

type AutoFailedEntry = {
  courseCode: string;
  courseName: string;
  assignmentType: string;
  remainingHourIndex: number;
  mainReason: string;
  topReasons: string[];
};

type AutoScheduleResult = {
  timetableId: number;
  totalPlaced: number;
  totalFailed: number;
  totalSkipped: number;
  totalCourses: number;
  log: string[];
  placedEntries?: AutoPlacedEntry[];
  failedEntries?: AutoFailedEntry[];
  failureSummary?: string[];
  topFailureReasons?: string[];
  topFailureMessages?: string[];
};

const assignmentTypeLabels: Record<string, string> = {
  LECTURE: 'Θεωρία',
  TUTORIAL: 'Φροντιστήριο',
  LAB: 'Εργαστήριο',
};

// Χρωματική λωρίδα κάρτας ανά τύπο ώρας (ίδια χρώματα με το print legend, ~γρ. 915).
const assignmentTypeColors: Record<string, string> = {
  LECTURE: '#2563eb',
  TUTORIAL: '#16a34a',
  LAB: '#d97706',
};

// (assignmentTypeLabelsGenitive αφαιρέθηκε — χρησιμοποιούνταν μόνο στους client
//  pre-guards του submitManualAssignment, που έγιναν non-blocking στο Feature #2.)

function getErrorMessage(error: any): string {
  return error?.response?.data?.error
    || error?.response?.data?.message
    || error?.message
    || 'Προέκυψε άγνωστο σφάλμα.';
}

function normalizeTime(value?: string | null): string {
  if (!value) return '';
  return value.length >= 5 ? value.slice(0, 5) : value;
}

function assignmentKey(dayOfWeek: string, startTime: string) {
  return `${dayOfWeek}-${normalizeTime(startTime)}`;
}

function hourEnd(hour: string) {
  const h = Number(hour.slice(0, 2));
  return `${String(h + 1).padStart(2, '0')}:00`;
}

function getRequiredHoursForType(course: Course, type: string) {
  if (type === 'LECTURE') return course.lectureHours;
  if (type === 'TUTORIAL') return course.tutorialHours;
  if (type === 'LAB') return course.labHours;
  return 0;
}

function getPlacedHoursForType(
  assignments: TimetableAssignment[],
  courseId: number,
  type: string
) {
  return assignments.filter((assignment) =>
    assignment.course?.id === courseId && assignment.assignmentType === type
  ).length;
}

function getFirstAvailableType(
  course: Course,
  assignments: TimetableAssignment[]
): AssignmentTypeKey {
  for (const type of ASSIGNMENT_TYPES) {
    const required = getRequiredHoursForType(course, type);
    const placed = getPlacedHoursForType(assignments, course.id, type);

    if (required > 0 && placed < required) {
      return type;
    }
  }

  for (const type of ASSIGNMENT_TYPES) {
    if (getRequiredHoursForType(course, type) > 0) {
      return type;
    }
  }

  return 'LECTURE';
}

function hasHoursForType(course: Course | undefined, type: string) {
  if (!course) return false;
  return getRequiredHoursForType(course, type) > 0;
}

function isCourseRelevantForTimetable(course: Course, timetable: any, yearFilter: number) {
  if (!timetable) return false;

  if (course.semesterType && timetable.semesterType && course.semesterType !== timetable.semesterType) {
    return false;
  }

  if (course.active === false) return false;
  if (course.visibleInTimetable === false) return false;

  if (yearFilter > 0 && course.studyYear !== yearFilter) {
    return false;
  }

  return course.lectureHours + course.tutorialHours + course.labHours > 0;
}

export default function WeeklyTimetable() {
  const [timetables, setTimetables] = useState<any[]>([]);
  const [selectedTimetableId, setSelectedTimetableId] = useState<number | null>(null);

  const [assignments, setAssignments] = useState<TimetableAssignment[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [timeSlots, setTimeSlots] = useState<TimeSlot[]>([]);

  const [progress, setProgress] = useState<TimetableProgress | null>(null);
  const [validation, setValidation] = useState<TimetableValidationReport | null>(null);
  const [placementOptions, setPlacementOptions] = useState<PlacementOptionsResponse | null>(null);

  const [selectedCourseId, setSelectedCourseId] = useState<number | ''>('');
  const [selectedAssignmentType, setSelectedAssignmentType] = useState<AssignmentTypeKey>('LECTURE');
  const [selectedRoomId, setSelectedRoomId] = useState<number | ''>('');

  const [yearFilter, setYearFilter] = useState<number>(0);
  const [courseSearch, setCourseSearch] = useState('');

  const [showAddModal, setShowAddModal] = useState(false);
  const [selectedDay, setSelectedDay] = useState('');
  const [selectedHour, setSelectedHour] = useState('');

  const [loading, setLoading] = useState(true);
  const [loadingTimetable, setLoadingTimetable] = useState(false);
  const [loadingOptions, setLoadingOptions] = useState(false);
  const [saving, setSaving] = useState(false);

  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  // Non-blocking advisory warnings (Feature #2): η τοποθέτηση γίνεται, αλλά το backend
  // επιστρέφει προειδοποιήσεις (π.χ. room double-book) — εμφανίζονται ως amber notice.
  const [warnings, setWarnings] = useState<string[]>([]);

  const [movingAssignment, setMovingAssignment] = useState<TimetableAssignment | null>(null);
  const [detailsAssignment, setDetailsAssignment] = useState<TimetableAssignment | null>(null);
  const [issuesModal, setIssuesModal] = useState<'ERROR' | 'WARNING' | null>(null);
  const [printOpen, setPrintOpen] = useState(false);
  const [draggingAssignment, setDraggingAssignment] = useState<TimetableAssignment | null>(null);
  const [dragOptions, setDragOptions] = useState<PlacementOptionsResponse | null>(null);
  const [instantHintMap, setInstantHintMap] = useState<Map<string, 'allowed' | 'blocked' | 'preferred'>>(new Map());
  const [teacherConstraintsMap, setTeacherConstraintsMap] = useState<
    Map<string, Array<{dayOfWeek: string; hour: number; constraintType: string}>>
  >(new Map());

  const [scheduling, setScheduling] = useState(false);
  const [autoScheduleResult, setAutoScheduleResult] = useState<AutoScheduleResult | null>(null);

  const selectedTimetable = useMemo(
    () => timetables.find((t: any) => t.id === selectedTimetableId),
    [timetables, selectedTimetableId]
  );

  const eligibleCourses = useMemo(() => {
    return courses
      .filter((course) => isCourseRelevantForTimetable(course, selectedTimetable, yearFilter))
      .sort((a, b) => {
        if (a.semester !== b.semester) return a.semester - b.semester;
        return a.name.localeCompare(b.name, 'el');
      });
  }, [courses, selectedTimetable, yearFilter]);

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

  const selectedCourse = useMemo(
    () => courses.find((course) => course.id === selectedCourseId),
    [courses, selectedCourseId]
  );

  const modalFilteredCourses = useMemo(() => {
    const search = courseSearch.trim().toLowerCase();

    if (!search) {
      return eligibleCourses;
    }

    return eligibleCourses.filter((course) =>
      course.name.toLowerCase().includes(search)
      || course.code.toLowerCase().includes(search)
      || course.teachersText?.toLowerCase().includes(search)
    );
  }, [eligibleCourses, courseSearch]);

  const assignmentsBySlot = useMemo(() => {
    const map = new Map<string, TimetableAssignment[]>();

    for (const assignment of assignments) {
      if (!assignment.timeSlot?.dayOfWeek || !assignment.timeSlot?.startTime) continue;

      const key = assignmentKey(
        assignment.timeSlot.dayOfWeek,
        assignment.timeSlot.startTime
      );

      const list = map.get(key) ?? [];
      list.push(assignment);
      map.set(key, list);
    }

    return map;
  }, [assignments]);

  // «Πότε;» resolver για το ValidationIssuesModal — μόνο για assignment-scoped codes.
  // Weekly: δεν υπάρχει specificDate → πάντα το dayOfWeek branch.
  const assignmentsById = useMemo(
    () => new Map(assignments.map((a) => [a.id, a])),
    [assignments]
  );

  const resolveLocation = useCallback((issue: ValidationIssue): string | null => {
    if (issue.referenceId == null || !ASSIGNMENT_SCOPED.has(issue.code)) return null;
    const a = assignmentsById.get(issue.referenceId);
    if (!a?.timeSlot) return null;
    const t = normalizeTime(a.timeSlot.startTime);
    return a.timeSlot.specificDate
      ? `${a.timeSlot.specificDate} ${t}`.trim()
      : `${dayLabel(a.timeSlot.dayOfWeek)} ${t}`.trim();
  }, [assignmentsById]);

const slotHintMap = useMemo(() => {
    const map = new Map<string, 'allowed' | 'blocked'>();

    if (!placementOptions) return map;

    for (const option of placementOptions.options) {
      if (!option.timeSlot?.dayOfWeek || !option.timeSlot?.startTime) continue;

      const key = assignmentKey(option.timeSlot.dayOfWeek, option.timeSlot.startTime);

      if (option.allowed) {
        if (map.get(key) !== 'allowed') {
          map.set(key, 'allowed');
        }
      } else {
        if (!map.has(key)) {
          map.set(key, 'blocked');
        }
      }
    }

    return map;
  }, [placementOptions]);

const dragSlotHintMap = useMemo(() => {
  const map = new Map<string, 'allowed' | 'blocked'>();
  if (!dragOptions) return map;
  for (const option of dragOptions.options) {
    if (!option.timeSlot?.dayOfWeek || !option.timeSlot?.startTime) continue;
    const key = assignmentKey(option.timeSlot.dayOfWeek, option.timeSlot.startTime);
    if (option.allowed) {
      if (map.get(key) !== 'allowed') map.set(key, 'allowed');
    } else {
      if (!map.has(key)) map.set(key, 'blocked');
    }
  }
  return map;
}, [dragOptions]);

// During drag: use API hints if loaded, else instant frontend hints
const activeDragMap = dragSlotHintMap.size > 0 ? dragSlotHintMap : instantHintMap;
const activeHintMap = draggingAssignment ? activeDragMap : slotHintMap;

  async function loadInitialData() {
    setLoading(true);
    setError(null);

    try {
      const [timetablesRes, coursesRes, roomsRes, timeSlotsRes, teacherConRes] = await Promise.all([
        timetableService.getAll(),
        courseService.getAll(),
        roomService.getAll(),
        timeSlotService.getAll(),
        api.get('/teachers/constraints/all').catch(() => ({ data: [] })),
      ]);

      setTimetables((timetablesRes.data as any[]).filter((t: any) => t.timetableType === 'SEMESTER'));
      const tcMap = new Map<string, Array<{dayOfWeek: string; hour: number; constraintType: string}>>();
      for (const entry of (teacherConRes.data as any[])) {
        tcMap.set((entry.teacherName as string).toLowerCase(), entry.constraints);
      }
      setTeacherConstraintsMap(tcMap);
      setCourses(coursesRes.data);
      setRooms(roomsRes.data);
      setTimeSlots(timeSlotsRes.data);

setSelectedTimetableId((previous) => {
  if (previous && timetablesRes.data.some((timetable: any) => timetable.id === previous)) {
    return previous;
  }

  return null;
});
    } catch {
      setError('Δεν μπόρεσα να φορτώσω τα αρχικά δεδομένα. Έλεγξε ότι το backend τρέχει στο http://localhost:8080.');
    } finally {
      setLoading(false);
    }
  }

  async function loadTimetableData(timetableId: number) {
    setLoadingTimetable(true);
    setError(null);

    try {
      const [assignmentsRes, progressRes, validationRes] = await Promise.all([
        timetableService.getAssignments(timetableId),
        timetableService.getProgress(timetableId),
        timetableService.validate(timetableId),
      ]);

      setAssignments(assignmentsRes.data);
      setProgress(progressRes.data);
      setValidation(validationRes.data);
    } catch {
      setError('Δεν μπόρεσα να φορτώσω το επιλεγμένο πρόγραμμα από το backend.');
    } finally {
      setLoadingTimetable(false);
    }
  }

function handleDragStart(assignment: TimetableAssignment) {
  setDraggingAssignment(assignment);

  // 1. INSTANT: compute hints locally from loaded data (<1ms)
  const blocked = new Set<string>();

  // Track: rooms used per slot, years with required courses per slot
  const roomsPerSlot = new Map<string, Set<number>>();
  const reqYearsPerSlot = new Map<string, Set<number>>();
  const teacherText = assignment.course.teachersText?.trim().toLowerCase() ?? '';

  for (const a of assignments) {
    if (a.id === assignment.id) continue;
    if (!a.timeSlot?.dayOfWeek || !a.timeSlot?.startTime) continue;
    const key = assignmentKey(a.timeSlot.dayOfWeek, normalizeTime(a.timeSlot.startTime));

    // Teacher conflict
    const aTeacher = a.course.teachersText?.trim().toLowerCase() ?? '';
    if (teacherText && aTeacher && teacherText === aTeacher) {
      blocked.add(key);
    }

    // Room tracking
    if (!roomsPerSlot.has(key)) roomsPerSlot.set(key, new Set());
    if (a.room?.id) roomsPerSlot.get(key)!.add(a.room.id);

    // Required same-year tracking
    if (a.course.courseType === 'REQUIRED' || a.course.courseType === 'REQUIRED_ELECTIVE') {
      if (!reqYearsPerSlot.has(key)) reqYearsPerSlot.set(key, new Set());
      reqYearsPerSlot.get(key)!.add(a.course.studyYear);
    }
  }

  // Block slots where all rooms are taken
  const totalRooms = rooms.length;
  for (const [key, usedRooms] of roomsPerSlot) {
    if (usedRooms.size >= totalRooms) blocked.add(key);
  }

  // Block slots where a required course of same year is already placed
  if (assignment.course.courseType === 'REQUIRED' || assignment.course.courseType === 'REQUIRED_ELECTIVE') {
    const year = assignment.course.studyYear;
    for (const [key, years] of reqYearsPerSlot) {
      if (years.has(year)) blocked.add(key);
    }
  }

  // Teacher availability constraints (BLOCKED → red, PREFERRED → bright green)
  const preferred = new Set<string>();
  const teacherNames = (assignment.course.teachersText ?? '')
    .split(/[,;]/).map((n: string) => n.trim().toLowerCase()).filter(Boolean);
  for (const name of teacherNames) {
    for (const [mapName, constraints] of teacherConstraintsMap) {
      const lastName = (s: string) => s.split(' ').pop() ?? '';
      if (mapName === name || lastName(mapName) === lastName(name)) {
        for (const c of constraints) {
          const key = assignmentKey(c.dayOfWeek, `${String(c.hour).padStart(2,'0')}:00`);
          if (c.constraintType === 'BLOCKED')    blocked.add(key);
          else if (c.constraintType === 'PREFERRED') preferred.add(key);
        }
        break;
      }
    }
  }

  const instant = new Map<string, 'allowed' | 'blocked' | 'preferred'>();
  for (const ts of timeSlots) {
    if (ts.slotType !== 'SEMESTER' || !ts.dayOfWeek || !ts.startTime) continue;
    const key = assignmentKey(ts.dayOfWeek, normalizeTime(ts.startTime));
    if (!instant.has(key)) {
      if (blocked.has(key))        instant.set(key, 'blocked');
      else if (preferred.has(key)) instant.set(key, 'preferred');
      else                         instant.set(key, 'allowed');
    }
  }
  setInstantHintMap(instant);
}

function handleDragEnd() {
  setDraggingAssignment(null);
  setDragOptions(null);
  setInstantHintMap(new Map());
}

async function handleDrop(day: string, hour: string) {
  if (!draggingAssignment || !selectedTimetableId) return;

  // Non-blocking (Feature #2): δεν μπλοκάρουμε πια σε 'blocked' hint — η τοποθέτηση
  // προχωρά και το backend επιστρέφει advisory warnings. Τα hints/χρώματα μένουν ως
  // ένδειξη «προσοχή».

  // Find the target time slot from loaded data
  const targetSlot = timeSlots.find(ts =>
    ts.slotType === 'SEMESTER' &&
    ts.dayOfWeek === day &&
    normalizeTime(ts.startTime) === hour
  );
  if (!targetSlot) return;

  // Room: prefer original room, else find first available at target slot
  const usedRoomsAtTarget = new Set<number>(
    assignments
      .filter(a => a.id !== draggingAssignment.id &&
        a.timeSlot?.dayOfWeek === day &&
        normalizeTime(a.timeSlot?.startTime) === hour)
      .map(a => a.room?.id)
      .filter(Boolean) as number[]
  );
  let roomId = draggingAssignment.room?.id ?? 0;
  if (usedRoomsAtTarget.has(roomId)) {
    const available = rooms.find(r => !usedRoomsAtTarget.has(r.id));
    if (available) roomId = available.id;
  }

  setSaving(true);
  setWarnings([]);
  const moved = draggingAssignment;
  setDraggingAssignment(null);
  setDragOptions(null);
  setInstantHintMap(new Map());
  try {
    const result = await timetableService.moveAssignment(moved.id, {
      timeSlotId: targetSlot.id,
      roomId: roomId,
    });
    const usedRoom = rooms.find(r => r.id === roomId);
    const roomChanged = usedRoom && moved.room && usedRoom.id !== moved.room.id;
    const roomMsg = roomChanged
      ? ` (${usedRoom!.code} — η ${moved.room!.code} ήταν κατειλημμένη)`
      : usedRoom ? ` (${usedRoom.code})` : '';
    setMessage(`${moved.course?.name} μετακινήθηκε → ${day} ${hour}${roomMsg}`);
    setWarnings(result.warnings ?? []);
    await loadTimetableData(selectedTimetableId);
  } catch (err: any) {
    setError(getErrorMessage(err));
  } finally {
    setSaving(false);
  }
}

async function refreshEverything() {
  setAutoScheduleResult(null);
  await loadInitialData();

  if (selectedTimetableId) {
    await loadTimetableData(selectedTimetableId);
  }
}

  useEffect(() => {
    loadInitialData();
  }, []);

useEffect(() => {
    if (!selectedTimetableId) {
      setAssignments([]);
      setProgress(null);
      setValidation(null);
      setPlacementOptions(null);
      setAutoScheduleResult(null);
      return;
    }

    setPlacementOptions(null);
    setAutoScheduleResult(null);
    setMessage(null);
    setWarnings([]);
    loadTimetableData(selectedTimetableId);
  }, [selectedTimetableId]);

useEffect(() => {
  if (!error && !message) {
    return;
  }

  const timeout = window.setTimeout(() => {
    setError(null);
    setMessage(null);
  }, error ? 7000 : 4000);

  return () => window.clearTimeout(timeout);
}, [error, message]);

  useEffect(() => {
    if (!selectedTimetableId) return;

    if (eligibleCourses.length === 0) {
      setSelectedCourseId('');
      return;
    }

    const currentCourse = eligibleCourses.find((course) => course.id === selectedCourseId);

    if (!currentCourse) {
      const first = eligibleCourses[0];
      setSelectedCourseId(first.id);
      setSelectedAssignmentType(getFirstAvailableType(first, assignments));
      return;
    }

    const required = getRequiredHoursForType(currentCourse, selectedAssignmentType);
    const placed = getPlacedHoursForType(assignments, currentCourse.id, selectedAssignmentType);

    if (required <= 0 || placed >= required) {
      setSelectedAssignmentType(getFirstAvailableType(currentCourse, assignments));
    }
  }, [eligibleCourses, selectedCourseId, selectedAssignmentType, assignments, selectedTimetableId]);

function handleSelectTimetable(id: number) {
    setSelectedTimetableId(id);
    setPlacementOptions(null);
    setAutoScheduleResult(null);
    setError(null);
    setMessage(null);
  }


function handleTimetableCreated(newTimetable: any) {
  setTimetables((prev) => [...prev, newTimetable]);
  setSelectedTimetableId(newTimetable.id);
  setAutoScheduleResult(null);
  setPlacementOptions(null);
  setMessage('Το νέο πρόγραμμα δημιουργήθηκε επιτυχώς.');
}

function handleTimetableDeleted(id: number) {
  setTimetables((prev) => prev.filter((t) => t.id !== id));

  if (selectedTimetableId === id) {
    setSelectedTimetableId(null);
  }

  setAutoScheduleResult(null);
  setPlacementOptions(null);
  setError(null);
  setMessage('Το πρόγραμμα διαγράφηκε.');
}

  function getSlotId(day: string, hour: string) {
    const slot = timeSlots.find((timeSlot) =>
      timeSlot.dayOfWeek === day
      && normalizeTime(timeSlot.startTime) === hour
      && (!timeSlot.slotType || timeSlot.slotType === 'SEMESTER')
    );

    return slot?.id;
  }

  function getVisibleAssignmentsAt(day: string, hour: string) {
    const slotAssignments = assignmentsBySlot.get(assignmentKey(day, hour)) ?? [];

    if (yearFilter === 0) {
      return slotAssignments;
    }

    return slotAssignments.filter((assignment) => assignment.course?.studyYear === yearFilter);
  }

  function openAddModal(day: string, hour: string) {
    if (!selectedTimetableId) return;

    setSelectedDay(day);
    setSelectedHour(hour);
    setSelectedRoomId('');
    setCourseSearch('');
    setSelectedCourseId('');
    setPlacementOptions(null);
    setSelectedAssignmentType('LECTURE');
    setError(null);
    setMessage(null);
    setShowAddModal(true);
  }

  function handleSelectCourse(course: Course) {
    setSelectedCourseId(course.id);
    setCourseSearch(course.name);
    setPlacementOptions(null);

    const required = getRequiredHoursForType(course, selectedAssignmentType);
    const placed = getPlacedHoursForType(assignments, course.id, selectedAssignmentType);

    if (required <= 0 || placed >= required) {
      setSelectedAssignmentType(getFirstAvailableType(course, assignments));
    }
  }

  async function submitManualAssignment() {
    if (!selectedTimetableId) {
      setError('Δεν έχει επιλεγεί πρόγραμμα.');
      return;
    }

    if (!selectedCourseId || !selectedRoomId) {
      setError('Επίλεξε μάθημα και αίθουσα.');
      return;
    }

    const course = courses.find((c) => c.id === selectedCourseId);

    if (!course) {
      setError('Δεν βρέθηκε το επιλεγμένο μάθημα.');
      return;
    }

    // Non-blocking (Feature #2): αφαιρέθηκαν τα client pre-guards #5 (καμία ώρα του τύπου)
    // και #7 (υπέρβαση ωρών) — ο καθηγητής επιτρέπεται να τοποθετήσει· το backend τα
    // επιστρέφει ως advisory warnings αντί να μπλοκάρει.

    const slotId = getSlotId(selectedDay, selectedHour);

    if (!slotId) {
      setError('Δεν βρέθηκε χρονοθυρίδα για αυτό το κελί.');
      return;
    }

    setSaving(true);
    setError(null);
    setMessage(null);
    setWarnings([]);

    try {
      const result = await timetableService.addAssignment(selectedTimetableId, {
        courseId: selectedCourseId,
        roomId: selectedRoomId,
        timeSlotId: slotId,
        assignmentType: selectedAssignmentType,
      });

      setShowAddModal(false);
setPlacementOptions(null);
setAutoScheduleResult(null);
setMessage('Η ώρα προστέθηκε επιτυχώς.');
setWarnings(result.warnings ?? []);
await loadTimetableData(selectedTimetableId);
    } catch (err: any) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  async function loadPlacementOptions() {
    if (!selectedTimetableId) {
      setError('Επίλεξε πρώτα πρόγραμμα.');
      return;
    }

    if (!selectedCourseId) {
      setError('Διάλεξε πρώτα μάθημα.');
      return;
    }

    setLoadingOptions(true);
    setError(null);
    setMessage(null);

    try {
      const res = await timetableService.getPlacementOptions(
        selectedTimetableId,
        selectedCourseId,
        selectedAssignmentType
      );

      setPlacementOptions(res.data);
    } catch (err: any) {
      setPlacementOptions(null);
      setError(getErrorMessage(err));
    } finally {
      setLoadingOptions(false);
    }
  }

  async function addPlacement(option: PlacementOption) {
    if (!selectedTimetableId || !selectedCourseId || !option.allowed) return;

    setSaving(true);
    setError(null);
    setMessage(null);

    try {
      await timetableService.addAssignment(selectedTimetableId, {
        courseId: selectedCourseId,
        roomId: option.room.id,
        timeSlotId: option.timeSlot.id,
        assignmentType: selectedAssignmentType,
      });

      setMessage('Η προτεινόμενη ανάθεση προστέθηκε επιτυχώς.');
      setPlacementOptions(null);
      await loadTimetableData(selectedTimetableId);
    } catch (err: any) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  async function removeAssignment(assignmentId: number) {
    if (!selectedTimetableId) return;
    if (!confirm('Αφαίρεση μαθήματος από το πρόγραμμα;')) return;

    setSaving(true);
    setError(null);
    setMessage(null);

    try {
      await timetableService.removeAssignment(assignmentId);
setMessage('Η ανάθεση διαγράφηκε.');
setPlacementOptions(null);
setAutoScheduleResult(null);
await loadTimetableData(selectedTimetableId);
    } catch (err: any) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

async function runAutoSchedule() {
  if (!selectedTimetableId) return;

  if (!confirm(
    'Αυτόματη τοποθέτηση επόμενων ωρών;\n\n' +
    'Το σύστημα θα προσπαθήσει να τοποθετήσει αυτόματα τις επόμενες ώρες μαθημάτων με greedy στρατηγική, χρησιμοποιώντας τους υπάρχοντες κανόνες validation.\n\n' +
    'Τα ήδη τοποθετημένα μαθήματα δεν θα αλλάξουν.'
  )) {
    return;
  }

  setScheduling(true);
  setError(null);
  setMessage(null);

  try {
    const res = await timetableService.autoSchedule(selectedTimetableId);
    const data = res.data as AutoScheduleResult;

    setAutoScheduleResult(data);

    setMessage(
      `Αυτόματη τοποθέτηση ολοκληρώθηκε: ${data.totalPlaced} νέες ώρες τοποθετήθηκαν, ${data.totalFailed} δεν βρήκαν έγκυρη θέση.`
    );

    setPlacementOptions(null);
    await loadTimetableData(selectedTimetableId);
  } catch (err: any) {
    setError(getErrorMessage(err));
  } finally {
    setScheduling(false);
  }
}

async function runSolver() {
    if (!selectedTimetableId) return;
    if (!confirm('Εκτέλεση Timefold;\n\nΘα αντικαταστήσει τις αυτόματα τοποθετημένες ώρες.\nΟι χειροκίνητες τοποθετήσεις θα παραμείνουν.\nΔιάρκεια: ~5 λεπτά.')) return;

    setScheduling(true);
    setError(null);
    setMessage(null);

    try {
      const res = await timetableService.solve(selectedTimetableId, 300);
      const data = res.data as any;
      setMessage(
        `Timefold: ${data.totalPlaced} ώρες τοποθετήθηκαν | Hard: ${data.hardScore} | Soft: ${data.softScore} | ${(data.solveTimeMs / 1000).toFixed(1)}s`
      );
      setPlacementOptions(null);
      await loadTimetableData(selectedTimetableId);
    } catch (err: any) {
      setError(getErrorMessage(err));
    } finally {
      setScheduling(false);
    }
  }
  const allowedOptions = placementOptions?.options.filter((option) => option.allowed) ?? [];
  const blockedOptions = placementOptions?.options.filter((option) => !option.allowed) ?? [];

  if (loading) {
    return <div style={{ padding: '2rem' }}>Φόρτωση δεδομένων...</div>;
  }

  function printWeeklyTimetable(req: PrintRequest) {
    if (!selectedTimetable || assignments.length === 0) return;
    const tt = selectedTimetable;
    // Μαθήματα «σε συνεννόηση» δεν τυπώνονται στο επίσημο πρόγραμμα.
    const printable = assignments.filter(a => a.course?.visibleInTimetable !== false);
    const DAYS_ALL = [
      {key:'MONDAY',label:'Δευτέρα'},{key:'TUESDAY',label:'Τρίτη'},
      {key:'WEDNESDAY',label:'Τετάρτη'},{key:'THURSDAY',label:'Πέμπτη'},
      {key:'FRIDAY',label:'Παρασκευή'},
    ];
    // activeDays/activeHours από ΟΛΑ τα visible → ίδιες στήλες/γραμμές σε κάθε σελίδα-group.
    const activeDays = DAYS_ALL.filter(d => printable.some(a => a.timeSlot?.dayOfWeek === d.key));
    const activeHours = ALL_HOURS.filter(h =>
      activeDays.some(d => printable.some(a =>
        a.timeSlot?.dayOfWeek === d.key && a.timeSlot?.startTime?.startsWith(h.slice(0,2))
      ))
    );
    if (activeDays.length === 0 || activeHours.length === 0) return;
    // Συνεχόμενο εύρος ωρών (χωρίς κενά) → καθαρές γραμμές + σωστό rowspan (διαδοχικές γραμμές = διαδοχικές ώρες).
    const activeHourInts = activeHours.map(h => parseInt(h.slice(0, 2), 10));
    const minH = Math.min(...activeHourInts);
    const maxH = Math.max(...activeHourInts);
    const rowHours: number[] = [];
    for (let h = minH; h <= maxH; h++) rowHours.push(h);

    // Περιεχόμενο κελιού (aSc look): «κωδικός όνομα» + αίθουσα κάτω. Τιμά showType/showSemesterBadge.
    function cellContent(a: TimetableAssignment): string {
      const type = a.assignmentType as string;
      const tc = TYPE_COLORS[type] ?? TYPE_COLORS.LECTURE;
      const typeChip = req.showType ? ` <span style="color:${tc.border};font-weight:700;">${tc.label}</span>` : '';
      const semBadge = req.showSemesterBadge ? ` · Εξ.${esc(a.course.semester)}` : '';
      // Κωδικός (χωρίς CEID_, μία γραμμή nowrap) & όνομα (ήπιο wrap) σε ΞΕΧΩΡΙΣΤΕΣ γραμμές.
      return `<div style="font-size:8pt;line-height:1.2;overflow-wrap:break-word;">
          <div style="font-weight:700;white-space:nowrap;">${esc(shortCode(a.course.code))}${typeChip}</div>
          <div style="margin-top:1px;">${esc(a.course.name)}</div>
          <div style="font-size:6.5pt;color:#475569;margin-top:2px;">${esc(a.room?.code ?? '')}${semBadge}</div>
        </div>`;
    }

    // Signature μαθήματος για merge διαδοχικών ωρών (μόνο όταν το κελί έχει ΑΚΡΙΒΩΣ 1 μάθημα).
    const cellOf = (items: TimetableAssignment[], dayKey: string, hourInt: number) =>
      items.filter(a => a.timeSlot?.dayOfWeek === dayKey && parseInt(a.timeSlot?.startTime ?? '', 10) === hourInt);
    const sigOf = (cellItems: TimetableAssignment[]): string | null => {
      if (cellItems.length !== 1) return null;
      const a = cellItems[0];
      return `${a.course.id}|${a.assignmentType}|${a.room?.id ?? ''}`;
    };

    const tdBase = 'border:1px solid #cbd5e1;padding:4px 6px;vertical-align:top;';

    // Grid με rowspan: πολύωρο μάθημα = ΕΝΑ ψηλό κελί (merge διαδοχικών ίδιων ωρών).
    function buildWeeklyGrid(items: TimetableAssignment[]): string {
      const coveredByDay = new Map<string, Set<number>>();
      activeDays.forEach(d => coveredByDay.set(d.key, new Set<number>()));

      const rows = rowHours.map(hourInt => {
        const tds = activeDays.map(d => {
          const covered = coveredByDay.get(d.key)!;
          if (covered.has(hourInt)) return ''; // καλυμμένο από rowspan από πάνω → χωρίς <td>
          const cellItems = cellOf(items, d.key, hourInt);
          const sig = sigOf(cellItems);
          if (sig != null) {
            // Run length: πόσες διαδοχικές επόμενες ώρες έχουν ίδιο sig.
            let run = 1;
            for (let nh = hourInt + 1; nh <= maxH; nh++) {
              if (covered.has(nh) || sigOf(cellOf(items, d.key, nh)) !== sig) break;
              covered.add(nh);
              run++;
            }
            const a = cellItems[0];
            const stripe = req.colorByYear ? `border-left:3px solid ${yearColor(a.course.studyYear)};` : '';
            const rs = run > 1 ? ` rowspan="${run}"` : '';
            return `<td${rs} style="${tdBase}${stripe}">${cellContent(a)}</td>`;
          }
          if (cellItems.length === 0) return `<td style="${tdBase}"></td>`;
          // Πολλαπλά μαθήματα στο ίδιο slot → stacked, rowspan 1 (πυκνή σελίδα Επιλογής).
          const stacked = cellItems.map(a => `<div style="margin-bottom:3px;">${cellContent(a)}</div>`).join('');
          return `<td style="${tdBase}">${stacked}</td>`;
        }).join('');
        return `<tr><td style="${tdBase}background:#f8fafc;font-size:7.5pt;color:#475569;white-space:nowrap;text-align:center;">${hourInt}-${hourInt + 1}</td>${tds}</tr>`;
      }).join('');

      const headTh = 'border:1px solid #cbd5e1;background:#f1f5f9;color:#0f172a;font-weight:600;padding:5px 6px;';
      const header = `<tr>
        <th style="${headTh}font-size:7.5pt;width:48px;">Ώρα</th>
        ${activeDays.map(d => `<th style="${headTh}font-size:9pt;">${d.label}</th>`).join('')}
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
    if (groups.length === 0) return;

    // Μία οντότητα ανά σελίδα (aSc-style: κεντρικός τίτλος, καθαρό grid, footer).
    const bodyHtml = groups.map((g, idx) => {
      const years = req.colorByYear ? Array.from(new Set(g.items.map(a => a.course.studyYear))).sort((x, y) => x - y) : [];
      const legend = years.length
        ? `<div style="display:flex;gap:12px;justify-content:center;font-size:7pt;color:#64748b;margin-top:6px;">${years.map(y => `<span style="display:inline-flex;align-items:center;gap:3px;"><span style="display:inline-block;width:8px;height:8px;border-radius:2px;background:${yearColor(y)};"></span>${y}ο Έτος</span>`).join('')}</div>`
        : '';
      return `
      <div style="${idx < groups.length - 1 ? 'page-break-after:always;' : ''}">
        <div style="text-align:center;font-size:18pt;font-weight:400;margin:4px 0 2px;">${esc(g.title)}</div>
        <div style="text-align:center;font-size:8pt;color:#64748b;margin-bottom:8px;">Ωρολόγιο Πρόγραμμα — ${esc(tt.name)} · ΤΜΗΥΠ Πανεπιστήμιο Πατρών</div>
        ${buildWeeklyGrid(g.items)}${legend}
        <div style="font-size:7pt;color:#94a3b8;margin-top:6px;">Δημιουργία Προγράμματος: ${todayGreek()}</div>
      </div>`;
    }).join('');

    const html = buildPrintDocument({
      title: `Ωρολόγιο — ${tt.name}`,
      headerHtml: '',
      bodyHtml,
    });
    openAndPrint(html);
  }

  return (
    <div style={{ padding: '40px 48px', background: '#080f1a', minHeight: 'calc(100vh - 52px)', fontFamily: "'IBM Plex Sans', sans-serif"     }}>
    <style>{`
      @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;400;500;600&family=JetBrains+Mono:wght@400;500&display=swap');
      @media print {
        @page { size: A4 landscape; margin: 8mm; }
        .ceid-nav  { display: none !important; }
        .no-print  { display: none !important; }
        aside      { display: none !important; }
        .wt-main-grid { grid-template-columns: 1fr !important; }
        body { -webkit-print-color-adjust: exact; print-color-adjust: exact; background: white !important; }
      }
    `}</style>
{error && (
  <div style={toastErrorStyle}>
    <strong>Σφάλμα</strong>
    <div style={{ marginTop: '0.35rem' }}>{error}</div>
    <button
  onClick={() => setError(null)}
  style={toastCloseButtonStyle}
  title="Κλείσιμο"
>
  ×
</button>
  </div>
)}

{message && (
  <div style={toastSuccessStyle}>
    <strong>Επιτυχία</strong>
    <div style={{ marginTop: '0.35rem' }}>{message}</div>
    <button
  onClick={() => setMessage(null)}
  style={toastCloseButtonStyle}
  title="Κλείσιμο"
>
  ×
</button>
  </div>
)}

{warnings.length > 0 && (
  <div style={toastWarningStyle}>
    <strong>⚠ Προσοχή</strong>
    <div style={{ marginTop: '0.35rem' }}>
      {warnings.map((w, i) => <div key={i}>{w}</div>)}
    </div>
    <button
      onClick={() => setWarnings([])}
      style={toastCloseButtonStyle}
      title="Κλείσιμο"
    >
      ×
    </button>
  </div>
)}
      <div className="no-print" style={{ display: 'flex', justifyContent: 'space-between', gap: '1rem', alignItems: 'flex-start', marginBottom: '1.5rem' }}>
        <div>
          <h1 style={{ fontSize: '1.6rem', marginBottom: '0.35rem' }}>Ωρολόγιο Πρόγραμμα</h1>
          <p style={{ color: '#94a3b8' }}>
            Επιλογή προγράμματος, manual προσθήκη σε κενό κελί, validation και προτεινόμενες τοποθετήσεις από το backend.
          </p>
        </div>

       <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>

  {selectedTimetableId && (
    <button
      onClick={runAutoSchedule}
      disabled={saving || loadingTimetable || scheduling}
      style={{
        padding: '0.65rem 1rem',
        border: 'none',
        borderRadius: '8px',
        background: scheduling ? '#4b5563' : '#f59e0b',
        color: '#000',
        fontWeight: 700,
        cursor: scheduling ? 'wait' : 'pointer',
      }}
    >
      {scheduling ? 'Τοποθέτηση...' : 'Αυτόματη Τοποθέτηση'}
    </button>
  )}

{selectedTimetableId && (
            <button
              onClick={runSolver}
              disabled={saving || loadingTimetable || scheduling}
              style={{
                padding: '0.65rem 1rem',
                border: 'none',
                borderRadius: '8px',
                background: scheduling ? '#4b5563' : '#8b5cf6',
                color: '#fff',
                fontWeight: 700,
                cursor: scheduling ? 'wait' : 'pointer',
              }}
            >
              {scheduling ? 'Solver...' : 'Timefold'}
            </button>
          )}

<button
          onClick={() => setPrintOpen(true)}
          disabled={!selectedTimetableId || assignments.length === 0}
          style={{ padding: '6px 14px', border: '1px solid #0f766e', borderRadius: '7px', background: 'transparent', color: '#0f766e', fontSize: '12px', fontWeight: 600, cursor: 'pointer', fontFamily: "'IBM Plex Sans', sans-serif" }}
        >🖨 Εκτύπωση</button>

  <button
    onClick={refreshEverything}
    disabled={saving || loadingTimetable}
    style={{
      padding: '0.65rem 1rem',
      border: 'none',
      borderRadius: '8px',
      background: '#334155',
      color: '#fff',
      cursor: 'pointer',
    }}
  >
    Ανανέωση
  </button>
</div>
      </div>


<div className="no-print">
        <TimetableSelector
          timetables={timetables}
          selectedTimetableId={selectedTimetableId}
          onSelect={handleSelectTimetable}
          onCreated={handleTimetableCreated}
          onDeleted={handleTimetableDeleted}
          disabled={saving || loadingTimetable}
          progress={progress}
        />
      </div>

      {selectedTimetableId && (
        <>
          <div className="no-print" style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', flexWrap: 'wrap', alignItems: 'center' }}>
            <span style={{ color: '#94a3b8' }}>Φίλτρο έτους:</span>
            {[0, 1, 2, 3, 4, 5].map((year) => (
              <button
                key={year}
                onClick={() => {
                  setYearFilter(year);
                  setPlacementOptions(null);
                }}
                style={{
                  padding: '0.4rem 0.8rem',
                  borderRadius: '8px',
                  border: 'none',
                  cursor: 'pointer',
                  background: yearFilter === year ? '#10b981' : '#334155',
                  color: '#fff',
                  fontSize: '0.85rem',
                }}
              >
                {year === 0 ? 'Όλα' : `${year}ο Έτος`}
              </button>
            ))}

            {selectedTimetable && (
              <span style={{ color: '#64748b', marginLeft: '0.5rem' }}>
                {selectedTimetable.semesterType === 'FALL' ? 'Χειμερινό' : 'Εαρινό'} πρόγραμμα
              </span>
            )}
          </div>

          <div className="no-print" style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
            <StatCard title="Τοποθετημένες ώρες" value={assignments.length} />
            <StatCard title="Πρόοδος" value={`${progress?.percentage ?? 0}%`} />
            <StatCard title="Errors" value={validation?.errorCount ?? 0} danger={(validation?.errorCount ?? 0) > 0} onClick={() => setIssuesModal('ERROR')} />
            <StatCard title="Warnings" value={validation?.warningCount ?? 0} warning={(validation?.warningCount ?? 0) > 0} onClick={() => setIssuesModal('WARNING')} />
          </div>

          <div className="wt-main-grid" style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '1.5rem', alignItems: 'start' }}>
            <section style={{ background: '#111827', border: '1px solid #1e293b', borderRadius: '12px', overflow: 'hidden' }}>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', minWidth: '720px', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr>
                      <th style={headerCellStyle}>Ώρα</th>
                      {DAYS.map((day) => (
                        <th key={day.key} style={headerCellStyle}>{day.label}</th>
                      ))}
                    </tr>
                  </thead>

                  <tbody>
                    {HOURS.map((hour) => (
                      <tr key={hour}>
                        <td style={{ ...cellStyle, width: '64px', color: '#cbd5e1', fontWeight: 700 }}>
                          {hour}<br />
                          <span style={{ fontWeight: 400, color: '#64748b' }}>{hourEnd(hour)}</span>
                        </td>

                        {DAYS.map((day) => {
                          const slotAssignments = getVisibleAssignmentsAt(day.key, hour);

                          return (
<td
  key={day.key}
  onClick={() => openAddModal(day.key, hour)}
  style={{
    ...cellStyle,
    cursor: 'pointer',
    background: (() => {
      const hint = activeHintMap.get(assignmentKey(day.key, hour));
      if (hint === 'blocked')   return '#1c0a0a';
      if (hint === 'preferred') return '#14532d';
      if (hint === 'allowed')   return '#052e16';
      return slotAssignments.length === 0 ? '#0b1120' : '#111827';
    })(),
    borderLeft: (() => {
      const hint = activeHintMap.get(assignmentKey(day.key, hour));
      if (hint === 'blocked')   return '3px solid #7f1d1d';
      if (hint === 'preferred') return '3px solid #4ade80';
      if (hint === 'allowed')   return '3px solid #22c55e';
      return cellStyle.borderRight;
    })(),
    transition: 'background 0.2s ease',
  }}
  title={(() => {
    const hint = activeHintMap.get(assignmentKey(day.key, hour));
    if (hint === 'blocked')   return 'Πιθανή σύγκρουση — επιτρέπεται η τοποθέτηση';
    if (hint === 'preferred') return '★ Προτιμώμενη ώρα καθηγητή';
    if (hint === 'allowed')   return 'Επιτρεπτή τοποθέτηση';
    return 'Κλικ για προσθήκη μαθήματος';
  })()}
  onDragOver={(e) => { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; }}
  onDrop={(e) => { e.preventDefault(); handleDrop(day.key, hour); }}
>

                              {slotAssignments.length === 0 ? (
                                <span style={{ color: '#334155', fontSize: '0.8rem' }}>+ Προσθήκη</span>
                              ) : (
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.25rem', alignItems: 'flex-start' }}>
                                  {slotAssignments.map((assignment) => (
                                    <AssignmentCard
  					key={assignment.id}
  					assignment={assignment}
  					onShowDetails={() => setDetailsAssignment(assignment)}
  					onDragStart={() => handleDragStart(assignment)}
  					onDragEnd={handleDragEnd}
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
            </section>

            <aside style={{ display: 'flex', flexDirection: 'row', flexWrap: 'wrap', gap: '1rem', alignItems: 'flex-start' }}>
              <section style={panelStyle}>
                <h2 style={panelTitleStyle}>Προσθήκη ώρας με προτάσεις</h2>

                <label style={labelStyle}>Μάθημα</label>
                <select
                  value={selectedCourseId}
                  onChange={(event) => {
                    const id = Number(event.target.value);
                    const course = courses.find((c) => c.id === id);

                    setSelectedCourseId(id);
                    setPlacementOptions(null);

                    if (course) {
                      setSelectedAssignmentType(getFirstAvailableType(course, assignments));
                    }
                  }}
                  style={inputStyle}
                >
                  {eligibleCourses.map((course) => (
                    <option key={course.id} value={course.id}>
                      {course.semester}ο εξ. - {course.code} - {course.name}
                    </option>
                  ))}
                </select>

                <label style={labelStyle}>Τύπος ώρας</label>
                <select
                  value={selectedAssignmentType}
                  onChange={(event) => {
                    setSelectedAssignmentType(event.target.value as AssignmentTypeKey);
                    setPlacementOptions(null);
                  }}
                  style={inputStyle}
                >
                  <option value="LECTURE">Θεωρία</option>
                  <option value="TUTORIAL">Φροντιστήριο</option>
                  <option value="LAB">Εργαστήριο</option>
                </select>

                {selectedCourse && (
                  <CourseHoursBox
                    course={selectedCourse}
                    assignments={assignments}
                  />
                )}

                {selectedCourse && !hasHoursForType(selectedCourse, selectedAssignmentType) && (
                  <div style={{ color: '#fbbf24', fontSize: '0.85rem', marginBottom: '0.75rem' }}>
                    Προσοχή: το επιλεγμένο μάθημα δεν έχει προβλεπόμενες ώρες για αυτόν τον τύπο.
                  </div>
                )}

                <button
                  onClick={loadPlacementOptions}
                  disabled={loadingOptions || saving || !selectedCourseId}
                  style={{ ...primaryButtonStyle, width: '100%' }}
                >
                  {loadingOptions ? 'Υπολογισμός...' : 'Βρες προτεινόμενες θέσεις'}
                </button>
              </section>

              {placementOptions && (
                <section style={panelStyle}>
                  <h2 style={panelTitleStyle}>Προτάσεις</h2>

                  <div style={{ color: '#94a3b8', fontSize: '0.9rem', marginBottom: '0.75rem' }}>
                    Επιτρεπτές: <b style={{ color: '#22c55e' }}>{placementOptions.allowedOptions}</b> / {placementOptions.totalOptions}
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.65rem', maxHeight: '430px', overflowY: 'auto', paddingRight: '0.25rem' }}>
                    {allowedOptions.slice(0, 12).map((option) => (
                      <PlacementOptionCard
                        key={`${option.room.id}-${option.timeSlot.id}`}
                        option={option}
                        onAdd={() => addPlacement(option)}
                        disabled={saving}
                      />
                    ))}
                  </div>

                  {allowedOptions.length === 0 && (
                    <div style={{ color: '#f87171', fontSize: '0.9rem' }}>
                      Δεν βρέθηκαν επιτρεπτές τοποθετήσεις. Δες τα blocked reasons.
                    </div>
                  )}
                </section>
              )}

              {placementOptions && blockedOptions.length > 0 && (
                <section style={panelStyle}>
                  <h2 style={panelTitleStyle}>Πρώτα blocked παραδείγματα</h2>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    {blockedOptions.slice(0, 5).map((option) => (
                      <div key={`${option.room.id}-${option.timeSlot.id}`} style={{ background: '#1f2937', borderRadius: '8px', padding: '0.7rem' }}>
                        <div style={{ color: '#fca5a5', fontWeight: 700, fontSize: '0.85rem' }}>
                          {dayLabel(option.timeSlot.dayOfWeek)} {normalizeTime(option.timeSlot.startTime)} - {option.room.code}
                        </div>

                        <div style={{ color: '#cbd5e1', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                          {option.reasons[0]}
                        </div>
                      </div>
                    ))}
                  </div>
                </section>
              )}

              {validation && validation.errors.length > 0 && (
                <section style={panelStyle}>
                  <h2 style={panelTitleStyle}>Πρώτα σφάλματα</h2>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.45rem', maxHeight: '260px', overflowY: 'auto' }}>
                    {validation.errors.slice(0, 8).map((error, index) => (
                      <div key={`${error.referenceId}-${error.code}-${index}`} style={{ color: '#f87171', fontSize: '0.82rem', lineHeight: 1.35 }}>
                        {error.message}
                      </div>
                    ))}
                  </div>
                </section>
              )}

              {validation && validation.warnings.length > 0 && (
                <section style={panelStyle}>
                  <h2 style={panelTitleStyle}>Πρώτα προειδοποιήσεις</h2>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.45rem', maxHeight: '260px', overflowY: 'auto' }}>
                    {validation.warnings.slice(0, 8).map((warning, index) => (
                      <div key={`${warning.referenceId}-${warning.code}-${index}`} style={{ color: '#fbbf24', fontSize: '0.82rem', lineHeight: 1.35 }}>
                        {warning.message}
                      </div>
                    ))}
                  </div>
                </section>
              )}

              {autoScheduleResult && (
                <AutoScheduleResultPanel
                  result={autoScheduleResult}
                  validation={validation}
                  onClear={() => setAutoScheduleResult(null)}
                />
              )}

            </aside>
          </div>
        </>
      )}


      {showAddModal && (
        <div
          style={modalOverlayStyle}
          onClick={() => setShowAddModal(false)}
        >
          <div
            style={modalStyle}
            onClick={(event) => event.stopPropagation()}
          >
            <h2 style={{ marginBottom: '0.35rem', fontSize: '1.2rem' }}>
              Προσθήκη μαθήματος σε κενό κελί
            </h2>

            <p style={{ color: '#94a3b8', marginBottom: '1rem', fontSize: '0.9rem' }}>
              {DAYS.find((day) => day.key === selectedDay)?.label} {selectedHour} - {hourEnd(selectedHour)}
            </p>

            <label style={labelStyle}>Μάθημα</label>
            <input
              value={courseSearch}
              onChange={(event) => setCourseSearch(event.target.value)}
              placeholder="Αναζήτηση μαθήματος..."
              style={inputStyle}
            />

            <div style={{
              maxHeight: '210px',
              overflowY: 'auto',
              background: '#0f172a',
              borderRadius: '8px',
              marginBottom: '1rem',
              border: '1px solid #334155',
            }}>
              {modalFilteredCourses.slice(0, 30).map((course) => (
                <div
                  key={course.id}
                  onClick={() => handleSelectCourse(course)}
                  style={{
                    padding: '0.6rem 0.75rem',
                    cursor: 'pointer',
                    background: selectedCourseId === course.id ? '#3b82f622' : 'transparent',
                    borderLeft: selectedCourseId === course.id ? '3px solid #3b82f6' : '3px solid transparent',
                    borderBottom: '1px solid #1e293b',
                  }}
                >
                  <div style={{ fontSize: '0.85rem', fontWeight: 700 }}>
                    {course.name}
                  </div>

                  <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                    {course.code} · Εξ.{course.semester} · {course.sector} · {course.teachersText}
                  </div>
                </div>
              ))}

              {modalFilteredCourses.length === 0 && (
                <div style={{ padding: '1rem', color: '#64748b', textAlign: 'center' }}>
                  Δεν βρέθηκαν μαθήματα.
                </div>
              )}
            </div>

            {selectedCourse && (
              <CourseHoursBox
                course={selectedCourse}
                assignments={assignments}
              />
            )}

            <label style={labelStyle}>Τύπος ώρας</label>
            <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
              {ASSIGNMENT_TYPES.map((type) => {
                const required = selectedCourse ? getRequiredHoursForType(selectedCourse, type) : 0;
                const placed = selectedCourse ? getPlacedHoursForType(assignments, selectedCourse.id, type) : 0;
                const disabled = !selectedCourse || required === 0 || placed >= required;

                return (
                  <button
                    key={type}
                    disabled={disabled}
                    onClick={() => !disabled && setSelectedAssignmentType(type)}
                    style={{
                      padding: '0.45rem 0.8rem',
                      borderRadius: '8px',
                      border: 'none',
                      cursor: disabled ? 'not-allowed' : 'pointer',
                      background: selectedAssignmentType === type ? '#2563eb' : '#334155',
                      color: '#fff',
                      opacity: disabled ? 0.45 : 1,
                    }}
                  >
                    {assignmentTypeLabels[type]} {selectedCourse ? `(${placed}/${required})` : ''}
                  </button>
                );
              })}
            </div>

            <label style={labelStyle}>Αίθουσα</label>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '0.5rem', marginBottom: '1.2rem' }}>
              {rooms
                .filter((room) => room.availableForSemester !== false)
                .map((room) => (
                  <button
                    key={room.id}
                    onClick={() => setSelectedRoomId(room.id)}
                    style={{
                      padding: '0.6rem',
                      borderRadius: '8px',
                      cursor: 'pointer',
                      textAlign: 'center',
                      background: selectedRoomId === room.id ? '#3b82f633' : '#0f172a',
                      border: selectedRoomId === room.id ? '2px solid #3b82f6' : '2px solid #334155',
                      color: '#fff',
                    }}
                  >
                    <div style={{ fontWeight: 800, fontSize: '1rem' }}>{room.code}</div>
                    <div style={{ fontSize: '0.7rem', color: '#94a3b8' }}>Χωρ: {room.capacity}</div>
                  </button>
                ))}
            </div>

            <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
              <button
                onClick={() => setShowAddModal(false)}
                style={secondaryButtonStyle}
              >
                Ακύρωση
              </button>

              <button
                onClick={submitManualAssignment}
                disabled={saving || !selectedCourseId || !selectedRoomId}
                style={{
                  ...successButtonStyle,
                  opacity: selectedCourseId && selectedRoomId ? 1 : 0.55,
                }}
              >
                {saving ? 'Αποθήκευση...' : 'Προσθήκη'}
              </button>
            </div>
          </div>
        </div>
      )}
{movingAssignment && (
        <MoveAssignmentModal
          assignment={movingAssignment}
          rooms={rooms}
          timeSlots={timeSlots}
	onMoved={() => {
  	setPlacementOptions(null);
	setAutoScheduleResult(null);

  	if (selectedTimetableId) {
    	loadTimetableData(selectedTimetableId);
  	}
	}}
          onClose={() => setMovingAssignment(null)}
          onError={(msg) => setError(msg)}
          onSuccess={(msg) => setMessage(msg)}
          onWarnings={(w) => setWarnings(w)}
        />
      )}

      <AssignmentDetailsModal
        assignment={detailsAssignment}
        onClose={() => setDetailsAssignment(null)}
        onMove={() => { if (detailsAssignment) setMovingAssignment(detailsAssignment); setDetailsAssignment(null); }}
        onDelete={() => { if (detailsAssignment) removeAssignment(detailsAssignment.id); setDetailsAssignment(null); }}
        disabled={saving}
      />

      <ValidationIssuesModal
        severity={issuesModal}
        issues={
          issuesModal === 'ERROR'   ? (validation?.errors   ?? [])
        : issuesModal === 'WARNING' ? (validation?.warnings ?? [])
        : []
        }
        onClose={() => setIssuesModal(null)}
        getLocation={resolveLocation}
      />

      <PrintOptionsModal
        open={printOpen}
        onClose={() => setPrintOpen(false)}
        showTypeToggle={true}
        available={printAvailable}
        onPrint={(req) => printWeeklyTimetable(req)}
      />
    </div>
  );
}

function CourseHoursBox({
  course,
  assignments,
}: {
  course: Course;
  assignments: TimetableAssignment[];
}) {
  return (
    <div style={{
      background: '#1e293b',
      border: '1px solid #334155',
      padding: '0.75rem',
      borderRadius: '8px',
      marginBottom: '1rem',
      fontSize: '0.85rem',
    }}>
      <strong>{course.name}</strong>

      <div style={{ color: '#94a3b8', fontSize: '0.8rem', marginTop: '0.25rem' }}>
        Σύνολο ωρών: {course.lectureHours}Θ / {course.tutorialHours}Φ / {course.labHours}Ε · Φοιτητές: ~{course.expectedStudents}
      </div>

      <div style={{ marginTop: '0.75rem', display: 'grid', gap: '0.5rem' }}>
        {ASSIGNMENT_TYPES.map((type) => {
          const required = getRequiredHoursForType(course, type);
          const placed = getPlacedHoursForType(assignments, course.id, type);
          const complete = required > 0 && placed >= required;
          const unavailable = required === 0;
          const percent = required > 0 ? Math.min(100, (placed / required) * 100) : 0;

          return (
            <div key={type}>
              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                marginBottom: '0.2rem',
                color: unavailable ? '#64748b' : complete ? '#10b981' : '#e2e8f0',
              }}>
                <span>{assignmentTypeLabels[type]}</span>
                <span>
                  {placed}/{required}
                  {complete && required > 0 ? ' ✓' : ''}
                  {unavailable ? ' — δεν προβλέπεται' : ''}
                </span>
              </div>

              <div style={{
                height: '6px',
                background: '#0f172a',
                borderRadius: '999px',
                overflow: 'hidden',
              }}>
                <div style={{
                  height: '100%',
                  width: `${percent}%`,
                  background: unavailable ? '#475569' : complete ? '#10b981' : '#3b82f6',
                  transition: 'width 0.2s ease',
                }} />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function StatCard({
  title,
  value,
  danger,
  warning,
  onClick,
}: {
  title: string;
  value: number | string;
  danger?: boolean;
  warning?: boolean;
  onClick?: () => void;
}) {
  const color = danger ? '#ef4444' : warning ? '#f59e0b' : '#3b82f6';

  return (
    <div
      onClick={onClick}
      title={onClick ? 'Κλικ για λεπτομέρειες' : undefined}
      style={{
        padding: '1rem',
        background: '#1e293b',
        borderRadius: '12px',
        borderLeft: `4px solid ${color}`,
        cursor: onClick ? 'pointer' : 'default',
        transition: 'filter 0.15s',
      }}
    >
      <div style={{ fontSize: '1.45rem', fontWeight: 800, color }}>{value}</div>
      <div style={{ color: '#94a3b8', fontSize: '0.9rem' }}>{title}</div>
    </div>
  );
}

function AssignmentCard({
  assignment,
  onShowDetails,
  onDragStart,
  onDragEnd,
  disabled,
}: {
  assignment: TimetableAssignment;
  onShowDetails: () => void;
  onDragStart: () => void;
  onDragEnd: () => void;
  disabled: boolean;
}) {
  const typeColor = assignmentTypeColors[assignment.assignmentType] ?? '#3b82f6';
  const shortCode = (assignment.course?.code ?? '').replace(/^CEID_/, '');

  return (
    <div
      onClick={(event) => { event.stopPropagation(); onShowDetails(); }}
      draggable={true}
      onDragStart={(e) => { e.stopPropagation(); onDragStart(); }}
      onDragEnd={(e) => { e.stopPropagation(); onDragEnd(); }}
      title={assignment.course?.code}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '0.2rem',
        background: '#1e293b',
        borderLeft: `3px solid ${typeColor}`,
        borderRadius: '4px',
        padding: '0.1rem 0.3rem',
        color: '#e2e8f0',
        fontSize: '0.68rem',
        fontWeight: 600,
        cursor: 'grab',
        whiteSpace: 'nowrap',
        flex: '0 0 auto',
        maxWidth: '100%',
        opacity: disabled ? 0.6 : 1,
      }}
    >
      {shortCode}
      {assignment.course?.visibleInTimetable === false && (
        <span title="Σε συνεννόηση — δεν εμφανίζεται στο δημόσιο πρόγραμμα"
              style={{ fontSize: '0.62rem' }}>🤝</span>
      )}
    </div>
  );
}

function PlacementOptionCard({
  option,
  onAdd,
  disabled,
}: {
  option: PlacementOption;
  onAdd: () => void;
  disabled: boolean;
}) {
  return (
    <div style={{ background: '#1f2937', border: '1px solid #334155', borderRadius: '10px', padding: '0.8rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: '0.5rem', marginBottom: '0.35rem' }}>
        <div>
          <div style={{ fontWeight: 800 }}>
            {dayLabel(option.timeSlot.dayOfWeek)} {normalizeTime(option.timeSlot.startTime)}-{normalizeTime(option.timeSlot.endTime)}
          </div>

          <div style={{ color: '#94a3b8', fontSize: '0.85rem' }}>
            {option.room.name} ({option.room.code})
          </div>
        </div>

        <div style={{ color: '#22c55e', fontWeight: 900 }}>{option.score}</div>
      </div>

      <ul style={{ margin: '0.5rem 0 0.75rem 1rem', color: '#cbd5e1', fontSize: '0.78rem', lineHeight: 1.35 }}>
        {option.reasons.slice(0, 3).map((reason, index) => (
          <li key={index}>{reason}</li>
        ))}
      </ul>

      <button
        onClick={onAdd}
        disabled={disabled}
        style={{ ...primaryButtonStyle, width: '100%', padding: '0.5rem 0.8rem' }}
      >
        Πρόσθεσε αυτή την ώρα
      </button>
    </div>
  );
}

function AutoScheduleResultPanel({
  result,
  validation,
  onClear,
}: {
  result: any;
  validation: TimetableValidationReport | null;
  onClear: () => void;
}) {
  const log: string[] = Array.isArray(result?.log) ? result.log : [];

const placedEntries: any[] = Array.isArray(result?.placedEntries) ? result.placedEntries : [];
const failedEntries: any[] = Array.isArray(result?.failedEntries) ? result.failedEntries : [];

const successLines: string[] =
  placedEntries.length > 0
    ? placedEntries.map((entry: any) =>
        `${entry.courseCode} ${entry.assignmentType} -> ${entry.day} ${entry.startTime} ${entry.roomCode} (score: ${entry.score})`
      )
    : log.filter((line: string) => !line.startsWith('FAIL') && !line.startsWith('ΑΠΟΤΥΧΙΑ'));

const failureLines: string[] =
  failedEntries.length > 0
    ? failedEntries.map((entry: any) =>
        `FAIL: ${entry.courseCode} ${entry.assignmentType} - ${entry.mainReason}`
      )
    : log.filter((line: string) => line.startsWith('FAIL') || line.startsWith('ΑΠΟΤΥΧΙΑ'));

const placed = result?.totalPlaced ?? 0;
const failed = result?.totalFailed ?? 0;
const skipped = result?.totalSkipped ?? 0;

const errorCount = validation?.errorCount ?? 0;
const warningCount = validation?.warningCount ?? 0;

  return (
    <section style={panelStyle}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: '0.75rem', alignItems: 'center', marginBottom: '0.85rem' }}>
        <h2 style={{ ...panelTitleStyle, marginBottom: 0 }}>
          Τελευταία αυτόματη τοποθέτηση
        </h2>

        <button
          onClick={onClear}
          style={{
            border: 'none',
            borderRadius: '6px',
            background: '#334155',
            color: '#e2e8f0',
            cursor: 'pointer',
            padding: '0.25rem 0.5rem',
            fontSize: '0.75rem',
          }}
        >
          Καθαρισμός
        </button>
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(3, 1fr)',
        gap: '0.5rem',
        marginBottom: '0.8rem',
      }}>
        <div style={{ background: '#064e3b', borderRadius: '8px', padding: '0.55rem' }}>
          <div style={{ fontWeight: 900, color: '#34d399' }}>{placed}</div>
          <div style={{ fontSize: '0.72rem', color: '#d1fae5' }}>Νέες ώρες</div>
        </div>

        <div style={{ background: failed > 0 ? '#7f1d1d' : '#1e293b', borderRadius: '8px', padding: '0.55rem' }}>
          <div style={{ fontWeight: 900, color: failed > 0 ? '#fca5a5' : '#94a3b8' }}>{failed}</div>
          <div style={{ fontSize: '0.72rem', color: '#cbd5e1' }}>Αποτυχίες</div>
        </div>

        <div style={{ background: '#1e293b', borderRadius: '8px', padding: '0.55rem' }}>
          <div style={{ fontWeight: 900, color: '#93c5fd' }}>{skipped}</div>
          <div style={{ fontSize: '0.72rem', color: '#cbd5e1' }}>Ήδη καλυμμένες</div>
        </div>
      </div>

      <div style={{
        background: errorCount > 0 ? '#7f1d1d' : '#0f172a',
        border: errorCount > 0 ? '1px solid #ef4444' : '1px solid #334155',
        borderRadius: '8px',
        padding: '0.6rem',
        marginBottom: '0.85rem',
        fontSize: '0.8rem',
      }}>
        <strong style={{ color: errorCount > 0 ? '#fecaca' : '#bfdbfe' }}>
          Backend validation μετά την εκτέλεση:
        </strong>

        <div style={{ marginTop: '0.25rem', color: '#cbd5e1' }}>
          Errors: <b style={{ color: errorCount > 0 ? '#f87171' : '#22c55e' }}>{errorCount}</b>
          {' '}· Warnings: <b style={{ color: warningCount > 0 ? '#fbbf24' : '#22c55e' }}>{warningCount}</b>
        </div>
      </div>

      <div style={{ marginBottom: '0.75rem' }}>
        <div style={{ color: '#22c55e', fontWeight: 800, fontSize: '0.82rem', marginBottom: '0.35rem' }}>
          Επιτυχείς τοποθετήσεις
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem', maxHeight: '150px', overflowY: 'auto' }}>
          {successLines.slice(0, 10).map((line: string, index: number) => (
            <div key={`success-${index}`} style={{ color: '#bbf7d0', fontSize: '0.76rem', lineHeight: 1.35 }}>
              {line}
            </div>
          ))}

          {successLines.length === 0 && (
            <div style={{ color: '#94a3b8', fontSize: '0.76rem' }}>
              Δεν καταγράφηκαν νέες επιτυχείς τοποθετήσεις.
            </div>
          )}
        </div>
      </div>

      <div>
        <div style={{ color: failed > 0 ? '#f87171' : '#22c55e', fontWeight: 800, fontSize: '0.82rem', marginBottom: '0.35rem' }}>
          Αποτυχίες / μη εφικτές τοποθετήσεις
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem', maxHeight: '150px', overflowY: 'auto' }}>
          {failureLines.slice(0, 10).map((line: string, index: number) => (
            <div key={`failure-${index}`} style={{ color: '#fecaca', fontSize: '0.76rem', lineHeight: 1.35 }}>
              {line}
            </div>
          ))}

          {failureLines.length === 0 && (
            <div style={{ color: '#bbf7d0', fontSize: '0.76rem' }}>
              Δεν καταγράφηκαν αποτυχίες στην τελευταία εκτέλεση.
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

const headerCellStyle: CSSProperties = {
  padding: '0.4rem',
  background: '#1e293b',
  color: '#e2e8f0',
  borderBottom: '1px solid #334155',
  textAlign: 'left',
};

const cellStyle: CSSProperties = {
  padding: '0.25rem',
  verticalAlign: 'top',
  borderBottom: '1px solid #1e293b',
  borderRight: '1px solid #1e293b',
  minHeight: '28px',
};

const panelStyle: CSSProperties = {
  background: '#111827',
  border: '1px solid #1e293b',
  borderRadius: '12px',
  padding: '1rem',
  flex: '1 1 320px',
};

const panelTitleStyle: CSSProperties = {
  fontSize: '1.05rem',
  marginBottom: '0.9rem',
};

const labelStyle: CSSProperties = {
  display: 'block',
  color: '#94a3b8',
  fontSize: '0.85rem',
  marginBottom: '0.35rem',
};

const inputStyle: CSSProperties = {
  width: '100%',
  padding: '0.65rem',
  borderRadius: '8px',
  border: '1px solid #334155',
  background: '#0f172a',
  color: '#e2e8f0',
  marginBottom: '0.8rem',
  boxSizing: 'border-box',
};

const primaryButtonStyle: CSSProperties = {
  padding: '0.65rem 1rem',
  border: 'none',
  borderRadius: '8px',
  background: '#2563eb',
  color: '#fff',
  fontWeight: 700,
  cursor: 'pointer',
};

const successButtonStyle: CSSProperties = {
  padding: '0.65rem 1rem',
  border: 'none',
  borderRadius: '8px',
  background: '#10b981',
  color: '#fff',
  fontWeight: 700,
  cursor: 'pointer',
};

const secondaryButtonStyle: CSSProperties = {
  padding: '0.65rem 1rem',
  border: 'none',
  borderRadius: '8px',
  background: '#334155',
  color: '#fff',
  fontWeight: 700,
  cursor: 'pointer',
};

const modalOverlayStyle: CSSProperties = {
  position: 'fixed',
  top: 0,
  left: 0,
  right: 0,
  bottom: 0,
  background: 'rgba(0,0,0,0.72)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  zIndex: 1000,
};

const modalStyle: CSSProperties = {
  background: '#111827',
  border: '1px solid #334155',
  borderRadius: '14px',
  padding: '1.5rem',
  width: '560px',
  maxHeight: '86vh',
  overflowY: 'auto',
};

const toastBaseStyle: CSSProperties = {
  position: 'fixed',
  top: '1.25rem',
  right: '1.25rem',
  width: '360px',
  maxWidth: 'calc(100vw - 2rem)',
  padding: '1rem 2.5rem 1rem 1rem',
  borderRadius: '12px',
  color: '#fff',
  zIndex: 3000,
  boxShadow: '0 20px 35px rgba(0,0,0,0.35)',
  lineHeight: 1.35,
};

const toastErrorStyle: CSSProperties = {
  ...toastBaseStyle,
  background: '#7f1d1d',
  border: '1px solid #ef4444',
};

const toastSuccessStyle: CSSProperties = {
  ...toastBaseStyle,
  background: '#064e3b',
  border: '1px solid #10b981',
};

// Non-blocking advisory (Feature #2): amber — διακριτό από το κόκκινο error.
const toastWarningStyle: CSSProperties = {
  ...toastBaseStyle,
  background: '#78350f',
  border: '1px solid #f59e0b',
};

const toastCloseButtonStyle: CSSProperties = {
  position: 'absolute',
  top: '0.45rem',
  right: '0.6rem',
  border: 'none',
  background: 'transparent',
  color: '#fff',
  fontSize: '1.2rem',
  cursor: 'pointer',
};