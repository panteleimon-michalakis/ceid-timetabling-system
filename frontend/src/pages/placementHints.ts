import type { PlacementOptionsResponse } from '../types';

/** Feature #3: 3-state hint ανά slot (πράσινο/amber/κόκκινο). */
export type SlotHint = 'allowed' | 'warning' | 'blocked';

// Ταυτόσημο με το normalizeTime/assignmentKey του WeeklyTimetable ώστε τα keys
// του map να ταιριάζουν με το rendering lookup (activeHintMap.get(assignmentKey(...))).
function normalizeTime(value?: string | null): string {
  if (!value) return '';
  return value.length >= 5 ? value.slice(0, 5) : value;
}

export function slotHintKey(dayOfWeek: string, startTime: string): string {
  return `${dayOfWeek}-${normalizeTime(startTime)}`;
}

/**
 * Feature #3: χτίζει 3-state hint map ανά slot από τα placement options (ένα
 * option ανά αίθουσα). Priority ανά slot: 'allowed' (υπάρχει ≥1 καθαρή αίθουσα) >
 * 'warning' (δομικά ΟΚ αλλά με advisory issue) > 'blocked' (καμία αίθουσα δεν
 * περνά τους δομικούς ελέγχους). Ταιριάζει με το drop gate: κόκκινο = θα
 * μπλοκάρει· amber = θα περάσει με προειδοποίηση· πράσινο = καθαρό.
 *
 * Σε ξεχωριστό (μη-component) module: pure/testable + αποφεύγει το
 * react-refresh/only-export-components του WeeklyTimetable.tsx.
 */
export function buildSlotHintMap(resp: PlacementOptionsResponse | null): Map<string, SlotHint> {
  const map = new Map<string, SlotHint>();
  if (!resp) return map;
  for (const option of resp.options) {
    if (!option.timeSlot?.dayOfWeek || !option.timeSlot?.startTime) continue;
    const key = slotHintKey(option.timeSlot.dayOfWeek, option.timeSlot.startTime);
    const state: SlotHint = !option.allowed ? 'blocked' : option.warning ? 'warning' : 'allowed';
    const cur = map.get(key);
    if (state === 'allowed') map.set(key, 'allowed');
    else if (state === 'warning') { if (cur !== 'allowed') map.set(key, 'warning'); }
    else if (cur === undefined) map.set(key, 'blocked');
  }
  return map;
}
