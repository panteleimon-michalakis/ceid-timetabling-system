package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.Course;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests για τη λογική αναφοράς συγκρούσεων του validation report.
 *
 * Εστιάζει στον guard {@link TimetableController#isSameCourseId} που αποτρέπει
 * τη διπλομέτρηση: όταν το ΙΔΙΟ μάθημα τοποθετηθεί δύο φορές στο ίδιο slot,
 * αναφέρεται ως SAME_COURSE_SAME_SLOT και ΟΧΙ (επιπλέον) ως TEACHER_CONFLICT.
 * Αντικατοπτρίζει τον guard {@code !a.getCourseId().equals(b.getCourseId())}
 * του teacherConflict στον CeidConstraintProvider.
 */
class TimetableControllerValidationTest {

    private static Course course(Long id) {
        return Course.builder().id(id).code("C" + id).name("Μάθημα " + id).build();
    }

    @Test
    void isSameCourseId_trueForSameId_suppressesTeacherConflict() {
        // Ίδιο μάθημα δύο φορές → guard ενεργός → ΟΧΙ TEACHER_CONFLICT (μόνο SAME_COURSE_SAME_SLOT)
        assertTrue(TimetableController.isSameCourseId(course(31L), course(31L)));
    }

    @Test
    void isSameCourseId_falseForDifferentIds_allowsTeacherConflict() {
        // Διαφορετικά μαθήματα → guard ανενεργός → επιτρέπεται έλεγχος TEACHER_CONFLICT
        assertFalse(TimetableController.isSameCourseId(course(68L), course(71L)));
    }

    @Test
    void isSameCourseId_falseWhenAnyIdNull() {
        assertFalse(TimetableController.isSameCourseId(course(null), course(31L)));
        assertFalse(TimetableController.isSameCourseId(course(31L), course(null)));
        assertFalse(TimetableController.isSameCourseId(course(null), course(null)));
    }

    @Test
    void isSameCourseId_falseWhenAnyCourseNull() {
        assertFalse(TimetableController.isSameCourseId(null, course(31L)));
        assertFalse(TimetableController.isSameCourseId(course(31L), null));
        assertFalse(TimetableController.isSameCourseId(null, null));
    }

    // ---------- parseExcludedDates (custom εξαιρέσεις admin) ----------

    @Test
    void parseExcludedDates_parsesCsvOfIsoDates() {
        assertEquals(
                List.of(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 3)),
                TimetableController.parseExcludedDates("2026-01-15,2026-02-03"));
    }

    @Test
    void parseExcludedDates_trimsAndSkipsBlankAndInvalid() {
        // Κενά τμήματα και μη έγκυρες ημερομηνίες αγνοούνται· τα υπόλοιπα παραμένουν.
        assertEquals(
                List.of(LocalDate.of(2026, 1, 15)),
                TimetableController.parseExcludedDates(" 2026-01-15 , , not-a-date "));
    }

    @Test
    void parseExcludedDates_emptyForNullOrBlank() {
        assertTrue(TimetableController.parseExcludedDates(null).isEmpty());
        assertTrue(TimetableController.parseExcludedDates("   ").isEmpty());
    }
}
