import api from './client';
import type {
  Room,
  Course,
  Teacher,
  TimeSlot,
  Timetable,
  TimetableAssignment,
  TimetableProgress,
  TimetableValidationReport,
  PlacementOptionsResponse,
  SolverResult,
  GenerateExamSlotsRequest,
  GenerateExamSlotsResult,
  CourseTeacherRef,
  TeacherCourseRef,
  CourseTeacherAssignment,
  TeacherCourseAssignment,
} from '../types';

/** Δεσμευμένη ώρα αίθουσας (room_constraints). */
export interface RoomConstraintDto {
  id?: number;
  dayOfWeek: string;
  hour: number;
  constraintType: 'BLOCKED';
}

export const roomService = {
  getConstraints: (id: number) =>
    api.get<RoomConstraintDto[]>(`/rooms/${id}/constraints`),

  updateConstraints: (id: number, body: RoomConstraintDto[]) =>
    api.put(`/rooms/${id}/constraints`, body),
  getAll: () => api.get<Room[]>('/rooms'),
  getById: (id: number) => api.get<Room>(`/rooms/${id}`),
  create: (room: Partial<Room>) => api.post<Room>('/rooms', room),
  update: (id: number, room: Partial<Room>) => api.put<Room>(`/rooms/${id}`, room),
  delete: (id: number) => api.delete(`/rooms/${id}`),
};

export const courseService = {
  getAll: () => api.get<Course[]>('/courses'),
  getById: (id: number) => api.get<Course>(`/courses/${id}`),
  getBySemester: (sem: number) => api.get<Course[]>(`/courses/semester/${sem}`),
  getByType: (type: string) => api.get<Course[]>(`/courses/type/${type}`),
  getActive: () => api.get<Course[]>('/courses/active'),
  create: (course: Partial<Course>) => api.post<Course>('/courses', course),
  update: (id: number, course: Partial<Course>) => api.put<Course>(`/courses/${id}`, course),
  delete: (id: number) => api.delete(`/courses/${id}`),
};

export const timeSlotService = {
  getAll: () => api.get<TimeSlot[]>('/timeslots'),
  getSemesterSlots: () => api.get<TimeSlot[]>('/timeslots/type/SEMESTER'),
  getExamSlots: () => api.get<TimeSlot[]>('/timeslots/type/EXAM'),
};

/**
 * Απάντηση του greedy auto-scheduler.
 * Έχει διαφορετικό σχήμα από το SolverResult του Timefold.
 */
export interface AutoScheduleSummary {
  timetableId: number;
  totalPlaced: number;
  totalFailed: number;
  totalSkipped: number;
  totalCourses: number;
  log: string[];
}

/**
 * Response των add/move μετά το non-blocking backend (Feature #2): το assignment DTO
 * + advisory `warnings` (0-ή-1 εγγραφή τώρα· το Feature #3 θα το γεμίσει με τον πλήρη
 * κατηγοριοποιημένο πίνακα). Κενό array = καθαρή τοποθέτηση.
 */
export interface AssignmentMutationResult extends TimetableAssignment {
  warnings?: string[];
}

