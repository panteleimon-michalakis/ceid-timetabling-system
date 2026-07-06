/**
 * TASK 2.4 — formal regression test για το Task 1 no-op guard. Καθαρή, testable εξαγωγή
 * της σύγκρισης "το target slot είναι το ίδιο με το origin slot του dragged assignment"
 * που ήδη τρέχει (ζωντανά επιβεβαιωμένη) μέσα στο handleDrop/handleExamDrop. Καμία αλλαγή
 * συμπεριφοράς — μόνο εξαγωγή, ώστε να υπάρχει μόνιμο automated regression test.
 */
export function isDropOnOriginSlot(
  origin: { dayOfWeek?: string | null; specificDate?: string | null; startTime?: string | null } | null | undefined,
  targetKey: string,
  targetHour: string,
  normalize: (v?: string | null) => string,
): boolean {
  if (!origin) return false;
  const originKey = origin.specificDate ?? origin.dayOfWeek;
  return originKey === targetKey && normalize(origin.startTime) === targetHour;
}
