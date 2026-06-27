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
 * Φ-SV2a: parity tests για τον κρίκο engine-violation → report-code.
 *
 * Αποδεικνύει ντετερμινιστικά (χωρίς DB) ότι ο engine Φ-SV1
 * ({@link SolverService#extractHardViolations}) + το συμβόλαιο
 * {@link ConstraintCodeMapping} παράγουν το ΣΩΣΤΟ report code ανά σενάριο,
 * ΣΥΜΠΕΡΙΛΑΜΒΑΝΟΜΕΝΩΝ των 2 NEW (TEACHER_BLOCKED, ROOM_BLOCKED) που το παλιό
 * validateTimetableReport έχανε. ΚΑΜΙΑ σύνδεση στο live report — αυτό είναι η Φάση 2b.
 *
 * Ότι «κάθε constraint πυροδοτείται σωστά» καλύπτεται ΗΔΗ από τα ConstraintVerifier
 * tests (CeidConstraintProviderTest 37 / ExamConstraintProviderTest 23)· εδώ
 * αποδεικνύουμε ΜΟΝΟ τον κρίκο violation → code (2 representative MATCH + 2 NEW + clean).
 */
class ValidationEngineParityTest {

    /** Τα 14 γνωστά HARD ονόματα (11 weekly + 3 exam), όπως στα asConstraint(...). */
    private static final Set<String> EXPECTED_HARD_NAMES = Set.of(
            // WEEKLY (11)
            "Room conflict",
            "Teacher conflict",
            "Same course conflict",
            "Required same-year conflict",
            "Lab must be in LAB room",
            "First year only in room G",
            "Required courses only in B or G",
            "Daily lecture limit for required courses",
            "Lunch break required for first three years",
            "Teacher blocked slot",
            "Room blocked slot",
            // EXAM (3)
            "Exam teacher conflict",
            "Required same-year exams on same day",
            "Exam room blocked slot");

    // ---------- fixtures ----------

    private static final SolverRoom D1   = new SolverRoom(3L, "Δ1", 110, "CLASSROOM");
    private static final SolverRoom BETA = new SolverRoom(2L, "Β", 238, "AMPHITHEATER");

    private static SolverTimeSlot slot(long id, String day, int hour) {
        return new SolverTimeSlot(id, day, hour);
    }

    /** Πλήρως τοποθετημένο Lesson — το id παίζει τον ρόλο του assignment id. */
    private static Lesson placed(long assignmentId, long courseId, String code, int year,
                                 String courseType, String assignType, int students,
                                 SolverTimeSlot ts, SolverRoom room, String... teachers) {
        Lesson l = new Lesson(assignmentId, courseId, code, "Μάθημα " + code,
                year, courseType, assignType, students, "FALL", year * 2 - 1);
        l.setTimeSlot(ts);
        l.setRoom(room);
        l.setTeacherKeys(Set.of(teachers));
        return l;
    }

    /** Τυλίγει τα lessons σε CeidTimetable με value ranges τα slots/rooms που χρησιμοποιούν. */
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

    /** Weekly SolutionManager — ίδιο config με τον SolverService.solverFactoryFor (χωρίς termination). */
    private static SolutionManager<CeidTimetable, HardSoftScore> weeklySolutionManager() {
        SolverConfig cfg = new SolverConfig()
                .withSolutionClass(CeidTimetable.class)
                .withEntityClasses(Lesson.class)
                .withConstraintProviderClass(CeidConstraintProvider.class);
        return SolutionManager.create(SolverFactory.create(cfg));
    }

    private static List<HardViolation> analyze(CeidTimetable solution) {
        ScoreExplanation<CeidTimetable, HardSoftScore> exp = weeklySolutionManager().explain(solution);
        return SolverService.extractHardViolations(exp);
    }

    @AfterEach
    void resetStaticRegistries() {
        TeacherAvailabilityConstraints.BLOCKED_SLOTS = Map.of();
        TeacherAvailabilityConstraints.PREFERRED_SLOTS = Map.of();
        RoomAvailabilityConstraints.BLOCKED_SLOTS = Map.of();
        SolverWeights.resetToDefaults();
    }

    // ---------- (α) mapping completeness (διπλή κατεύθυνση) ----------

    @Test
    void mappingCompleteness_everyKnownHardNameHasCode_andNoOrphans() {
        // (i) κάθε γνωστό hard όνομα έχει code
        for (String name : EXPECTED_HARD_NAMES) {
            assertTrue(ConstraintCodeMapping.codeFor(name).isPresent(),
                    "λείπει mapping για γνωστό hard constraint: " + name);
        }
        // (ii) καμία επιπλέον/ορφανή entry — guard «νέο hard constraint χωρίς code → κόκκινο»
        assertEquals(EXPECTED_HARD_NAMES, ConstraintCodeMapping.HARD_NAME_TO_CODE.keySet(),
                "το mapping δεν πρέπει να έχει keys πέρα από τα γνωστά hard ονόματα");
    }

    // ---------- (β) engine → code, ανά σενάριο ----------

    @Test
    void roomConflict_mapsToRoomConflictCode() {
        SolverTimeSlot ts = slot(10, "MONDAY", 9);
        Lesson a = placed(101, 1, "C1", 2, "ELECTIVE", "LECTURE", 60, ts, D1, "T1|A");
        Lesson b = placed(102, 2, "C2", 2, "ELECTIVE", "LECTURE", 60, ts, D1, "T2|B");

        List<HardViolation> v = analyze(solutionOf(a, b));

        assertEquals(1, v.size(), "ακριβώς 1 HARD violation");
        HardViolation hv = v.get(0);
        assertEquals("Room conflict", hv.constraintName());
        assertEquals("ROOM_CONFLICT", ConstraintCodeMapping.codeFor(hv.constraintName()).orElseThrow());
        assertEquals(Set.of(101L, 102L), Set.copyOf(hv.assignmentIds()),
                "και τα 2 assignment ids ενοχοποιούνται");
    }

    @Test
    void labInNonLabRoom_mapsToLabRoomRequiredCode() {
        Lesson lab = placed(201, 1, "C1", 2, "ELECTIVE", "LAB", 40, slot(20, "MONDAY", 9), D1, "T1|A");

        List<HardViolation> v = analyze(solutionOf(lab));

        assertEquals(1, v.size(), "ακριβώς 1 HARD violation");
        HardViolation hv = v.get(0);
        assertEquals("Lab must be in LAB room", hv.constraintName());
        assertEquals("LAB_ROOM_REQUIRED", ConstraintCodeMapping.codeFor(hv.constraintName()).orElseThrow());
        assertEquals(List.of(201L), hv.assignmentIds());
    }

    @Test
    void teacherBlocked_mapsToTeacherBlockedCode_NEW() {
        // NEW: ο engine πιάνει ό,τι έχανε το παλιό report.
        TeacherAvailabilityConstraints.BLOCKED_SLOTS =
                Map.of("ΒΛΑΧΟΣ|Κ", Set.of("WEDNESDAY_9"));
        Lesson l = placed(301, 1, "C1", 2, "ELECTIVE", "LECTURE", 60,
                slot(30, "WEDNESDAY", 9), D1, "ΒΛΑΧΟΣ|Κ");

        List<HardViolation> v = analyze(solutionOf(l));

        assertEquals(1, v.size(), "ακριβώς 1 HARD violation");
        HardViolation hv = v.get(0);
        assertEquals("Teacher blocked slot", hv.constraintName());
        assertEquals("TEACHER_BLOCKED", ConstraintCodeMapping.codeFor(hv.constraintName()).orElseThrow());
        assertEquals(List.of(301L), hv.assignmentIds());
    }

    @Test
    void roomBlocked_mapsToRoomBlockedCode_NEW() {
        // NEW: ο engine πιάνει ό,τι έχανε το παλιό report.
        RoomAvailabilityConstraints.BLOCKED_SLOTS =
                Map.of("Δ1", Set.of("WEDNESDAY_12"));
        Lesson l = placed(401, 1, "C1", 4, "ELECTIVE", "LECTURE", 60,
                slot(40, "WEDNESDAY", 12), D1, "T1|A");

        List<HardViolation> v = analyze(solutionOf(l));

        assertEquals(1, v.size(), "ακριβώς 1 HARD violation");
        HardViolation hv = v.get(0);
        assertEquals("Room blocked slot", hv.constraintName());
        assertEquals("ROOM_BLOCKED", ConstraintCodeMapping.codeFor(hv.constraintName()).orElseThrow());
        assertEquals(List.of(401L), hv.assignmentIds());
    }

    @Test
    void clean_noViolations_noCodes() {
        Lesson a = placed(501, 1, "C1", 2, "ELECTIVE", "LECTURE", 60, slot(50, "MONDAY", 9),  D1,   "T1|A");
        Lesson b = placed(502, 2, "C2", 2, "ELECTIVE", "LECTURE", 60, slot(51, "MONDAY", 10), BETA, "T2|B");
        assertTrue(analyze(solutionOf(a, b)).isEmpty(),
                "καθαρή λύση → καμία violation → κανένα code");
    }
}
