package gr.upatras.ceid.timetable.util;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Set;

/**
 * Επίσημες ελληνικές αργίες — authoritative πηγή για την παραγωγή exam slots.
 *
 * Καμία αργία δεν είναι σκληρά κωδικοποιημένη για συγκεκριμένο έτος: οι σταθερές
 * αργίες ελέγχονται ως {@link MonthDay} και οι κινητές υπολογίζονται σε σχέση με το
 * Ορθόδοξο Πάσχα ({@link #orthodoxEaster(int)}).
 *
 * Καθαρή στατική λογική, χωρίς DB/Spring — ελέγχεται με unit tests.
 */
public final class GreekHolidays {

    private GreekHolidays() {
        // utility class — no instances
    }

    /** Σταθερές (μη κινητές) επίσημες αργίες. */
    private static final Set<MonthDay> FIXED = Set.of(
            MonthDay.of(1, 1),    // Πρωτοχρονιά
            MonthDay.of(1, 6),    // Θεοφάνια
            MonthDay.of(3, 25),   // Ευαγγελισμός / Εθνική εορτή
            MonthDay.of(5, 1),    // Εργατική Πρωτομαγιά
            MonthDay.of(8, 15),   // Κοίμηση της Θεοτόκου
            MonthDay.of(10, 28),  // Επέτειος του «Όχι»
            MonthDay.of(12, 25),  // Χριστούγεννα
            MonthDay.of(12, 26)   // Σύναξη της Θεοτόκου
    );

    /**
     * Επιστρέφει {@code true} αν η ημερομηνία είναι επίσημη ελληνική αργία
     * (σταθερή ή κινητή βάσει Ορθόδοξου Πάσχα).
     */
    public static boolean isPublicHoliday(LocalDate date) {
        if (date == null) {
            return false;
        }
        if (FIXED.contains(MonthDay.from(date))) {
            return true;
        }

        LocalDate easter = orthodoxEaster(date.getYear());
        return date.equals(easter.minusDays(48))   // Καθαρά Δευτέρα
                || date.equals(easter.minusDays(2)) // Μεγάλη Παρασκευή
                || date.equals(easter)              // Κυριακή του Πάσχα
                || date.equals(easter.plusDays(1))  // Δευτέρα του Πάσχα
                || date.equals(easter.plusDays(50)); // Αγίου Πνεύματος (Δευτέρα)
    }

    /**
     * Ορθόδοξο Πάσχα (Γρηγοριανή ημερομηνία) για το δοθέν έτος.
     *
     * Αλγόριθμος Meeus για το Ιουλιανό Πάσχα + 13 ημέρες για μετατροπή στο
     * Γρηγοριανό ημερολόγιο (έγκυρο για τα έτη 1900–2099).
     */
    public static LocalDate orthodoxEaster(int year) {
        int a = year % 4;
        int b = year % 7;
        int c = year % 19;
        int d = (19 * c + 15) % 30;
        int e = (2 * a + 4 * b - d + 34) % 7;
        int month = (d + e + 114) / 31;       // 3 = Μάρτιος, 4 = Απρίλιος
        int day = ((d + e + 114) % 31) + 1;
        return LocalDate.of(year, month, day).plusDays(13);
    }
}
