package gr.upatras.ceid.timetable.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests για τον CeidConstraintProvider (εβδομαδιαίο πρόγραμμα)
 * με το Timefold ConstraintVerifier. Κάθε test απομονώνει ΕΝΑ constraint.
 *
 * Καλύπτει και τα constraints των A4 (Room blocked slot) και
 * A5 (Required same-year daily gaps — gap minimization).
 */
class CeidConstraintProviderTest {

    private final ConstraintVerifier<CeidConstraintProvider, CeidTimetable> verifier =
            ConstraintVerifier.build(new CeidConstraintProvider(), CeidTimetable.class, Lesson.class);

    // ---------- fixtures ----------

    private static final SolverRoom GAMMA = new SolverRoom(1L, "Γ", 244, "AMPHITHEATER");
    private static final SolverRoom BETA  = new SolverRoom(2L, "Β", 238, "AMPHITHEATER");
    private static final SolverRoom D1    = new SolverRoom(3L, "Δ1", 110, "CLASSROOM");
    private static final SolverRoom HL3   = new SolverRoom(4L, "ΗΛ3", 50, "LAB");

    private static SolverTimeSlot slot(long id, String day, int hour) {
        return new SolverTimeSlot(id, day, hour);
    }

    private static long nextId = 1;

    private static Lesson lesson(long courseId, String code, int year, String courseType,
                                 String assignType, int students,
                                 SolverTimeSlot ts, SolverRoom room, String... teachers) {
        Lesson l = new Lesson(nextId++, courseId, code, "Μάθημα " + code,
                year, courseType, assignType, students, "FALL", year * 2 - 1);
        l.setTimeSlot(ts);
        l.setRoom(room);
        l.setTeacherKeys(Set.of(teachers));
        return l;
    }

    @AfterEach
    void resetStaticRegistries() {
        TeacherAvailabilityConstraints.BLOCKED_SLOTS = Map.of();
        TeacherAvailabilityConstraints.PREFERRED_SLOTS = Map.of();
        RoomAvailabilityConstraints.BLOCKED_SLOTS = Map.of();
    }

    // ---------- HARD ----------

    @Test
    void roomConflict_penalizesTwoLessonsSameSlotSameRoom() {
        SolverTimeSlot ts = slot(10, "MONDAY", 9);
        Lesson a = lesson(1, "C1", 2, "REQUIRED", "LECTURE", 100, ts, D1, "T1|A");
        Lesson b = lesson(2, "C2", 3, "ELECTIVE", "LECTURE", 50, ts, D1, "T2|B");
        verifier.verifyThat(CeidConstraintProvider::roomConflict)
                .given(a, b).penalizesBy(1);
    }

    @Test
    void roomConflict_noPenaltyDifferentRooms() {
        SolverTimeSlot ts = slot(10, "MONDAY", 9);
        Lesson a = lesson(1, "C1", 2, "REQUIRED", "LECTURE", 100, ts, D1, "T1|A");
        Lesson b = lesson(2, "C2", 3, "ELECTIVE", "LECTURE", 50, ts, BETA, "T2|B");
        verifier.verifyThat(CeidConstraintProvider::roomConflict)
                .given(a, b).penalizesBy(0);
    }

    @Test
    void teacherConflict_penalizesSharedTeacherSameSlot() {
        SolverTimeSlot ts = slot(10, "MONDAY", 9);
        Lesson a = lesson(1, "C1", 2, "REQUIRED", "LECTURE", 100, ts, D1, "ΒΛΑΧΟΣ|Κ");
        Lesson b = lesson(2, "C2", 3, "ELECTIVE", "LECTURE", 50, ts, BETA, "ΒΛΑΧΟΣ|Κ");
        verifier.verifyThat(CeidConstraintProvider::teacherConflict)
                .given(a, b).penalizesBy(1);
    }

