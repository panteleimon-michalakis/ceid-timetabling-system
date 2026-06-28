package gr.upatras.ceid.timetable.validation;

import gr.upatras.ceid.timetable.solver.HardViolation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Φ-SV2b-i: ντετερμινιστικά (no-DB) tests του {@link HardViolationTranslator}.
 * Synthetic HardViolation + in-memory lookup (assignmentId -> AssignmentView).
 * Ελέγχει code / referenceId / assignmentIds / message ανά περίπτωση,
 * ΣΥΜΠΕΡΙΛΑΜΒΑΝΟΜΕΝΩΝ των 2 NEW (TEACHER_BLOCKED, ROOM_BLOCKED).
 */
class HardViolationTranslatorTest {

    // ---------- in-memory lookup ----------

    private static AssignmentView view(long id, String course, String room,
                                       String day, Integer hour, String... teachers) {
        return new AssignmentView(id, course, 2, room, day, hour, List.of(teachers), "LECTURE");
    }

    private static final Map<Long, AssignmentView> LOOKUP = new LinkedHashMap<>();
    static {
        LOOKUP.put(101L, view(101, "Διακριτά Μαθηματικά", "Δ1", "MONDAY", 9, "Παπαδόπουλος Α."));
        LOOKUP.put(102L, view(102, "Φυσική", "Δ1", "MONDAY", 9, "Γεωργίου Β."));
        LOOKUP.put(201L, view(201, "Αλγόριθμοι", "Β", "TUESDAY", 10, "Κοινός Κ.", "Άλλος Α."));
        LOOKUP.put(202L, view(202, "Δομές Δεδομένων", "Γ", "TUESDAY", 10, "Κοινός Κ.", "Διαφορετικός Δ."));
        LOOKUP.put(211L, view(211, "Μ1", "Β", "TUESDAY", 11, "Πρώτος Π."));
        LOOKUP.put(212L, view(212, "Μ2", "Γ", "TUESDAY", 11, "Δεύτερος Δ."));
        LOOKUP.put(301L, view(301, "Εργαστήριο Δικτύων", "Δ1", "MONDAY", 9, "Τεχνικός Τ."));
        LOOKUP.put(601L, view(601, "Βάσεις Δεδομένων", "Δ1", "WEDNESDAY", 9, "Βλάχος Κ."));
        LOOKUP.put(701L, view(701, "Δίκτυα Υπολογιστών", "Δ1", "WEDNESDAY", 12, "Σκλάβος Ν."));
    }

    private static List<Map<String, Object>> translate(HardViolation... vs) {
        return HardViolationTranslator.translate(List.of(vs), LOOKUP::get);
    }

    private static Map<String, Object> single(List<Map<String, Object>> issues) {
        assertEquals(1, issues.size(), "ακριβώς 1 issue");
        return issues.get(0);
    }

    // ---------- 1) ROOM_CONFLICT ----------

    @Test
    void roomConflict_codeRefIdsMessage() {
        // ids σκόπιμα μη-ταξινομημένα → ελέγχει sort + min.
        Map<String, Object> issue = single(translate(
                new HardViolation("Room conflict", List.of(102L, 101L), -1)));
        assertEquals("ROOM_CONFLICT", issue.get("code"));
        assertEquals(101L, issue.get("referenceId"));
        assertEquals(List.of(101L, 102L), issue.get("assignmentIds"));
        assertEquals("Η αίθουσα Δ1 έχει δύο μαθήματα την ίδια ώρα: Διακριτά Μαθηματικά και Φυσική.",
                issue.get("message"));
    }

    // ---------- 2) TEACHER_CONFLICT (τομή + fallback) ----------

    @Test
    void teacherConflict_intersectionNames() {
        Map<String, Object> issue = single(translate(
                new HardViolation("Teacher conflict", List.of(201L, 202L), -1)));
        assertEquals("TEACHER_CONFLICT", issue.get("code"));
        assertEquals(201L, issue.get("referenceId"));
        assertEquals("Σύγκρουση διδάσκοντα: Κοινός Κ. έχει δύο μαθήματα την ίδια ώρα: "
                + "Αλγόριθμοι και Δομές Δεδομένων.", issue.get("message"));
    }

    @Test
    void teacherConflict_emptyIntersection_fallback() {
        // D3: κενή τομή teacherNames → «κοινός διδάσκων».
        Map<String, Object> issue = single(translate(
                new HardViolation("Teacher conflict", List.of(211L, 212L), -1)));
        assertEquals("Σύγκρουση διδάσκοντα: κοινός διδάσκων έχει δύο μαθήματα την ίδια ώρα: Μ1 και Μ2.",
                issue.get("message"));
    }

    // ---------- 3) LAB_ROOM_REQUIRED ----------

    @Test
    void labRoomRequired_singleId() {
        Map<String, Object> issue = single(translate(
                new HardViolation("Lab must be in LAB room", List.of(301L), -1)));
        assertEquals("LAB_ROOM_REQUIRED", issue.get("code"));
        assertEquals(301L, issue.get("referenceId"));
        assertEquals(List.of(301L), issue.get("assignmentIds"));
        assertEquals("Το εργαστήριο Εργαστήριο Δικτύων πρέπει να βρίσκεται σε εργαστηριακή αίθουσα.",
                issue.get("message"));
    }

