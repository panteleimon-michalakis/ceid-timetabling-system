// ============================================================
// CEID Timetable — TypeScript Types
// ============================================================

// --- Enums / Union Types ---

export type TimetableType = 'SEMESTER' | 'EXAM';
export type SemesterType = 'FALL' | 'SPRING' | 'SEPTEMBER';
export type AssignmentType = 'LECTURE' | 'TUTORIAL' | 'LAB' | 'EXAM';
export type SlotType = 'SEMESTER' | 'EXAM';
export type CourseType = 'REQUIRED' | 'REQUIRED_ELECTIVE' | 'EXTERNAL' | 'GENERAL_EDUCATION';
export type RoomType = 'AMPHITHEATER' | 'CLASSROOM' | 'LAB' | 'EXAM_HALL' | 'MEETING_ROOM';
export type TimetableStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

// --- Core Entities ---

export interface Room {
  id: number;
  name: string;
  code: string;
  capacity: number;
  roomType: RoomType | string;
  hasProjector?: boolean;
  hasComputers?: boolean;
  availableForExams?: boolean;
  availableForSemester?: boolean;
  notes?: string | null;
}

export interface Course {
  id: number;
  code: string;
  name: string;
  semester: number;
  studyYear: number;
  courseType: CourseType | string;
  lectureHours: number;
  tutorialHours: number;
  labHours: number;
  ects: number;
  sector: string;
  expectedStudents: number;
  semesterType: SemesterType | string;
  teachersText: string;
  active?: boolean;
  visibleInTimetable?: boolean;
  examDurationMinutes?: number | null;
  notes?: string | null;
}

export interface TimeSlot {
  id: number;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  slotType: SlotType | string;
  // Exam-specific fields
  specificDate?: string | null;     // π.χ. "2026-01-19"
  examPeriodLabel?: string | null;  // π.χ. "Χειμερινή Εξεταστική 2026"
}

export interface Timetable {
  id: number;
  name: string;
  academicYear: string;
  timetableType: TimetableType;
  semesterType: SemesterType | string | null;
  startDate?: string | null;
  endDate?: string | null;
  status: TimetableStatus | string;
  solverScore?: string | null;
  solverConflicts?: number | null;
  solverTimeSeconds?: number | null;
  createdBy?: string | null;
  publishedAt?: string | null;
  createdAt?: string;
  notes?: string | null;
}

export interface TimetableAssignment {
  id: number;
  timetable?: Timetable;
  course: Course;
  room: Room;
  timeSlot: TimeSlot;
  assignmentType: AssignmentType | string;
  isLocked?: boolean;
  manuallyAssigned?: boolean;
  examDurationMinutes?: number | null;
}

// --- Progress ---

export interface MissingCourseProgress {
  courseId: number;
  code: string;
  name: string;
  semester: number;
  studyYear: number;
  courseType: CourseType | string | null;
  requiredLecture: number;
  placedLecture: number;
  requiredTutorial: number;
  placedTutorial: number;
  requiredLab: number;
  placedLab: number;
  requiredTotal: number;
  placedTotal: number;
  missingHours: number;
}

export interface TimetableProgress {
  timetableId: number;
  timetableName: string;
  timetableType?: TimetableType;
  semesterType: SemesterType | string | null;
  totalCourses: number;
  completedCourses: number;
  totalRequiredHours: number;
  placedHours: number;
  percentage: number;
  missingCourses: MissingCourseProgress[];
}

// --- Validation ---

export interface ValidationIssue {
  severity: 'ERROR' | 'WARNING' | string;
  code: string;
  message: string;
  referenceId: number | null;
}

export interface TimetableValidationReport {
  valid: boolean;
  errorCount: number;
  warningCount: number;
  errors: ValidationIssue[];
  warnings: ValidationIssue[];
}

// --- Placement Options ---

export interface PlacementOption {
  allowed: boolean;
  score: number;
  room: Room;
  timeSlot: TimeSlot;
  status: 'ALLOWED' | 'BLOCKED' | string;
  reasons: string[];
}

export interface PlacementOptionsResponse {
  timetableId: number;
  course: Course;
  assignmentType: AssignmentType | string;
  totalOptions: number;
  allowedOptions: number;
  blockedOptions: number;
  options: PlacementOption[];
}

// --- Solver Result ---

export interface SolverResult {
  status: string;
  totalLessons: number;
  totalPlaced: number;
  hardScore: number;
  softScore: number;
  solveTimeMs: number;
  deletedPrevious: number;
  log?: string[];
}

// --- Exam Slot Generation ---

export interface GenerateExamSlotsRequest {
  startDate: string;   // "2026-01-19"
  endDate: string;     // "2026-02-07"
  label: string;       // "Χειμερινή Εξεταστική 2025-26"
}

export interface GenerateExamSlotsResult {
  totalCreated: number;
  totalExisting: number;
  totalProcessed: number;
  startDate: string;
  endDate: string;
  label: string;
  slots?: TimeSlot[];
}