    @Test
    void teacherConflict_sameCourseNotPenalized() {
        // Δύο ώρες του ΙΔΙΟΥ μαθήματος δεν μετρούν ως teacher conflict
        // (καλύπτονται από το sameCourseConflict).
        SolverTimeSlot ts = slot(10, "MONDAY", 9);
        Lesson a = lesson(1, "C1", 2, "REQUIRED", "LECTURE", 100, ts, D1, "ΒΛΑΧΟΣ|Κ");
        Lesson b = lesson(1, "C1", 2, "REQUIRED", "TUTORIAL", 100, ts, BETA, "ΒΛΑΧΟΣ|Κ");
        verifier.verifyThat(CeidConstraintProvider::teacherConflict)
                .given(a, b).penalizesBy(0);
    }

    @Test
    void requiredSameYearConflict_singleMatch() {
        SolverTimeSlot ts = slot(10, "TUESDAY", 11);
        Lesson a = lesson(1, "C1", 2, "REQUIRED", "LECTURE", 100, ts, BETA, "T1|A");
        Lesson b = lesson(2, "C2", 2, "REQUIRED", "LECTURE", 100, ts, GAMMA, "T2|B");
        verifier.verifyThat(CeidConstraintProvider::requiredSameYearConflict)
                .given(a, b).penalizesBy(1); // 1 match (βάρος constraint = ofHard(5))
    }

    @Test
    void labInLabRoom_penalizesLabInClassroom() {
        Lesson lab = lesson(1, "C1", 2, "REQUIRED", "LAB", 40, slot(10, "MONDAY", 9), D1, "T1|A");
        verifier.verifyThat(CeidConstraintProvider::labInLabRoom)
                .given(lab).penalizesBy(1);
    }

    @Test
    void firstYearOnlyGamma_lectureOutsideGammaPenalized() {
        Lesson l = lesson(1, "C1", 1, "REQUIRED", "LECTURE", 200, slot(10, "MONDAY", 9), BETA, "T1|A");
        verifier.verifyThat(CeidConstraintProvider::firstYearOnlyGamma)
                .given(l).penalizesBy(1);
    }

    @Test
    void firstYearOnlyGamma_labExempt() {
        Lesson l = lesson(1, "C1", 1, "REQUIRED", "LAB", 40, slot(10, "MONDAY", 9), HL3, "T1|A");
        verifier.verifyThat(CeidConstraintProvider::firstYearOnlyGamma)
                .given(l).penalizesBy(0);
    }

    @Test
    void requiredOnlyBorG_electiveAllowedAnywhere() {
        Lesson l = lesson(1, "C1", 4, "ELECTIVE", "LECTURE", 60, slot(10, "MONDAY", 9), D1, "T1|A");
        verifier.verifyThat(CeidConstraintProvider::requiredOnlyBorG)
                .given(l).penalizesBy(0);
    }

    @Test
    void dailyLectureLimit_sevenLecturesPenalizedByOne() {
        Lesson[] lessons = new Lesson[7];
        for (int i = 0; i < 7; i++) {
            lessons[i] = lesson(100 + i, "C" + i, 2, "REQUIRED", "LECTURE", 100,
                    slot(20 + i, "MONDAY", 9 + i), BETA, "T" + i + "|X");
        }
        verifier.verifyThat(CeidConstraintProvider::dailyLectureLimit)
                .given((Object[]) lessons).penalizesBy(1); // 7 - 6 = 1
    }

    @Test
    void lunchBreak_allThreeLunchHoursOccupied_penalized() {
        Lesson a = lesson(1, "C1", 2, "REQUIRED", "LECTURE", 100, slot(20, "MONDAY", 12), BETA, "T1|A");
        Lesson b = lesson(2, "C2", 2, "REQUIRED", "LECTURE", 100, slot(21, "MONDAY", 13), BETA, "T2|B");
        Lesson c = lesson(3, "C3", 2, "REQUIRED", "LECTURE", 100, slot(22, "MONDAY", 14), BETA, "T3|C");
        verifier.verifyThat(CeidConstraintProvider::lunchBreakFirstThreeYears)
                .given(a, b, c).penalizesBy(1);
    }

    @Test
    void lunchBreak_oneFreeHour_notPenalized() {
        Lesson a = lesson(1, "C1", 2, "REQUIRED", "LECTURE", 100, slot(20, "MONDAY", 12), BETA, "T1|A");
        Lesson b = lesson(2, "C2", 2, "REQUIRED", "LECTURE", 100, slot(21, "MONDAY", 13), BETA, "T2|B");
        verifier.verifyThat(CeidConstraintProvider::lunchBreakFirstThreeYears)
                .given(a, b).penalizesBy(0);
    }

