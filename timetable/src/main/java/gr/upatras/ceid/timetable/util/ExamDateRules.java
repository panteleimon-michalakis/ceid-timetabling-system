package gr.upatras.ceid.timetable.util;

import java.time.LocalDate;
import java.util.Set;

/**
 * Κανόνες επιλεξιμότητας ημερομηνίας για εξετάσεις — ένα σημείο αλήθειας για το
 * φίλτρο «αργία ή custom εξαίρεση», κοινό για τη ΔΗΜΙΟΥΡΓΙΑ exam slots και για την
 * ΤΟΠΟΘΕΤΗΣΗ εξετάσεων (value range του solver).
 *
 * Η λογική αργιών επαναχρησιμοποιεί το {@link GreekHolidays} — δεν επαναγράφεται εδώ.
 * Ο έλεγχος Σαββατοκύριακου παραμένει στα call sites (δεν αφορά όλες τις διαδρομές).
 *
 * Καθαρή στατική λογική, χωρίς DB/Spring — ελέγχεται με unit tests.
 */
public final class ExamDateRules {

    private ExamDateRules() {
        // utility class — no instances
    }

    /**
     * {@code true} αν η ημερομηνία ΔΕΝ επιτρέπεται για εξέταση επειδή είναι επίσημη
     * ελληνική αργία ή περιλαμβάνεται στις custom εξαιρέσεις του προγράμματος.
     *
     * @param date          η ημερομηνία του exam slot (μπορεί να είναι {@code null})
     * @param excludedDates οι custom εξαιρούμενες ημερομηνίες (μπορεί {@code null}/κενό)
     */
    public static boolean isExcludedExamDate(LocalDate date, Set<LocalDate> excludedDates) {
        if (date == null) {
            return false;
        }
        return GreekHolidays.isPublicHoliday(date)
                || (excludedDates != null && excludedDates.contains(date));
    }
}
