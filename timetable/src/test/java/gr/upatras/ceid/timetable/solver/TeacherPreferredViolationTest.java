package gr.upatras.ceid.timetable.solver;

import ai.timefold.solver.core.api.score.ScoreExplanation;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK 2: tests για τον pure extractor {@link SolverService#extractTeacherPreferredViolations}
 * (SOFT teacherPreferredSlot — "πράσινο" κελί). Ίδιο στιλ με το {@link SolutionAnalysisTest}:
 * in-memory ΤΟΠΟΘΕΤΗΜΕΝΗ λύση, πραγματικός {@link CeidConstraintProvider} μέσω
 * {@code SolutionManager.explain}. Κρίσιμο: επιβεβαιώνει ότι εξάγεται ΜΟΝΟ ο
 * teacherPreferredSlot, ΟΧΙ άλλοι soft constraints (π.χ. preferNormalHours).
 */
class TeacherPreferredViolationTest {

    private static final SolverRoom D1   = new SolverRoom(3L, "Δ1", 110, "CLASSROOM");
    private static final SolverRoom BETA = new SolverRoom(2L, "Β", 238, "AMPHITHEATER");

    private static SolverTimeSlot slot(long id, String day, int hour) {
        return new SolverTimeSlot(id, day, hour);
    }

    private static Lesson placed(long assignmentId, long courseId, String code, int year,
                                 String assignType, SolverTimeSlot ts, SolverRoom room, String teacher) {
        Lesson l = new Lesson(assignmentId, courseId, code, "Μάθημα " + code,
                year, "ELECTIVE", assignType, 60, "FALL", year * 2 - 1);
        l.setTimeSlot(ts);
        l.setRoom(room);
        l.setTeacherKeys(Set.of(teacher));
        return l;
    }

    private static CeidTimetable solutionOf(Lesson... lessons) {
        Map<Long, SolverTimeSlot> slots = new LinkedHashMap<>();
        Map<Long, SolverRoom> rooms = new LinkedHashMap<>();
        for (Lesson l : lessons) {
            if (l.getTimeSlot() != null) slots.putIfAbsent(l.getTimeSlot().getId(), l.getTimeSlot());
            if (l.getRoom() != null) rooms.putIfAbsent(l.getRoom().getId(), l.getRoom());
        }
        return new CeidTimetable(new ArrayList<>(slots.values()),
                new ArrayList<>(rooms.values()), List.of(lessons));
    }

    private static SolutionManager<CeidTimetable, HardSoftScore> weeklySolutionManager() {
        SolverConfig cfg = new SolverConfig()
                .withSolutionClass(CeidTimetable.class)
                .withEntityClasses(Lesson.class)
                .withConstraintProviderClass(CeidConstraintProvider.class);
        return SolutionManager.create(SolverFactory.create(cfg));
    }

    private static List<SoftViolation> analyze(CeidTimetable solution) {
        ScoreExplanation<CeidTimetable, HardSoftScore> exp = weeklySolutionManager().explain(solution);
        return SolverService.extractTeacherPreferredViolations(exp);
    }

    @AfterEach
    void resetStaticRegistries() {
        TeacherAvailabilityConstraints.BLOCKED_SLOTS = Map.of();
        TeacherAvailabilityConstraints.PREFERRED_SLOTS = Map.of();
        RoomAvailabilityConstraints.BLOCKED_SLOTS = Map.of();
        SolverWeights.resetToDefaults();
    }

    /**
     * Ένα μάθημα εκτός preferred → 1 violation. ΤΑΥΤΟΧΡΟΝΑ ένα δεύτερο μάθημα σε νυχτερινή
     * ώρα (19:00 → ενεργοποιεί τον ΑΛΛΟ soft "Prefer normal hours") ΧΩΡΙΣ preferred: πρέπει
     * να ΜΗΝ εμφανιστεί. Αποδεικνύει ότι εξάγεται ΜΟΝΟ ο teacherPreferredSlot.
     */
    @Test
    void onlyTeacherPreferred_extracted_notOtherSoft() {
        TeacherAvailabilityConstraints.PREFERRED_SLOTS =
                Map.of("ΤΣΙΧΛΑΣ|Κ", Set.of("MONDAY_9", "MONDAY_10", "MONDAY_11"));

        // A: preferred καθηγητής, τοποθετημένος ΕΚΤΟΣ (Δευτέρα 13:00) → 1 teacherPreferred violation
        Lesson a = placed(201, 1, "C1", 2, "LECTURE", slot(10, "MONDAY", 13), D1, "ΤΣΙΧΛΑΣ|Κ");
        // B: ΧΩΡΙΣ preferred, νυχτερινή ώρα (19:00) → ενεργοποιεί "Prefer normal hours" (άλλος soft)
        Lesson b = placed(202, 2, "C2", 3, "LECTURE", slot(11, "MONDAY", 19), BETA, "ΑΛΛΟΣ|Χ");

        List<SoftViolation> v = analyze(solutionOf(a, b));

        assertEquals(1, v.size(), "ακριβώς 1 soft παραβίαση — ΜΟΝΟ ο teacherPreferredSlot");
        SoftViolation sv = v.get(0);
        assertEquals("Teacher not in preferred slot", sv.constraintName());
        assertEquals(List.of(201L), sv.assignmentIds(), "μόνο το A ενοχοποιείται (όχι το νυχτερινό B)");
        assertEquals(-3, sv.softImpact(), "βάρος WEEKLY_TEACHER_PREFERRED = 3");
    }

    /** Καθηγητής τοποθετημένος ΜΕΣΑ στις preferred ώρες του → καμία παραβίαση. */
    @Test
    void insidePreferred_noViolation() {
        TeacherAvailabilityConstraints.PREFERRED_SLOTS =
                Map.of("ΤΣΙΧΛΑΣ|Κ", Set.of("MONDAY_9", "MONDAY_10", "MONDAY_11"));
        Lesson a = placed(201, 1, "C1", 2, "LECTURE", slot(10, "MONDAY", 9), D1, "ΤΣΙΧΛΑΣ|Κ");
        assertTrue(analyze(solutionOf(a)).isEmpty(), "εντός preferred: καμία soft παραβίαση");
    }

    /** Χωρίς preferred registry: ακόμα και νυχτερινή τοποθέτηση δεν παράγει teacherPreferred. */
    @Test
    void noPreferredRegistry_noTeacherPreferredViolation() {
        Lesson a = placed(201, 1, "C1", 2, "LECTURE", slot(10, "MONDAY", 19), D1, "ΤΣΙΧΛΑΣ|Κ");
        assertTrue(analyze(solutionOf(a)).isEmpty(),
                "κενό PREFERRED_SLOTS: κανένας teacherPreferred match (ο preferNormalHours φιλτράρεται)");
    }
}
