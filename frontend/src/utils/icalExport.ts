/**
 * iCal export utility για CEID Timetable
 * RFC 5545 compliant — συγχωνεύει συνεχόμενα slots σε ένα event
 */

import type { TimetableAssignment, Timetable } from '../types';

const DAY_NUM: Record<string, number> = {
  MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5,
};

const TYPE_LABEL: Record<string, string> = {
  LECTURE: 'Θεωρία', TUTORIAL: 'Φροντιστήριο', LAB: 'Εργαστήριο',
};

/** Πρώτη εμφάνιση ημέρας εβδομάδας από δεδομένη ημερομηνία */
function firstWeekday(from: Date, weekday: number): Date {
  const d = new Date(from);
  const diff = (weekday - d.getDay() + 7) % 7;
  d.setDate(d.getDate() + diff);
  return d;
}

/** iCal floating datetime (χωρίς timezone) */
function fmtDT(date: Date, time: string): string {
  const [h, m] = time.split(':').map(Number);
  const y  = date.getFullYear();
  const mo = String(date.getMonth() + 1).padStart(2, '0');
  const d  = String(date.getDate()).padStart(2, '0');
  return `${y}${mo}${d}T${String(h).padStart(2,'0')}${String(m).padStart(2,'0')}00`;
}

/** Ημερομηνίες εξαμήνου */
function semesterDates(t: Timetable): { start: Date; end: Date } {
  const baseYear = parseInt((t.academicYear ?? '2025-26').split('-')[0]);
  if (t.semesterType === 'FALL')
    return { start: new Date(baseYear, 9, 1), end: new Date(baseYear + 1, 0, 31) };
  return { start: new Date(baseYear + 1, 1, 1), end: new Date(baseYear + 1, 5, 30) };
}

interface MergedBlock {
  assignmentId: number;
  courseName: string; courseCode: string; semester: number;
  assignmentType: string;
  dayOfWeek: string;
  startTime: string; endTime: string;
  roomCode: string;  roomName: string;
}

/**
 * Συγχωνεύει συνεχόμενα 1-ωρα slots του ίδιου μαθήματος/τύπου/ημέρας
 * π.χ. CEID_23Y106 LECTURE MONDAY 09:00-10:00 + 10:00-11:00 + 11:00-12:00
 *   → CEID_23Y106 LECTURE MONDAY 09:00-12:00 (ΕΝΑ event)
 */
function mergeSlots(assignments: TimetableAssignment[]): MergedBlock[] {
  // Ομαδοποίηση ανά (course, type, day)
  const groups = new Map<string, TimetableAssignment[]>();
  for (const a of assignments) {
    if (!a.timeSlot?.dayOfWeek || !a.timeSlot?.startTime) continue;
    const key = `${a.course.id}__${a.assignmentType}__${a.timeSlot.dayOfWeek}`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(a);
  }

  const blocks: MergedBlock[] = [];

  for (const slots of groups.values()) {
    // Ταξινόμηση κατά ώρα έναρξης
    const sorted = [...slots].sort((a, b) =>
      (a.timeSlot.startTime ?? '').localeCompare(b.timeSlot.startTime ?? '')
    );

    let start = sorted[0].timeSlot.startTime!.slice(0, 5);
    let end   = sorted[0].timeSlot.endTime!.slice(0, 5);

    for (let i = 1; i < sorted.length; i++) {
      const nextStart = sorted[i].timeSlot.startTime!.slice(0, 5);
      if (nextStart === end) {
        // Συνεχόμενο slot — επέκτεινε
        end = sorted[i].timeSlot.endTime!.slice(0, 5);
      } else {
        // Κενό — αποθήκευσε και ξεκίνα νέο
        blocks.push(makeBlock(sorted[0], start, end));
        start = nextStart;
        end   = sorted[i].timeSlot.endTime!.slice(0, 5);
      }
    }
    blocks.push(makeBlock(sorted[0], start, end));
  }

  return blocks;
}

function makeBlock(a: TimetableAssignment, startTime: string, endTime: string): MergedBlock {
  return {
    assignmentId:   a.id,
    courseName:     a.course?.name   ?? '',
    courseCode:     a.course?.code   ?? '',
    semester:       a.course?.semester ?? 0,
    assignmentType: a.assignmentType,
    dayOfWeek:      a.timeSlot.dayOfWeek!,
    startTime, endTime,
    roomCode: a.room?.code ?? '',
    roomName: a.room?.name ?? a.room?.code ?? '',
  };
}

