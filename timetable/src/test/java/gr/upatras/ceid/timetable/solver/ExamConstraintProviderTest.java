package gr.upatras.ceid.timetable.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

/**
 * Unit tests για τον ExamConstraintProvider (πρόγραμμα εξεταστικής)
 * με το Timefold ConstraintVerifier.
 *
 * Στην εξεταστική τα slots είναι 3ωρα (9/12/15/18) και κάθε slot έχει
 * συγκεκριμένη ημερομηνία (dayKey, π.χ. "2026-09-01").
 */
class ExamConstraintProviderTest {

    private final ConstraintVerifier<ExamConstraintProvider, CeidTimetable> verifier =
            ConstraintVerifier.build(new ExamConstraintProvider(), CeidTimetable.class, Lesson.class);

    // ---------- fixtures ----------

    private static final SolverRoom GAMMA = new SolverRoom(1L, "Γ", 244, "AMPHITHEATER");
    private static final SolverRoom BETA  = new SolverRoom(2L, "Β", 238, "AMPHITHEATER");
    private static final SolverRoom D1    = new SolverRoom(3L, "Δ1", 110, "CLASSROOM");

    /** Exam slot: ημερομηνία + ώρα έναρξης 3ωρου (9/12/15/18). */
    private static SolverTimeSlot examSlot(long id, String date, int startHour) {
        return new SolverTimeSlot(id, "MONDAY", startHour, date);
    }

    private static long nextId = 1;

    private static Lesson exam(long courseId, String code, int year, String courseType,
                               int students, SolverTimeSlot ts, SolverRoom room,
                               String... teachers) {
        Lesson l = new Lesson(nextId++, courseId, code, "Μάθημα " + code,
                year, courseType, "EXAM", students, "FALL", year * 2 - 1);
        l.setTimeSlot(ts);
        l.setRoom(room);
        l.setTeacherKeys(Set.of(teachers));
        return l;
    }

    @AfterEach
    void resetStaticRegistries() {
        RoomAvailabilityConstraints.BLOCKED_SLOTS = Map.of();
    }

    // ---------- HARD ----------

    @Test
    void teacherConflict_twoExamsSameSlotSharedTeacher() {
        SolverTimeSlot ts = examSlot(10, "2026-09-01", 9);
        Lesson a = exam(1, "C1", 2, "REQUIRED", 100, ts, BETA, "ΒΛΑΧΟΣ|Κ");
        Lesson b = exam(2, "C2", 3, "ELECTIVE", 50, ts, GAMMA, "ΒΛΑΧΟΣ|Κ");
        verifier.verifyThat(ExamConstraintProvider::teacherConflict)
                .given(a, b).penalizesBy(1);
    }

    @Test
    void requiredSameYearSameDay_penalized() {
        // Δύο υποχρεωτικά ίδιου έτους την ίδια ημερομηνία (έστω άλλο slot) → hard
        Lesson a = exam(1, "C1", 2, "REQUIRED", 100, examSlot(10, "2026-09-01", 9), BETA, "T1|A");
        Lesson b = exam(2, "C2", 2, "REQUIRED", 100, examSlot(11, "2026-09-01", 15), GAMMA, "T2|B");
        verifier.verifyThat(ExamConstraintProvider::requiredSameYearSameDay)
                .given(a, b).penalizesBy(1); // 1 match (βάρος ofHard(5))
    }

    @Test
    void requiredSameYearSameDay_differentDatesAllowed() {
        Lesson a = exam(1, "C1", 2, "REQUIRED", 100, examSlot(10, "2026-09-01", 9), BETA, "T1|A");
        Lesson b = exam(2, "C2", 2, "REQUIRED", 100, examSlot(12, "2026-09-03", 9), GAMMA, "T2|B");
        verifier.verifyThat(ExamConstraintProvider::requiredSameYearSameDay)
                .given(a, b).penalizesBy(0);
    }

    @Test
    void requiredSameYearSameDay_electivePairExempt() {
        // Υποχρεωτικό + επιλογής ίδια μέρα: ΔΕΝ είναι hard violation
        // (καλύπτεται από το SOFT sameYearSameDay).
        Lesson a = exam(1, "C1", 2, "REQUIRED", 100, examSlot(10, "2026-09-01", 9), BETA, "T1|A");
        Lesson b = exam(2, "C2", 2, "ELECTIVE", 60, examSlot(11, "2026-09-01", 15), GAMMA, "T2|B");
        verifier.verifyThat(ExamConstraintProvider::requiredSameYearSameDay)
                .given(a, b).penalizesBy(0);
    }

    @Test
    void roomBlockedSlot_blockedHourInsideThreeHourWindowPenalized() {
        // Slot 09:00-12:00, δεσμευμένη η 11:00 → μπλοκάρεται όλο το παράθυρο.
        RoomAvailabilityConstraints.BLOCKED_SLOTS =
                Map.of("Δ1", Set.of("MONDAY_11"));
        Lesson l = exam(1, "C1", 4, "ELECTIVE", 60, examSlot(10, "2026-09-01", 9), D1, "T1|A");
        verifier.verifyThat(ExamConstraintProvider::roomBlockedSlot)
                .given(l).penalizesBy(1);
    }

