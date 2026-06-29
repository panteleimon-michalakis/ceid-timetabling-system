package gr.upatras.ceid.timetable.util;

import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.Timetable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BL-10 — pure-unit απόδειξη ότι το {@link CourseRelevance#isSchedulable}:
 * (α) έχει σωστό null-handling των active/visible flags, και
 * (β) είναι ΤΑΥΤΟΣΗΜΟ σε αποτέλεσμα με τη schedulability semantic του solver
 *     (SolverService.isCourseRelevant): active-guard ∧ visible-guard ∧ isRelevant.
 *
 * Καθαρά in-memory (κανένα DB/Spring) → καλύπτει και την null περίπτωση που το DB
 * schema (NOT NULL σε active/visible) δεν επιτρέπει να αποθηκευτεί, αλλά ο solver
 * την χειρίζεται defensively. Το exhaustive grid κλειδώνει την ισοδυναμία ώστε
 * μελλοντική απόκλιση των δύο predicates να γίνεται κόκκινο test.
 */
class CourseRelevanceSchedulabilityTest {

    private static Course course(Boolean active, Boolean visible, Course.SemesterType sem) {
        return Course.builder().active(active).visibleInTimetable(visible).semesterType(sem).build();
    }

    private static Timetable tt(Timetable.SemesterType sem) {
        return Timetable.builder().semesterType(sem).build();
    }

    /**
     * Oracle: ΑΚΡΙΒΗΣ αναπαραγωγή του σώματος του SolverService.isCourseRelevant
     * τη στιγμή του BL-10. Κλειδώνει το «frozen scope == ό,τι επιχειρεί ο solver».
     */
    private static boolean solverRelevanceOracle(Course c, Timetable t) {
        if (c == null || t == null) return false;
        if (c.getActive() != null && !c.getActive()) return false;
        if (c.getVisibleInTimetable() != null && !c.getVisibleInTimetable()) return false;
        Timetable.SemesterType ttSem = t.getSemesterType();
        Course.SemesterType cSem = c.getSemesterType();
        if (ttSem == null || cSem == null) return true;
        if (cSem == Course.SemesterType.BOTH) return true;
        if (ttSem == Timetable.SemesterType.SEPTEMBER) return true;
        return ttSem.name().equals(cSem.name());
    }

    // ---- 1. null-handling: active=null & visible=null → schedulable (≡ isRelevant) ----
    @Test
    void nullFlags_areSchedulable() {
        Course c = course(null, null, Course.SemesterType.FALL);
        Timetable t = tt(Timetable.SemesterType.FALL);
        assertTrue(CourseRelevance.isSchedulable(c, t), "null active/visible → schedulable");
        assertEquals(CourseRelevance.isRelevant(c, t), CourseRelevance.isSchedulable(c, t),
                "με null flags, isSchedulable ≡ isRelevant");
    }

    // ---- 2. active=false → ποτέ schedulable (ακόμη κι αν semester-relevant) ----
    @Test
    void inactive_isNeverSchedulable() {
        Course c = course(false, true, Course.SemesterType.FALL);
        Timetable t = tt(Timetable.SemesterType.FALL);
        assertTrue(CourseRelevance.isRelevant(c, t), "precondition: semester-relevant");
        assertFalse(CourseRelevance.isSchedulable(c, t), "active=false → μη-schedulable");
    }

    // ---- 3. visible=false → ποτέ schedulable ----
    @Test
    void invisible_isNeverSchedulable() {
        Course c = course(true, false, Course.SemesterType.FALL);
        Timetable t = tt(Timetable.SemesterType.FALL);
        assertTrue(CourseRelevance.isRelevant(c, t), "precondition: semester-relevant");
        assertFalse(CourseRelevance.isSchedulable(c, t), "visibleInTimetable=false → μη-schedulable");
    }

    // ---- 4. null course/timetable → false ----
    @Test
    void nullArgs_areNotSchedulable() {
        assertFalse(CourseRelevance.isSchedulable(null, tt(Timetable.SemesterType.FALL)));
        assertFalse(CourseRelevance.isSchedulable(course(true, true, Course.SemesterType.FALL), null));
    }

    // ---- 5. ΤΑΥΤΟΣΗΜΟ με τη solver semantic σε ΟΛΑ τα combos (Gate A ως regression test) ----
    @Test
    void schedulable_matchesSolverSemantics_acrossAllCombos() {
        Boolean[] flags = { null, Boolean.TRUE, Boolean.FALSE };
        Course.SemesterType[] cSems = { null, Course.SemesterType.FALL, Course.SemesterType.SPRING, Course.SemesterType.BOTH };
        Timetable.SemesterType[] tSems = { null, Timetable.SemesterType.FALL, Timetable.SemesterType.SPRING, Timetable.SemesterType.SEPTEMBER };

        for (Boolean active : flags) {
            for (Boolean visible : flags) {
                for (Course.SemesterType cs : cSems) {
                    for (Timetable.SemesterType ts : tSems) {
                        Course c = course(active, visible, cs);
                        Timetable t = tt(ts);
                        assertEquals(solverRelevanceOracle(c, t), CourseRelevance.isSchedulable(c, t),
                                () -> "mismatch @ active=" + active + " visible=" + visible
                                        + " cSem=" + cs + " ttSem=" + ts);
                    }
                }
            }
        }
    }
}
