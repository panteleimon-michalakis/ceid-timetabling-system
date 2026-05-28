package gr.upatras.ceid.timetable.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;

import java.util.Map;
import java.util.Set;

/**
 * Περιορισμοί διαθεσιμότητας καθηγητών.
 *
 * Κάθε καθηγητής μπορεί να έχει:
 *   - blockedSlots  : ώρες/μέρες που ΔΕΝ μπορεί (HARD constraint)
 *   - preferredSlots: ώρες/μέρες που ΠΡΟΤΙΜΑ    (SOFT constraint)
 *
 * Η αρχικοποίηση γίνεται μέσω στατικών maps που φορτώνει το SolverService
 * πριν τρέξει ο solver.
 *
 * Μορφή slot key : "MONDAY_9", "TUESDAY_14", "FRIDAY_19", κλπ.
 * Μορφή teacher key: ίδια με teacherKey() στο SolverService
 *                    (π.χ. "VLACHOS_K", "KAKLAMANIS_C")
 */
public class TeacherAvailabilityConstraints {

    // -----------------------------------------------------------------------
    // Static registry — φορτώνεται από το SolverService πριν τον solver
    // -----------------------------------------------------------------------

    /**
     * teacherKey -> set of "DAY_HOUR" slots που είναι BLOCKED (hard).
     *
     * Παραδείγματα από τα constraint forms:
     *   "VLACHOS_K"      -> {"WEDNESDAY_9","WEDNESDAY_10","WEDNESDAY_11",...,
     *                         "THURSDAY_9", ..., "FRIDAY_9", ...}
     *   "BERBERIDIS_K"   -> {"MONDAY_9","MONDAY_10",...,"FRIDAY_9",...}
     *   "KOMNINOS_A"     -> {"MONDAY_13","MONDAY_14",...,"WEDNESDAY_13",...}
     */
    public static volatile Map<String, Set<String>> BLOCKED_SLOTS = Map.of();

    /**
     * teacherKey -> set of "DAY_HOUR" slots που είναι PREFERRED (soft).
     *
     * Παραδείγματα:
     *   "KAKLAMANIS_C"   -> {"FRIDAY_19","FRIDAY_20"}  (7-9μμ Παρασκευή)
     *   "ZAROLIAGKIS_C"  -> {"TUESDAY_11","THURSDAY_9"}
     *   "SKLAVOS_N"      -> {"WEDNESDAY_16","WEDNESDAY_17","WEDNESDAY_18"}
     */
    public static volatile Map<String, Set<String>> PREFERRED_SLOTS = Map.of();

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    /** Φτιάχνει το slot key από ένα SolverTimeSlot: "WEDNESDAY_14" */
    static String slotKey(SolverTimeSlot ts) {
        if (ts == null) return "";
        return ts.getDayOfWeek() + "_" + ts.getStartHour();
    }

    /**
     * Επιστρέφει true αν ΟΠΟΙΟΣΔΗΠΟΤΕ teacher του lesson
     * έχει το συγκεκριμένο slot blocked.
     */
    static boolean isBlocked(Lesson lesson) {
        if (lesson.getTimeSlot() == null) return false;
        if (BLOCKED_SLOTS.isEmpty()) return false;

        String slot = slotKey(lesson.getTimeSlot());
        for (String teacherKey : lesson.getTeacherKeys()) {
            Set<String> blocked = BLOCKED_SLOTS.get(teacherKey);
            if (blocked != null && blocked.contains(slot)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Επιστρέφει true αν ΟΠΟΙΟΣΔΗΠΟΤΕ teacher έχει δηλώσει preferred slots
     * αλλά το τρέχον slot ΔΕΝ είναι μέσα σε αυτά.
     */
    static boolean isNotPreferred(Lesson lesson) {
        if (lesson.getTimeSlot() == null) return false;
        if (PREFERRED_SLOTS.isEmpty()) return false;

        String slot = slotKey(lesson.getTimeSlot());
        for (String teacherKey : lesson.getTeacherKeys()) {
            Set<String> preferred = PREFERRED_SLOTS.get(teacherKey);
            if (preferred != null && !preferred.isEmpty() && !preferred.contains(slot)) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Constraints
    // -----------------------------------------------------------------------


    /**
     * HARD: Καθηγητής δεν μπορεί να διδάσκει σε blocked ώρα/μέρα.
     * Penalty: -10 hard ανά violation (μεγαλύτερο από room/teacher conflict
     * ώστε να έχει προτεραιότητα).
     *
     * Παραδείγματα που καλύπτει:
     *   - Βλάχος: ΟΧΙ Τετάρτη, Πέμπτη, Παρασκευή
     *   - Μπερμπερίδης: ΟΧΙ Δευτέρα ή Παρασκευή
     *   - Κομνηνός: ΟΧΙ Δευτέρα & Τετάρτη μετά τις 13:00
     *   - Ζερβάκης: ΟΧΙ Δευτέρα και Παρασκευή πρωί (σε άλλα μαθήματα)
     */
    Constraint teacherBlockedSlot(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(TeacherAvailabilityConstraints::isBlocked)
                .penalize(HardSoftScore.ofHard(10))
                .asConstraint("Teacher blocked slot");
    }

    /**
     * SOFT: Καθηγητής προτιμά συγκεκριμένες ώρες/μέρες.
     * Penalty: -3 soft ανά ώρα εκτός preferred (ήπιο, δεν μπλοκάρει solver).
     *
     * Παραδείγματα:
     *   - Κακλαμάνης: Παρ 19:00-21:00
     *   - Ζαρολιάγκης: Τρίτη 11-13, Πέμπτη 9-11
     *   - Σκλάβος (Κυβερνοασφάλεια): Τετάρτη 16-19
     *   - Βέργος: 09:00-11:00 οποιαδήποτε μέρα
     */
    Constraint teacherPreferredSlot(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null)
                .filter(TeacherAvailabilityConstraints::isNotPreferred)
                .penalize(HardSoftScore.ONE_SOFT, l -> 3)
                .asConstraint("Teacher not in preferred slot");
    }
}