    // ---------- SOFT ----------

    @Test
    void sharedRoomCapacity_roomSharingAllowedWithinCapacity() {
        // Κανόνας τμήματος: δύο εξετάσεις στην ίδια αίθουσα/ώρα επιτρέπονται
        // όσο το άθροισμα φοιτητών χωράει (100 + 100 < 238).
        SolverTimeSlot ts = examSlot(10, "2026-09-01", 9);
        Lesson a = exam(1, "C1", 2, "REQUIRED", 100, ts, BETA, "T1|A");
        Lesson b = exam(2, "C2", 3, "ELECTIVE", 100, ts, BETA, "T2|B");
        verifier.verifyThat(ExamConstraintProvider::sharedRoomCapacity)
                .given(a, b).penalizesBy(0);
    }

    @Test
    void sharedRoomCapacity_penalizedByTotalOverflow() {
        // 150 + 150 = 300 σε αίθουσα 238 → υπέρβαση 62.
        SolverTimeSlot ts = examSlot(10, "2026-09-01", 9);
        Lesson a = exam(1, "C1", 2, "REQUIRED", 150, ts, BETA, "T1|A");
        Lesson b = exam(2, "C2", 3, "ELECTIVE", 150, ts, BETA, "T2|B");
        verifier.verifyThat(ExamConstraintProvider::sharedRoomCapacity)
                .given(a, b).penalizesBy(300 - 238);
    }

    @Test
    void spreadSameYear_consecutiveDatesPenalized() {
        Lesson a = exam(1, "C1", 2, "REQUIRED", 100, examSlot(10, "2026-09-01", 9), BETA, "T1|A");
        Lesson b = exam(2, "C2", 2, "REQUIRED", 100, examSlot(12, "2026-09-02", 9), GAMMA, "T2|B");
        verifier.verifyThat(ExamConstraintProvider::spreadSameYear)
                .given(a, b).penalizesBy(3);
    }

    @Test
    void spreadSameYear_twoDayGapNotPenalized() {
        Lesson a = exam(1, "C1", 2, "REQUIRED", 100, examSlot(10, "2026-09-01", 9), BETA, "T1|A");
        Lesson b = exam(2, "C2", 2, "REQUIRED", 100, examSlot(12, "2026-09-03", 9), GAMMA, "T2|B");
        verifier.verifyThat(ExamConstraintProvider::spreadSameYear)
                .given(a, b).penalizesBy(0);
    }

    @Test
    void sameYearSameDay_electiveStackingPenalizedBySix() {
        Lesson a = exam(1, "C1", 2, "REQUIRED", 100, examSlot(10, "2026-09-01", 9), BETA, "T1|A");
        Lesson b = exam(2, "C2", 2, "ELECTIVE", 60, examSlot(11, "2026-09-01", 15), GAMMA, "T2|B");
        verifier.verifyThat(ExamConstraintProvider::sameYearSameDay)
                .given(a, b).penalizesBy(6);
    }

    @Test
    void teacherMultipleExamsSameDay_penalizedByTwo() {
        Lesson a = exam(1, "C1", 2, "REQUIRED", 100, examSlot(10, "2026-09-01", 9), BETA, "ΒΛΑΧΟΣ|Κ");
        Lesson b = exam(2, "C2", 4, "ELECTIVE", 50, examSlot(11, "2026-09-01", 15), GAMMA, "ΒΛΑΧΟΣ|Κ");
        verifier.verifyThat(ExamConstraintProvider::teacherMultipleExamsSameDay)
                .given(a, b).penalizesBy(2);
    }

    @Test
    void directionGroupADifferentDays_twoK1ExamsSameDay_penalizedByFive() {
        // Δύο μαθήματα Ομάδας Α της ίδιας κατεύθυνσης (Κ1) εξετάζονται την
        // ίδια ημερομηνία (έστω σε άλλο 3ωρο) → ποινή 5.
        Lesson a = exam(1, "CEID_NE5057", 4, "ELECTIVE", 60, examSlot(10, "2026-09-01", 9), BETA, "T1|A");
        Lesson b = exam(2, "CEID_NE4168", 4, "ELECTIVE", 60, examSlot(11, "2026-09-01", 12), GAMMA, "T2|B");
        verifier.verifyThat(ExamConstraintProvider::directionGroupADifferentDays)
                .given(a, b).penalizesBy(5);
    }

    @Test
    void directionGroupADifferentDays_k1ExamsDifferentDays_notPenalized() {
        Lesson a = exam(1, "CEID_NE5057", 4, "ELECTIVE", 60, examSlot(10, "2026-09-01", 9), BETA, "T1|A");
        Lesson b = exam(2, "CEID_NE4168", 4, "ELECTIVE", 60, examSlot(12, "2026-09-03", 9), GAMMA, "T2|B");
        verifier.verifyThat(ExamConstraintProvider::directionGroupADifferentDays)
                .given(a, b).penalizesBy(0);
    }