    @Test
    void teacherBlockedSlot_usesRegistry() {
        TeacherAvailabilityConstraints.BLOCKED_SLOTS =
                Map.of("ΒΛΑΧΟΣ|Κ", Set.of("WEDNESDAY_9"));
        Lesson l = lesson(1, "C1", 2, "REQUIRED", "LECTURE", 100,
                slot(30, "WEDNESDAY", 9), BETA, "ΒΛΑΧΟΣ|Κ");
        verifier.verifyThat(CeidConstraintProvider::teacherBlockedSlot)
                .given(l).penalizesBy(1);
    }

    // ---------- A4: Room blocked slot ----------

    @Test
    void roomBlockedSlot_penalizesLessonInReservedRoomHour() {
        RoomAvailabilityConstraints.BLOCKED_SLOTS =
                Map.of("Δ1", Set.of("WEDNESDAY_12"));
        Lesson l = lesson(1, "C1", 4, "ELECTIVE", "LECTURE", 60,
                slot(30, "WEDNESDAY", 12), D1, "T1|A");
        verifier.verifyThat(CeidConstraintProvider::roomBlockedSlot)
                .given(l).penalizesBy(1);
    }

    @Test
    void roomBlockedSlot_otherRoomUnaffected() {
        RoomAvailabilityConstraints.BLOCKED_SLOTS =
                Map.of("Δ1", Set.of("WEDNESDAY_12"));
        Lesson l = lesson(1, "C1", 4, "ELECTIVE", "LECTURE", 60,
                slot(30, "WEDNESDAY", 12), BETA, "T1|A");
        verifier.verifyThat(CeidConstraintProvider::roomBlockedSlot)
                .given(l).penalizesBy(0);
    }

    // ---------- A5: Required same-year daily gaps (gap minimization) ----------

    @Test
    void dailyGapPenalty_consecutiveHours_zeroPenalty() {
        Lesson a = lesson(1, "C1", 2, "REQUIRED", "LECTURE", 100, slot(20, "MONDAY", 9), BETA, "T1|A");
        Lesson b = lesson(2, "C2", 2, "REQUIRED", "LECTURE", 100, slot(21, "MONDAY", 10), BETA, "T2|B");
        verifier.verifyThat(CeidConstraintProvider::requiredSameYearGaps)
                .given(a, b).penalizesBy(0);
    }

    @Test
    void dailyGapPenalty_twoHourGap_penalizedByFour() {
        // 9:00 και 12:00 → κενό 2 ωρών (10:00, 11:00) → 2 * 2 = 4
        Lesson a = lesson(1, "C1", 2, "REQUIRED", "LECTURE", 100, slot(20, "MONDAY", 9), BETA, "T1|A");
        Lesson b = lesson(2, "C2", 2, "REQUIRED", "LECTURE", 100, slot(23, "MONDAY", 12), BETA, "T2|B");
        verifier.verifyThat(CeidConstraintProvider::requiredSameYearGaps)
                .given(a, b).penalizesBy(4);
    }

    @Test
    void dailyGapPenalty_threeHourGap_extraPenaltyBeyondTwoHours() {
        // 9:00 και 13:00 → κενό 3 ωρών → 2*3 + 4*(3-2) = 10
        Lesson a = lesson(1, "C1", 2, "REQUIRED", "LECTURE", 100, slot(20, "MONDAY", 9), BETA, "T1|A");
        Lesson b = lesson(2, "C2", 2, "REQUIRED", "LECTURE", 100, slot(24, "MONDAY", 13), BETA, "T2|B");
        verifier.verifyThat(CeidConstraintProvider::requiredSameYearGaps)
                .given(a, b).penalizesBy(10);
    }

