package gr.upatras.ceid.timetable.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests για το {@link GreekHolidays} — καθαρά, χωρίς DB/Spring.
 *
 * Καλύπτει: τον υπολογισμό του Ορθόδοξου Πάσχα (computus) με reference τιμές,
 * τις σταθερές αργίες (μαζί με το ιστορικό bug Πρωτοχρονιά/Θεοφάνια), τις κινητές
 * εορτές, και αρνητικά cases (τυχαία εργάσιμη).
 */
class GreekHolidaysTest {

    // ---------- Ορθόδοξο Πάσχα (reference values) ----------

    @Test
    void orthodoxEaster_knownYears_matchReference() {
        assertEquals(LocalDate.of(2024, 5, 5), GreekHolidays.orthodoxEaster(2024));
        assertEquals(LocalDate.of(2025, 4, 20), GreekHolidays.orthodoxEaster(2025));
        assertEquals(LocalDate.of(2026, 4, 12), GreekHolidays.orthodoxEaster(2026));
    }

    // ---------- Σταθερές αργίες (το ιστορικό bug) ----------

    @Test
    void isPublicHoliday_trueForNewYearAndEpiphany() {
        // Το ακριβές bug: εξετάσεις βρέθηκαν σε Πρωτοχρονιά/Θεοφάνια.
        assertTrue(GreekHolidays.isPublicHoliday(LocalDate.of(2026, 1, 1)));
        assertTrue(GreekHolidays.isPublicHoliday(LocalDate.of(2026, 1, 6)));
    }

    @Test
    void isPublicHoliday_trueForAllFixedHolidays() {
        int y = 2027;
        assertTrue(GreekHolidays.isPublicHoliday(LocalDate.of(y, 3, 25)));   // Ευαγγελισμός
        assertTrue(GreekHolidays.isPublicHoliday(LocalDate.of(y, 5, 1)));    // Πρωτομαγιά
        assertTrue(GreekHolidays.isPublicHoliday(LocalDate.of(y, 8, 15)));   // Κοίμηση Θεοτόκου
        assertTrue(GreekHolidays.isPublicHoliday(LocalDate.of(y, 10, 28)));  // «Όχι»
        assertTrue(GreekHolidays.isPublicHoliday(LocalDate.of(y, 12, 25)));  // Χριστούγεννα
        assertTrue(GreekHolidays.isPublicHoliday(LocalDate.of(y, 12, 26)));  // Σύναξη Θεοτόκου
    }

    // ---------- Κινητές εορτές (σχετικά με το Πάσχα) ----------

    @Test
    void isPublicHoliday_trueForMovableFeasts2026() {
        LocalDate easter = LocalDate.of(2026, 4, 12);
        assertTrue(GreekHolidays.isPublicHoliday(easter.minusDays(48)),
                "Καθαρά Δευτέρα (23/2/2026)");
        assertTrue(GreekHolidays.isPublicHoliday(easter.minusDays(2)),
                "Μεγάλη Παρασκευή");
        assertTrue(GreekHolidays.isPublicHoliday(easter), "Κυριακή του Πάσχα");
        assertTrue(GreekHolidays.isPublicHoliday(easter.plusDays(1)),
                "Δευτέρα του Πάσχα");
        assertTrue(GreekHolidays.isPublicHoliday(easter.plusDays(50)),
                "Αγίου Πνεύματος");
    }

    @Test
    void cleanMonday2026_isFeb23() {
        // Καθαρά Δευτέρα 2026 = Πάσχα − 48 = 23/2/2026.
        assertEquals(LocalDate.of(2026, 2, 23),
                GreekHolidays.orthodoxEaster(2026).minusDays(48));
    }

    // ---------- Αρνητικά / edge ----------

    @Test
    void isPublicHoliday_falseForOrdinaryWorkday() {
        assertFalse(GreekHolidays.isPublicHoliday(LocalDate.of(2026, 1, 20)));
        assertFalse(GreekHolidays.isPublicHoliday(LocalDate.of(2026, 6, 10)));
    }

    @Test
    void isPublicHoliday_falseForNull() {
        assertFalse(GreekHolidays.isPublicHoliday(null));
    }
}
