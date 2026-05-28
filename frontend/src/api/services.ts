import api from './client';
import type {
  Room,
  Course,
  TimeSlot,
  Timetable,
  TimetableAssignment,
  TimetableProgress,
  TimetableValidationReport,
  PlacementOptionsResponse,
  SolverResult,
  GenerateExamSlotsRequest,
  GenerateExamSlotsResult,
} from '../types';

export const roomService = {
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
  // Backend endpoint: GET /api/timeslots/type/{SEMESTER|EXAM}
  getSemesterSlots: () => api.get<TimeSlot[]>('/timeslots/type/SEMESTER'),
  getExamSlots: () => api.get<TimeSlot[]>('/timeslots/type/EXAM'),
};

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
  }) => api.post<Timetable>('/timetables', data),
  update: (id: number, data: Partial<Timetable>) =>
    api.put<Timetable>(`/timetables/${id}`, data),
  delete: (id: number) => api.delete(`/timetables/${id}`),

  getAssignments: (id: number) =>
    api.get<TimetableAssignment[]>(`/timetables/${id}/assignments`),

  addAssignment: (id: number, data: {
    courseId: number;
    roomId: number;
    timeSlotId: number;
    assignmentType: string;
  }) => api.post<TimetableAssignment>(`/timetables/${id}/assignments`, data),

  removeAssignment: (assignmentId: number) =>
    api.delete(`/timetables/assignments/${assignmentId}`),

  moveAssignment: (assignmentId: number, data: { roomId?: number; timeSlotId?: number }) =>
    api.put<TimetableAssignment>(`/timetables/assignments/${assignmentId}/move`, data),

  getProgress: (id: number) =>
    api.get<TimetableProgress>(`/timetables/${id}/progress`),

  validate: (id: number) =>
    api.get<TimetableValidationReport>(`/timetables/${id}/validation`),

  getPlacementOptions: (id: number, courseId: number, assignmentType: string) =>
    api.get<PlacementOptionsResponse>(
      `/timetables/${id}/placement-options`,
      { params: { courseId, assignmentType } }
    ),

  autoSchedule: (id: number) =>
    api.post(`/timetables/${id}/auto-schedule`),

  solve: (id: number, timeLimit = 30) =>
    api.post<SolverResult>(`/timetables/${id}/solve`, null, {
      params: { timeLimit },
      timeout: 180_000,
    }),

  generateExamSlots: (id: number) =>
    api.get(`/timetables/${id}/generate-exam-slots`),

  generateExamSlotsLegacy: (data: GenerateExamSlotsRequest) =>
    api.post<GenerateExamSlotsResult>('/timetables/generate-exam-slots', data),
};

export const healthService = {
  check: () => api.get('/health'),
};