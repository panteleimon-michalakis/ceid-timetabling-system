package gr.upatras.ceid.timetable.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests για το {@link ExamDateRules} — καθαρά, χωρίς DB/Spring.
 *
 * Καλύπτει το κοινό φίλτρο «αργία ή custom εξαίρεση» που εφαρμόζεται τόσο στη
 * δημιουργία exam slots όσο και στο value range της τοποθέτησης. Το ίδιο το
 * computus των αργιών ελέγχεται στο {@link GreekHolidaysTest}.
 */
class ExamDateRulesTest {

    @Test
    void excludedExamDate_trueForPublicHoliday() {
        // Χριστούγεννα — αργία ανεξάρτητα από custom εξαιρέσεις.
        assertTrue(ExamDateRules.isExcludedExamDate(LocalDate.of(2026, 12, 25), Set.of()));
    }

    @Test
    void excludedExamDate_trueForCustomExcludedDate() {
        LocalDate custom = LocalDate.of(2026, 1, 20); // κανονική εργάσιμη, στη λίστα
        assertFalse(GreekHolidays.isPublicHoliday(custom)); // sanity: όχι αργία
        assertTrue(ExamDateRules.isExcludedExamDate(custom, Set.of(custom)));
    }

    @Test
    void excludedExamDate_falseForOrdinaryWorkingDayNotExcluded() {
        LocalDate ordinary = LocalDate.of(2026, 1, 20);
        assertFalse(ExamDateRules.isExcludedExamDate(ordinary, Set.of(LocalDate.of(2026, 1, 21))));
    }

    @Test
    void excludedExamDate_falseForNullDate() {
        assertFalse(ExamDateRules.isExcludedExamDate(null, Set.of(LocalDate.of(2026, 1, 1))));
    }

    @Test
    void excludedExamDate_nullOrEmptyExcluded_fallsBackToHolidayOnly() {
        // Με null/κενές εξαιρέσεις: μόνο η αργία μετράει.
        assertTrue(ExamDateRules.isExcludedExamDate(LocalDate.of(2026, 1, 1), null));
        assertFalse(ExamDateRules.isExcludedExamDate(LocalDate.of(2026, 1, 20), null));
        assertFalse(ExamDateRules.isExcludedExamDate(LocalDate.of(2026, 1, 20), Set.of()));
    }
}