    // ---------- 4) DAILY_LECTURE_LIMIT (aggregate — rich μήνυμα από group-key) ----------

    @Test
    void dailyLectureLimit_aggregate_richMessageFromContextFacts() {
        // Production: ids κενά (group-key indictment)· το group-key έρχεται ως contextFacts.
        Map<String, Object> issue = single(translate(new HardViolation(
                "Daily lecture limit for required courses", List.of(), -1, List.of(2, "MONDAY", 7))));
        assertEquals("DAILY_LECTURE_LIMIT", issue.get("code"));
        assertNull(issue.get("referenceId"));
        assertEquals(List.of(), issue.get("assignmentIds"));
        assertEquals("Το 2ο έτος έχει 7 ώρες θεωρίας την ημέρα Δευτέρα. Το μέγιστο επιτρεπτό είναι 6.",
                issue.get("message"));
    }

    // ---------- 5) LUNCH_BREAK_REQUIRED (aggregate — rich μήνυμα από group-key) ----------

    @Test
    void lunchBreak_aggregate_richMessageFromContextFacts() {
        Map<String, Object> issue = single(translate(new HardViolation(
                "Lunch break required for first three years", List.of(), -3, List.of(1, "FRIDAY", 3))));
        assertEquals("LUNCH_BREAK_REQUIRED", issue.get("code"));
        assertNull(issue.get("referenceId"));
        assertEquals(List.of(), issue.get("assignmentIds"));
        assertEquals("Το 1ο έτος δεν έχει ελεύθερη ώρα για φαγητό μεταξύ 12:00-15:00 την ημέρα Παρασκευή.",
                issue.get("message"));
    }

    // ---------- 5b) aggregate με λάθος-shape contextFacts → generic fallback ----------

    @Test
    void aggregate_wrongShapeContextFacts_genericFallback() {
        // Κενά contextFacts (3-args constructor) → generic fallback (μη-crash). Επιπλέον τα ids
        // μη-κενά → επιβεβαιώνει ότι aggregate code ΕΠΙΒΑΛΛΕΙ referenceId=null ΑΝΕΞΑΡΤΗΤΩΣ ids.
        Map<String, Object> issue = single(translate(new HardViolation(
                "Daily lecture limit for required courses", List.of(105L, 103L, 104L), -1)));
        assertEquals("DAILY_LECTURE_LIMIT", issue.get("code"));
        assertNull(issue.get("referenceId"));
        assertEquals(List.of(103L, 104L, 105L), issue.get("assignmentIds"));
        assertEquals("Υπέρβαση του ημερήσιου ορίου των 6 ωρών θεωρίας για υποχρεωτικά μαθήματα ίδιου έτους.",
                issue.get("message"));
    }

    // ---------- 6) TEACHER_BLOCKED (NEW) ----------

    @Test
    void teacherBlocked_new_singleId() {
        Map<String, Object> issue = single(translate(
                new HardViolation("Teacher blocked slot", List.of(601L), -10)));
        assertEquals("TEACHER_BLOCKED", issue.get("code"));
        assertEquals(601L, issue.get("referenceId"));
        assertEquals("Ο διδάσκων του μαθήματος Βάσεις Δεδομένων είναι δεσμευμένος τη συγκεκριμένη ώρα "
                + "(Τετάρτη 9:00).", issue.get("message"));
    }

    // ---------- 7) ROOM_BLOCKED (NEW) ----------

    @Test
    void roomBlocked_new_singleId() {
        Map<String, Object> issue = single(translate(
                new HardViolation("Room blocked slot", List.of(701L), -10)));
        assertEquals("ROOM_BLOCKED", issue.get("code"));
        assertEquals(701L, issue.get("referenceId"));
        assertEquals("Η αίθουσα Δ1 είναι δεσμευμένη τη συγκεκριμένη ώρα: Δίκτυα Υπολογιστών (Τετάρτη 12:00).",
                issue.get("message"));
    }

    // ---------- 8) unknown → skip ----------

    @Test
    void unknownConstraint_skipped() {
        assertTrue(translate(new HardViolation("Totally unknown constraint", List.of(999L), -1)).isEmpty(),
                "άγνωστο constraintName → κανένα issue");
    }

    @Test
    void unknownSkipped_knownKept_inSameBatch() {
        List<Map<String, Object>> issues = translate(
                new HardViolation("Totally unknown constraint", List.of(999L), -1),
                new HardViolation("Room conflict", List.of(101L, 102L), -1));
        assertEquals(1, issues.size(), "μόνο το γνωστό περνά");
        assertEquals("ROOM_CONFLICT", issues.get(0).get("code"));
    }

    // ---------- defensive ----------

    @Test
    void missingView_usesFallbackPlaceholders() {
        // lookup χωρίς τα ids → ασφαλή placeholders, χωρίς NPE.
        Map<String, Object> issue = single(translate(
                new HardViolation("Room conflict", List.of(901L, 902L), -1)));
        assertEquals("Η αίθουσα (άγνωστη αίθουσα) έχει δύο μαθήματα την ίδια ώρα: "
                + "(άγνωστο μάθημα) και (άγνωστο μάθημα).", issue.get("message"));
    }

    @Test
    void nullViolations_emptyResult() {
        assertTrue(HardViolationTranslator.translate(null, LOOKUP::get).isEmpty());
    }
}
