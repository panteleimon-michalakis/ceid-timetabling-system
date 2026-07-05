// TASK 3 — Ένδειξη «τοποθετημένο στην προτιμώμενη ώρα/αίθουσα εξέτασης».
// Καθαρά ΕΜΦΑΝΙΣΗ: συγκρίνει την πραγματική αίθουσα/ώρα μιας τοποθετημένης εξέτασης
// με τα preferredExamRooms / preferredExamHours (CSV) του μαθήματος. Ίδιο parsing με
// τον backend (SolverService.parseCsvCodes/parseCsvHours): split σε , ή ; και trim.
//
// ΘΕΤΙΚΗ-ΜΟΝΟ ένδειξη: επιστρέφει true ΜΟΝΟ όταν υπάρχει δηλωμένη προτίμηση ΚΑΙ
// ικανοποιείται πλήρως (όποιες από room/hour έχουν οριστεί). Καμία αρνητική ένδειξη
// για αποκλίσεις — δεν «τιμωρείται» οπτικά η μη-τήρηση.

function parseCsv(value: string | null | undefined): string[] {
  return (value ?? '')
    .split(/[,;]/)
    .map(part => part.trim())
    .filter(part => part.length > 0);
}

/** Ώρα έναρξης (ακέραιη) από "09:00:00" → 9· από "" / null → NaN. */
function startHourOf(startTime: string | null | undefined): number {
  if (!startTime) return NaN;
  return parseInt(startTime.split(':')[0], 10);
}

/**
 * true αν η τοποθέτηση ταιριάζει με ΟΛΕΣ τις δηλωμένες προτιμήσεις εξέτασης του μαθήματος.
 * Αν δεν υπάρχει καμία δηλωμένη προτίμηση (κενά και τα δύο) → false (δεν δείχνουμε τίποτα).
 */
export function matchesExamPreference(
  preferredExamRooms: string | null | undefined,
  preferredExamHours: string | null | undefined,
  roomCode: string | null | undefined,
  startTime: string | null | undefined,
): boolean {
  const rooms = parseCsv(preferredExamRooms);
  const hours = parseCsv(preferredExamHours)
    .map(h => parseInt(h, 10))
    .filter(n => !Number.isNaN(n));

  if (rooms.length === 0 && hours.length === 0) return false; // καμία προτίμηση δηλωμένη

  const roomOk = rooms.length === 0 || (roomCode != null && rooms.includes(roomCode));

  const startHour = startHourOf(startTime);
  const hourOk = hours.length === 0 || (!Number.isNaN(startHour) && hours.includes(startHour));

  return roomOk && hourOk;
}