export const timetableService = {
  getAll: () => api.get<Timetable[]>('/timetables'),
  getById: (id: number) => api.get<Timetable>(`/timetables/${id}`),
  create: (data: {
    name: string;
    academicYear: string;
    timetableType: string;
    semesterType?: string | null;
    startDate?: string | null;
    endDate?: string | null;
    notes?: string | null;
    excludedDates?: string | null; // CSV YYYY-MM-DD — custom εξαιρούμενες ημερομηνίες
  }) => api.post<Timetable>('/timetables', data),
  update: (id: number, data: Partial<Timetable>) =>
    api.put<Timetable>(`/timetables/${id}`, data),
  delete: (id: number) => api.delete(`/timetables/${id}`),

  // ── Publish workflow ─────────────────────────────────────────────────
  publish:   (id: number) => api.put<Timetable>(`/timetables/${id}/publish`),
  unpublish: (id: number) => api.put<Timetable>(`/timetables/${id}/unpublish`),

  // ── Assignments ──────────────────────────────────────────────────────
  getAssignments: (id: number) =>
    api.get<TimetableAssignment[]>(`/timetables/${id}/assignments`),

  addAssignment: (id: number, data: {
    courseId: number;
    roomId: number;
    timeSlotId: number;
    assignmentType: string;
    examDurationMinutes?: number;
    isLocked?: boolean;
  }): Promise<AssignmentMutationResult> =>
    api.post<AssignmentMutationResult>(`/timetables/${id}/assignments`, data).then(r => r.data),

  removeAssignment: (assignmentId: number) =>
    api.delete(`/timetables/assignments/${assignmentId}`),

  /** Μαζικό καθάρισμα όλων των αναθέσεων ενός προγράμματος (ADMIN). */
  clearAssignments: (id: number) =>
    api.delete(`/timetables/${id}/assignments`),

  /** Επιστρέφει υπάρχον EXAM slot για (ημερομηνία, ώρα) ή το δημιουργεί (ADMIN/TEACHER). */
  findOrCreateExamSlot: (date: string, startHour: number) =>
    api.post<{ id: number; dayOfWeek: string; startTime: string; endTime: string; slotType: string; specificDate: string }>(
      `/timetables/exam-slots/find-or-create`, { date, startHour }),

  moveAssignment: (assignmentId: number, data: { roomId?: number; timeSlotId?: number }): Promise<AssignmentMutationResult> =>
    api.put<AssignmentMutationResult>(`/timetables/assignments/${assignmentId}/move`, data).then(r => r.data),

  getProgress: (id: number) =>
    api.get<TimetableProgress>(`/timetables/${id}/progress`),

  validate: (id: number) =>
    api.get<TimetableValidationReport>(`/timetables/${id}/validation`),

  getPlacementOptions: (id: number, courseId: number, assignmentType: string) =>
    api.get<PlacementOptionsResponse>(
      `/timetables/${id}/placement-options?courseId=${courseId}&assignmentType=${assignmentType}`
    ),

  autoSchedule: (id: number) =>
    api.post<AutoScheduleSummary>(`/timetables/${id}/auto-schedule`),

  solve: (id: number, timeLimitSeconds: number = 30) =>
    api.post<SolverResult>(`/timetables/${id}/solve?timeLimit=${timeLimitSeconds}`),

  generateExamSlots: (data: GenerateExamSlotsRequest) =>
    api.post<GenerateExamSlotsResult>('/timetables/generate-exam-slots', data),

  generateExamSlotsForTimetable: (id: number) =>
    api.post(`/timetables/${id}/generate-exam-slots`, {}),
};

export const healthService = {
  check: () => api.get('/health'),
};

// ── Course ↔ Teacher M2M (Φ2b) ──────────────────────────────────────────

/** Λίστα όλων των teachers — μόνο ό,τι χρειάζεται ο picker. */
export const teacherService = {
  getAll: () => api.get<Teacher[]>('/teachers').then(r => r.data),
};

/** Διδάσκοντες ανά μάθημα (course-side picker). */
export const courseTeacherService = {
  getForCourse: (courseId: number) =>
    api.get<CourseTeacherRef[]>(`/courses/${courseId}/teachers`).then(r => r.data),
  setForCourse: (courseId: number, body: CourseTeacherAssignment[]) =>
    api.put<CourseTeacherRef[]>(`/courses/${courseId}/teachers`, body).then(r => r.data),
};

/** Μαθήματα ανά διδάσκοντα (teacher-side picker, reverse sync). */
export const teacherCourseService = {
  getForTeacher: (teacherId: number) =>
    api.get<TeacherCourseRef[]>(`/teachers/${teacherId}/courses`).then(r => r.data),
  setForTeacher: (teacherId: number, body: TeacherCourseAssignment[]) =>
    api.put<TeacherCourseRef[]>(`/teachers/${teacherId}/courses`, body).then(r => r.data),
};