    @Test
    void dailyGapPenalty_labsExcludedFromGapCalculation() {
        // Διάλεξη 9:00, LAB 11:00, διάλεξη 13:00: το LAB ΔΕΝ "γεμίζει" το κενό
        // αλλά ούτε συμμετέχει — κενό διαλέξεων 9→13 = 3 ώρες → 10.
        Lesson a = lesson(1, "C1", 2, "REQUIRED", "LECTURE", 100, slot(20, "MONDAY", 9), BETA, "T1|A");
        Lesson lab = lesson(2, "C2", 2, "REQUIRED", "LAB", 40, slot(22, "MONDAY", 11), HL3, "T2|B");
        Lesson b = lesson(3, "C3", 2, "REQUIRED", "LECTURE", 100, slot(24, "MONDAY", 13), BETA, "T3|C");
        verifier.verifyThat(CeidConstraintProvider::requiredSameYearGaps)
                .given(a, lab, b).penalizesBy(10);
    }

    @Test
    void dailyGapPenalty_differentYearsIndependent() {
        // Κενό "ανάμεσα" σε μαθήματα διαφορετικών ετών δεν είναι κενό.
        Lesson a = lesson(1, "C1", 2, "REQUIRED", "LECTURE", 100, slot(20, "MONDAY", 9), BETA, "T1|A");
        Lesson b = lesson(2, "C2", 3, "REQUIRED", "LECTURE", 100, slot(23, "MONDAY", 12), GAMMA, "T2|B");
        verifier.verifyThat(CeidConstraintProvider::requiredSameYearGaps)
                .given(a, b).penalizesBy(0);
    }

    @Test
    void dailyGapPenalty_helperMath() {
        // Άμεσος έλεγχος της dailyGapPenalty: δύο κενά (1 ώρα + 3 ώρες)
        // 2*1 + (2*3 + 4*1) = 2 + 10 = 12
        List<Lesson> day = List.of(
                lesson(1, "C1", 2, "REQUIRED", "LECTURE", 100, slot(20, "MONDAY", 9), BETA, "T1|A"),
                lesson(2, "C2", 2, "REQUIRED", "LECTURE", 100, slot(22, "MONDAY", 11), BETA, "T2|B"),
                lesson(3, "C3", 2, "REQUIRED", "LECTURE", 100, slot(26, "MONDAY", 15), BETA, "T3|C"));
        assertEquals(12, CeidConstraintProvider.dailyGapPenalty(day));
    }

    // ---------- SOFT ----------

    @Test
    void roomCapacityMatch_penalizesByOverflow() {
        Lesson l = lesson(1, "C1", 1, "REQUIRED", "LECTURE", 300, slot(10, "MONDAY", 9), GAMMA, "T1|A");
        verifier.verifyThat(CeidConstraintProvider::roomCapacityMatch)
                .given(l).penalizesBy(300 - 244);
    }

    @Test
    void preferNormalHours_eveningPenalizedByTwo() {
        Lesson l = lesson(1, "C1", 4, "ELECTIVE", "LECTURE", 60, slot(10, "FRIDAY", 19), D1, "T1|A");
        verifier.verifyThat(CeidConstraintProvider::preferNormalHours)
                .given(l).penalizesBy(2);
    }

    @Test
    void directionGroupA_twoK1CoursesSameSlot_penalizedByFive() {
        SolverTimeSlot ts = slot(10, "MONDAY", 15);
        Lesson a = lesson(1, "CEID_NE5057", 4, "ELECTIVE", "LECTURE", 60, ts, D1, "T1|A");
        Lesson b = lesson(2, "CEID_NE4168", 4, "ELECTIVE", "LECTURE", 60, ts, BETA, "T2|B");
        verifier.verifyThat(CeidConstraintProvider::directionGroupAConflict)
                .given(a, b).penalizesBy(5);
    }

    @Test
    void teacherPreferredSlot_coTeacherWithoutPreferencesStillPenalized() {
        TeacherAvailabilityConstraints.PREFERRED_SLOTS =
                Map.of("ΚΑΚΛΑΜΑΝΗΣ|Χ", Set.of("FRIDAY_19", "FRIDAY_20"));
        Lesson l = lesson(1, "C1", 4, "ELECTIVE", "LECTURE", 60,
                slot(10, "MONDAY", 15), D1, "ΚΑΚΛΑΜΑΝΗΣ|Χ", "ΑΛΛΟΣ|Α");
        verifier.verifyThat(CeidConstraintProvider::teacherPreferredSlot)
                .given(l).penalizesBy(3);
    }
}