    @Test
    void preferMorningForLargeCourses_largeExamAfternoonPenalized() {
        Lesson l = exam(1, "C1", 1, "REQUIRED", 200, examSlot(10, "2026-09-01", 15), GAMMA, "T1|A");
        verifier.verifyThat(ExamConstraintProvider::preferMorningForLargeCourses)
                .given(l).penalizesBy(1);
    }

    @Test
    void preferMorningForLargeCourses_morningSlotNotPenalized() {
        Lesson l = exam(1, "C1", 1, "REQUIRED", 200, examSlot(10, "2026-09-01", 9), GAMMA, "T1|A");
        verifier.verifyThat(ExamConstraintProvider::preferMorningForLargeCourses)
                .given(l).penalizesBy(0);
    }

    @Test
    void preferDistinctRoomsWithinSlot_tieBreakerPenaltyOfOne() {
        SolverTimeSlot ts = examSlot(10, "2026-09-01", 9);
        Lesson a = exam(1, "C1", 2, "REQUIRED", 50, ts, BETA, "T1|A");
        Lesson b = exam(2, "C2", 3, "ELECTIVE", 50, ts, BETA, "T2|B");
        verifier.verifyThat(ExamConstraintProvider::preferDistinctRoomsWithinSlot)
                .given(a, b).penalizesBy(1);
    }

    @Test
    void dailyLoadBalance_quadraticGrowthWithExamsPerDay() {
        // 3 εξετάσεις ίδια μέρα → 3 ζεύγη → ποινή 3 (n(n-1)/2).
        Lesson a = exam(1, "C1", 1, "REQUIRED", 100, examSlot(10, "2026-09-01", 9), BETA, "T1|A");
        Lesson b = exam(2, "C2", 2, "REQUIRED", 100, examSlot(11, "2026-09-01", 12), GAMMA, "T2|B");
        Lesson c = exam(3, "C3", 3, "REQUIRED", 100, examSlot(12, "2026-09-01", 15), D1, "T3|C");
        verifier.verifyThat(ExamConstraintProvider::dailyLoadBalance)
                .given(a, b, c).penalizesBy(3);
    }

    @Test
    void requiredBeforeElectives_requiredAfterElectivePenalized() {
        Lesson required = exam(1, "C1", 2, "REQUIRED", 100, examSlot(12, "2026-09-10", 9), BETA, "T1|A");
        Lesson elective = exam(2, "C2", 4, "ELECTIVE", 50, examSlot(10, "2026-09-01", 9), D1, "T2|B");
        verifier.verifyThat(ExamConstraintProvider::requiredBeforeElectives)
                .given(required, elective).penalizesBy(1);
    }

    // ---------- A6: Προτιμήσεις διδασκόντων (φόρμες) ----------

    @Test
    void preferredExamRoom_outsidePreferenceListPenalized() {
        Lesson l = exam(1, "C1", 2, "REQUIRED", 100, examSlot(10, "2026-09-01", 9), D1, "T1|A");
        l.setPreferredRoomCodes(java.util.Set.of("Β", "Γ"));
        verifier.verifyThat(ExamConstraintProvider::preferredExamRoom)
                .given(l).penalizesBy(1); // 1 match (βάρος constraint = ofSoft(4))
    }

    @Test
    void preferredExamRoom_withinPreferenceNotPenalized() {
        Lesson l = exam(1, "C1", 2, "REQUIRED", 100, examSlot(10, "2026-09-01", 9), BETA, "T1|A");
        l.setPreferredRoomCodes(java.util.Set.of("Β", "Γ"));
        verifier.verifyThat(ExamConstraintProvider::preferredExamRoom)
                .given(l).penalizesBy(0);
    }

    @Test
    void preferredExamRoom_noPreferenceNeverPenalized() {
        Lesson l = exam(1, "C1", 2, "REQUIRED", 100, examSlot(10, "2026-09-01", 9), D1, "T1|A");
        verifier.verifyThat(ExamConstraintProvider::preferredExamRoom)
                .given(l).penalizesBy(0);
    }

    @Test
    void preferredExamHour_outsidePreferencePenalized() {
        Lesson l = exam(1, "C1", 2, "REQUIRED", 100, examSlot(10, "2026-09-01", 15), BETA, "T1|A");
        l.setPreferredStartHours(java.util.Set.of(9, 12));
        verifier.verifyThat(ExamConstraintProvider::preferredExamHour)
                .given(l).penalizesBy(1); // 1 match (βάρος constraint = ofSoft(4))
    }

    @Test
    void requiredBeforeElectives_correctOrderNotPenalized() {
        Lesson required = exam(1, "C1", 2, "REQUIRED", 100, examSlot(10, "2026-09-01", 9), BETA, "T1|A");
        Lesson elective = exam(2, "C2", 4, "ELECTIVE", 50, examSlot(12, "2026-09-10", 9), D1, "T2|B");
        verifier.verifyThat(ExamConstraintProvider::requiredBeforeElectives)
                .given(required, elective).penalizesBy(0);
    }
}
