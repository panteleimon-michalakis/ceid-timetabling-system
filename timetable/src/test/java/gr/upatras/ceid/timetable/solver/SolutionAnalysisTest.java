package gr.upatras.ceid.timetable.solver;

import ai.timefold.solver.core.api.score.ScoreExplanation;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import gr.upatras.ceid.timetable.entity.Room;
import gr.upatras.ceid.timetable.entity.TimeSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Φ-SV1: tests για τη μηχανή ανάλυσης score-explanation (SolverService).
 *
 * Δεν χρειάζονται DB ούτε full solve: χτίζεται in-memory ΤΟΠΟΘΕΤΗΜΕΝΗ λύση, τρέχει
 * ο ΠΡΑΓΜΑΤΙΚΟΣ {@link CeidConstraintProvider} μέσω {@code SolutionManager.explain}
 * (ίδιο config με τον helper {@code solverFactoryFor}, weekly), και ελέγχεται ο pure
 * extractor {@link SolverService#extractHardViolations}. Δείχνει ειδικά ότι το νέο
 * path πιάνει τα teacher/room-blocked που το παλιό validation report έχανε.
 */
class SolutionAnalysisTest {

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

    /** Τυλίγει τα lessons σε CeidTimetable με value ranges τα slots/rooms που χρησιμοποιούν
     *  (ίδια δομή με το buildPlacedSolution: distinct slots/rooms). */
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

    /** Weekly SolutionManager — ίδιο config με τον solverFactoryFor (χωρίς termination). */
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

    // ---------- 1) clean ----------

    @Test
    void clean_noHardViolations() {
        // Δύο επιλογής μαθήματα 2ου έτους, διαφορετικά slots/rooms/courses/teachers,
        // χωρίς registry blocks → καμία HARD παραβίαση.
        Lesson a = placed(101, 1, "C1", 2, "ELECTIVE", "LECTURE", 60, slot(10, "MONDAY", 9),  D1,   "T1|A");
        Lesson b = placed(102, 2, "C2", 2, "ELECTIVE", "LECTURE", 60, slot(11, "MONDAY", 10), BETA, "T2|B");
        assertTrue(analyze(solutionOf(a, b)).isEmpty(), "καθαρή λύση: καμία HARD παραβίαση");
    }

    // ---------- 2) room conflict ----------

    @Test
    void roomConflict_oneViolationWithBothIds() {
        SolverTimeSlot ts = slot(10, "MONDAY", 9);
        Lesson a = placed(101, 1, "C1", 2, "ELECTIVE", "LECTURE", 60, ts, D1, "T1|A");
        Lesson b = placed(102, 2, "C2", 2, "ELECTIVE", "LECTURE", 60, ts, D1, "T2|B");

        List<HardViolation> v = analyze(solutionOf(a, b));

        assertEquals(1, v.size(), "ακριβώς 1 HARD παραβίαση");
        HardViolation hv = v.get(0);
        assertEquals("Room conflict", hv.constraintName());
        assertEquals(-1, hv.hardImpact(), "βάρος WEEKLY_ROOM_CONFLICT = 1");
        assertEquals(Set.of(101L, 102L), Set.copyOf(hv.assignmentIds()),
                "και τα 2 assignment ids ενοχοποιούνται");
    }

    // ---------- 3) teacher blocked slot (ήταν MISSING στο παλιό report) ----------

    @Test
    void teacherBlockedSlot_oneViolation() {
        TeacherAvailabilityConstraints.BLOCKED_SLOTS =
                Map.of("ΒΛΑΧΟΣ|Κ", Set.of("WEDNESDAY_9"));
        Lesson l = placed(201, 1, "C1", 2, "ELECTIVE", "LECTURE", 60,
                slot(30, "WEDNESDAY", 9), D1, "ΒΛΑΧΟΣ|Κ");

        List<HardViolation> v = analyze(solutionOf(l));

        assertEquals(1, v.size(), "ακριβώς 1 HARD παραβίαση");
        HardViolation hv = v.get(0);
        assertEquals("Teacher blocked slot", hv.constraintName());
        assertEquals(-10, hv.hardImpact(), "βάρος WEEKLY_TEACHER_BLOCKED = 10");
        assertEquals(List.of(201L), hv.assignmentIds());
    }

    // ---------- 4) room blocked slot ----------

    @Test
    void roomBlockedSlot_oneViolation() {
        RoomAvailabilityConstraints.BLOCKED_SLOTS =
                Map.of("Δ1", Set.of("WEDNESDAY_12"));
        Lesson l = placed(301, 1, "C1", 4, "ELECTIVE", "LECTURE", 60,
                slot(40, "WEDNESDAY", 12), D1, "T1|A");

        List<HardViolation> v = analyze(solutionOf(l));

        assertEquals(1, v.size(), "ακριβώς 1 HARD παραβίαση");
        HardViolation hv = v.get(0);
        assertEquals("Room blocked slot", hv.constraintName());
        assertEquals(-10, hv.hardImpact(), "βάρος WEEKLY_ROOM_BLOCKED = 10");
        assertEquals(List.of(301L), hv.assignmentIds());
    }

    // ---------- 5) mapper parity ----------

    @Test
    void mapperParity_timeSlotAndRoom() {
        // Semester slot: specificDate null → dayKey = όνομα ημέρας.
        TimeSlot sem = new TimeSlot();
        sem.setId(42L);
        sem.setDayOfWeek(DayOfWeek.WEDNESDAY);
        sem.setStartTime(LocalTime.of(14, 0));
        sem.setEndTime(LocalTime.of(15, 0));
        sem.setSlotType(TimeSlot.SlotType.SEMESTER);
        SolverTimeSlot ssem = SolverService.toSolverTimeSlot(sem);
        assertEquals(42L, ssem.getId().longValue());
        assertEquals("WEDNESDAY", ssem.getDayOfWeek());
        assertEquals(14, ssem.getStartHour());
        assertEquals("WEDNESDAY", ssem.getDayKey());

        // Exam slot: specificDate παρόν → dayKey = ISO ημερομηνία.
        TimeSlot exam = new TimeSlot();
        exam.setId(7L);
        exam.setDayOfWeek(DayOfWeek.MONDAY);
        exam.setStartTime(LocalTime.of(9, 0));
        exam.setEndTime(LocalTime.of(12, 0));
        exam.setSlotType(TimeSlot.SlotType.EXAM);
        exam.setSpecificDate(LocalDate.of(2026, 1, 15));
        SolverTimeSlot sexam = SolverService.toSolverTimeSlot(exam);
        assertEquals("MONDAY", sexam.getDayOfWeek());
        assertEquals(9, sexam.getStartHour());
        assertEquals("2026-01-15", sexam.getDayKey());

        // Room mapper.
        Room room = new Room();
        room.setId(5L);
        room.setName("Αίθουσα Δ1");
        room.setCode("Δ1");
        room.setCapacity(110);
        room.setRoomType(Room.RoomType.CLASSROOM);
        SolverRoom sroom = SolverService.toSolverRoom(room);
        assertEquals(5L, sroom.getId().longValue());
        assertEquals("Δ1", sroom.getCode());
        assertEquals(110, sroom.getCapacity());
        assertEquals("CLASSROOM", sroom.getRoomType());
    }
}