/** Δημιουργεί .ics string */
export function generateIcal(
  assignments: TimetableAssignment[],
  timetable: Timetable,
  calName = 'CEID Πρόγραμμα',
): string {
  const { start, end } = semesterDates(timetable);
  const until = `${end.getFullYear()}${String(end.getMonth()+1).padStart(2,'0')}${String(end.getDate()).padStart(2,'0')}T235959`;

  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//CEID ΤΜΗΥΠ Πανεπιστήμιο Πατρών//Ωρολόγιο Πρόγραμμα//EL',
    `X-WR-CALNAME:${calName}`,
    'X-WR-CALDESC:Εξαχθέν από το Ωρολόγιο Πρόγραμμα ΤΜΗΥΠ',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
  ];

  const blocks = mergeSlots(assignments);

  for (const b of blocks) {
    const dayNum = DAY_NUM[b.dayOfWeek];
    if (dayNum === undefined) continue;

    const firstOcc = firstWeekday(start, dayNum);
    if (firstOcc > end) continue;

    const dtStart  = fmtDT(firstOcc, b.startTime);
    const dtEnd    = fmtDT(firstOcc, b.endTime);
    const typeStr  = TYPE_LABEL[b.assignmentType] ?? b.assignmentType;

    lines.push(
      'BEGIN:VEVENT',
      `UID:ceid-${b.assignmentId}-${timetable.id}-merged@upatras.gr`,
      `DTSTAMP:${fmtDT(new Date(), new Date().toTimeString().slice(0, 5))}`,
      `DTSTART:${dtStart}`,
      `DTEND:${dtEnd}`,
      `RRULE:FREQ=WEEKLY;UNTIL=${until}`,
      `SUMMARY:${b.courseName} — ${typeStr}`,
      `LOCATION:${b.roomName}`,
      `DESCRIPTION:${typeStr} · ${b.courseCode} · ${b.semester}ο Εξάμηνο\\nΑίθουσα: ${b.roomCode}\\nΠρόγραμμα: ${timetable.name}`,
      'STATUS:CONFIRMED',
      'TRANSP:OPAQUE',
      'END:VEVENT',
    );
  }

  lines.push('END:VCALENDAR');
  return lines.join('\r\n');
}

/** Κατεβάζει .ics αρχείο */
export function downloadIcal(filename: string, content: string): void {
  const blob = new Blob([content], { type: 'text/calendar;charset=utf-8' });
  const url  = URL.createObjectURL(blob);
  const a    = document.createElement('a');
  a.href = url; a.download = filename;
  document.body.appendChild(a); a.click();
  document.body.removeChild(a); URL.revokeObjectURL(url);
}

/** iCal εξεταστικού — specific dates, όχι recurring εβδομαδιαία events */
export function generateExamIcal(
  assignments: Array<{
    id: number;
    course: { code: string; name: string; semester: number };
    room: { code: string; name?: string };
    timeSlot: { specificDate?: string | null; startTime: string };
    examDurationMinutes?: number | null;
  }>,
  calName = 'CEID Εξεταστική',
): string {
  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//CEID ΤΜΗΥΠ Πανεπιστήμιο Πατρών//Εξεταστικό Πρόγραμμα//EL',
    `X-WR-CALNAME:${calName}`,
    'X-WR-CALDESC:Εξεταστικό Πρόγραμμα ΤΜΗΥΠ — Πανεπιστήμιο Πατρών',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
  ];

  for (const a of assignments) {
    if (!a.timeSlot?.specificDate || !a.timeSlot?.startTime) continue;
    const dateStr     = a.timeSlot.specificDate.replace(/-/g, '');
    const [sH, sM]    = a.timeSlot.startTime.split(':').map(Number);
    const durationMin = a.examDurationMinutes ?? 180;
    const endMin      = sH * 60 + (sM ?? 0) + durationMin;
    const dtStart = `${dateStr}T${String(sH).padStart(2,'0')}${String(sM??0).padStart(2,'0')}00`;
    const dtEnd   = `${dateStr}T${String(Math.floor(endMin/60)).padStart(2,'0')}${String(endMin%60).padStart(2,'0')}00`;
    lines.push(
      'BEGIN:VEVENT',
      `UID:ceid-exam-${a.id}@upatras.gr`,
      `DTSTAMP:${new Date().toISOString().replace(/[-:.T]/g,'').slice(0,15)}`,
      `DTSTART:${dtStart}`,
      `DTEND:${dtEnd}`,
      `SUMMARY:Εξέταση — ${a.course?.name ?? ''}`,
      `LOCATION:${a.room?.name ?? a.room?.code ?? ''}`,
      `DESCRIPTION:${a.course?.code ?? ''} · ${a.course?.semester ?? ''}ο Εξάμηνο\\nΑίθουσα: ${a.room?.code ?? ''}\\nΔιάρκεια: ${durationMin/60}h`,
      'STATUS:CONFIRMED',
      'TRANSP:OPAQUE',
      'END:VEVENT',
    );
  }
  lines.push('END:VCALENDAR');
  return lines.join('\r\n');
}