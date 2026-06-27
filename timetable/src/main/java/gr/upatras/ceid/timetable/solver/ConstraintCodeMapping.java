package gr.upatras.ceid.timetable.solver;

import java.util.Map;
import java.util.Optional;

/**
 * Φ-SV2a: συμβόλαιο solver constraintName -> report error code. Ενιαία, maintained
 * πηγή που θα καταναλώσει η Φάση 2b ώστε το validateTimetableReport να παράγει τα
 * hard errors από το score-explanation αντί για διπλο-υλοποιημένους ελέγχους.
 *
 * ΣΥΝΤΗΡΗΣΗ: όταν προστίθεται ΝΕΟ HARD constraint σε Ceid/ExamConstraintProvider,
 * πρόσθεσε εδώ name->code. Το completeness test (ValidationEngineParityTest) σπάει
 * αν λείπει mapping για γνωστό hard constraint.
 *
 * Σημείωση: μόνο HARD constraints χαρτογραφούνται (γίνονται report errors)· τα SOFT
 * (π.χ. "Direction Group A conflict") ΔΕΝ είναι errors — τα φιλτράρει ήδη το
 * SolverService.extractHardViolations με hardScore() < 0. Δύο constraintNames
 * μοιράζονται σκόπιμα κοινό code (weekly+exam teacher conflict -> TEACHER_CONFLICT,
 * weekly+exam room blocked -> ROOM_BLOCKED): τα keys (ονόματα) παραμένουν distinct.
 */
public final class ConstraintCodeMapping {

    private ConstraintCodeMapping() {}

    /** constraintName (όπως στο asConstraint(...)) -> report code. */
    public static final Map<String, String> HARD_NAME_TO_CODE = Map.ofEntries(
            // WEEKLY
            Map.entry("Room conflict", "ROOM_CONFLICT"),
            Map.entry("Teacher conflict", "TEACHER_CONFLICT"),
            Map.entry("Same course conflict", "SAME_COURSE_SAME_SLOT"),
            Map.entry("Required same-year conflict", "REQUIRED_YEAR_CONFLICT"),
            Map.entry("Lab must be in LAB room", "LAB_ROOM_REQUIRED"),
            Map.entry("First year only in room G", "FIRST_YEAR_ROOM"),
            Map.entry("Required courses only in B or G", "REQUIRED_ROOM"),
            Map.entry("Daily lecture limit for required courses", "DAILY_LECTURE_LIMIT"),
            Map.entry("Lunch break required for first three years", "LUNCH_BREAK_REQUIRED"),
            Map.entry("Teacher blocked slot", "TEACHER_BLOCKED"),
            Map.entry("Room blocked slot", "ROOM_BLOCKED"),
            // EXAM
            Map.entry("Exam teacher conflict", "TEACHER_CONFLICT"),
            Map.entry("Required same-year exams on same day", "REQUIRED_YEAR_EXAM_SAME_DATE"),
            Map.entry("Exam room blocked slot", "ROOM_BLOCKED")
    );

    /** report code για ένα constraintName, αν υπάρχει mapping. */
    public static Optional<String> codeFor(String constraintName) {
        return Optional.ofNullable(HARD_NAME_TO_CODE.get(constraintName));
    }
}
