package gr.upatras.ceid.timetable.solver;

import java.util.Map;
import java.util.Set;

/**
 * Δεσμευμένες ώρες αιθουσών για τον solver.
 * Φορτώνεται από το SolverService (DB: room_constraints) πριν την επίλυση.
 * Μορφή: roomCode -> set of "DAY_HOUR" (π.χ. "Δ1" -> {"WEDNESDAY_12", ...})
 */
public class RoomAvailabilityConstraints {

    public static volatile Map<String, Set<String>> BLOCKED_SLOTS = Map.of();

    /** Εβδομαδιαία slots (1 ώρα): έλεγχος της ώρας έναρξης. */
    public static boolean isBlockedWeekly(Lesson l) {
        SolverRoom r = l.getRoom();
        SolverTimeSlot t = l.getTimeSlot();
        if (r == null || t == null) return false;
        return isBlocked(r.getCode(), t.getDayOfWeek(), t.getStartHour());
    }

    /** Exam slots (3ωρα): μπλοκάρεται αν ΟΠΟΙΑΔΗΠΟΤΕ ώρα του παραθύρου είναι δεσμευμένη. */
    public static boolean isBlockedExam(Lesson l) {
        SolverRoom r = l.getRoom();
        SolverTimeSlot t = l.getTimeSlot();
        if (r == null || t == null) return false;
        for (int h = t.getStartHour(); h < t.getStartHour() + 3; h++) {
            if (isBlocked(r.getCode(), t.getDayOfWeek(), h)) return true;
        }
        return false;
    }

    private static boolean isBlocked(String roomCode, String day, int hour) {
        Set<String> slots = BLOCKED_SLOTS.get(roomCode);
        return slots != null && slots.contains(day + "_" + hour);
    }
}