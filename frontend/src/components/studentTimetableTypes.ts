// Shared types για το StudentTimetableView + useStudentTimetableData hook.
// Μετακινήθηκαν verbatim από το StudentView.tsx (no behavior change).

export interface Timetable {
  id: number; name: string; timetableType: string; status?: string;
  semesterType?: string; startDate?: string; endDate?: string;
}

export interface Assignment {
  id: number;
  assignmentType: 'LECTURE' | 'TUTORIAL' | 'LAB' | 'EXAM';
  examDurationMinutes?: number | null;
  course: {
    id: number; code: string; name: string;
    semester: number; studyYear: number; courseType: string;
    sector?: string; teachersText?: string;
    visibleInTimetable?: boolean;
  };
  room: { id: number; code: string; capacity: number; roomType: string };
  timeSlot: {
    id: number; dayOfWeek?: string | null;
    startTime: string; endTime: string;
    slotType: string;
    specificDate?: string | null;
  };
}
