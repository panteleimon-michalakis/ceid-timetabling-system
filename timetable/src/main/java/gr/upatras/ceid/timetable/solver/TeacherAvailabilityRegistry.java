package gr.upatras.ceid.timetable.solver;

import java.util.*;

/**
 * Registry περιορισμών διαθεσιμότητας καθηγητών.
 *
 * Μορφή teacher key: ΕΠΩΝΥΜΟ|ΑΡΧΙΚΟ  (π.χ. "ΒΛΑΧΟΣ|Κ")
 * — παράγεται από την teacherKey() του SolverService.
 *
 * Μορφή slot key: DAY_HOUR  (π.χ. "WEDNESDAY_14")
 * Ημέρες: MONDAY TUESDAY WEDNESDAY THURSDAY FRIDAY
 * Ώρες  : 9–20
 */
public class TeacherAvailabilityRegistry {

    public static void load() {
        Map<String, Set<String>> blocked   = new HashMap<>();
        Map<String, Set<String>> preferred = new HashMap<>();

        // -----------------------------------------------------------------------
        // Βλάχος Κ. — ΟΧΙ Τετάρτη, Πέμπτη, Παρασκευή
        // -----------------------------------------------------------------------
        addBlocked(blocked, "ΒΛΑΧΟΣ|Κ", allHoursOn("WEDNESDAY", "THURSDAY", "FRIDAY"));

        // -----------------------------------------------------------------------
        // Μπερμπερίδης Κ.
        // ΧΕΙΜΕΡΙΝΟ: ΟΧΙ Δευτέρα και Παρασκευή
        // ΕΑΡΙΝΟ: ΟΧΙ πριν 11:00 (εκτός Παρ που ήδη blocked)
        // -----------------------------------------------------------------------
        addBlocked(blocked, "ΜΠΕΡΜΠΕΡΙΔΗΣ|Κ", allHoursOn("MONDAY", "FRIDAY"));
        addBlocked(blocked, "ΜΠΕΡΜΠΕΡΙΔΗΣ|Κ", hoursOnBefore("TUESDAY",   11));
        addBlocked(blocked, "ΜΠΕΡΜΠΕΡΙΔΗΣ|Κ", hoursOnBefore("WEDNESDAY", 11));
        addBlocked(blocked, "ΜΠΕΡΜΠΕΡΙΔΗΣ|Κ", hoursOnBefore("THURSDAY",  11));

        // -----------------------------------------------------------------------
        // Κακλαμάνης Χρ. — ΜΟΝΟ μετά τις 15:00
        // -----------------------------------------------------------------------
        addBlocked(blocked, "ΚΑΚΛΑΜΑΝΗΣ|Χ", hoursOnBefore("MONDAY",    15));
        addBlocked(blocked, "ΚΑΚΛΑΜΑΝΗΣ|Χ", hoursOnBefore("TUESDAY",   15));
        addBlocked(blocked, "ΚΑΚΛΑΜΑΝΗΣ|Χ", hoursOnBefore("WEDNESDAY", 15));
        addBlocked(blocked, "ΚΑΚΛΑΜΑΝΗΣ|Χ", hoursOnBefore("THURSDAY",  15));
        addBlocked(blocked, "ΚΑΚΛΑΜΑΝΗΣ|Χ", hoursOnBefore("FRIDAY",    15));
        addPreferred(preferred, "ΚΑΚΛΑΜΑΝΗΣ|Χ",
            slots("THURSDAY_18","THURSDAY_19","FRIDAY_19","FRIDAY_20"));

        // -----------------------------------------------------------------------
        // Ζαρολιάγκης Χρ.
        // -----------------------------------------------------------------------
        addPreferred(preferred, "ΖΑΡΟΛΙΑΓΚΗΣ|Χ",
            slots("TUESDAY_11","TUESDAY_12","THURSDAY_9","THURSDAY_10",
                  "WEDNESDAY_11","WEDNESDAY_12"));

        // -----------------------------------------------------------------------
        // Παπαϊωάννου Ευ. — ΟΧΙ μετά 17:00
        // -----------------------------------------------------------------------
        addBlocked(blocked, "ΠΑΠΑΙΩΑΝΝΟΥ|Ε", hoursOnAfter("MONDAY",    17));
        addBlocked(blocked, "ΠΑΠΑΙΩΑΝΝΟΥ|Ε", hoursOnAfter("TUESDAY",   17));
        addBlocked(blocked, "ΠΑΠΑΙΩΑΝΝΟΥ|Ε", hoursOnAfter("WEDNESDAY", 17));
        addBlocked(blocked, "ΠΑΠΑΙΩΑΝΝΟΥ|Ε", hoursOnAfter("THURSDAY",  17));
        addBlocked(blocked, "ΠΑΠΑΙΩΑΝΝΟΥ|Ε", hoursOnAfter("FRIDAY",    17));
        addPreferred(preferred, "ΠΑΠΑΙΩΑΝΝΟΥ|Ε",
            slots("THURSDAY_18","THURSDAY_19","FRIDAY_19","FRIDAY_20"));

        // -----------------------------------------------------------------------
        // Βερυκούκης Χρ. — ΠΡΟΤΙΜΑ Δευτέρα 9-12 (SOFT, όχι HARD)
        // Το ΝΕ592 έχει 3 lecture hours και μόνο 3 διαθέσιμα slots = αδύνατο ως HARD.
        // -----------------------------------------------------------------------
        addPreferred(preferred, "ΒΕΡΥΚΟΥΚΗΣ|Χ",
            slots("MONDAY_9","MONDAY_10","MONDAY_11","MONDAY_12",
                  "TUESDAY_9","TUESDAY_10","TUESDAY_11","TUESDAY_12"));

        // -----------------------------------------------------------------------
        // Βέργος Χ.
        // ΧΕΙΜΕΡΙΝΟ: Προτιμά 09:00-11:00
        // ΕΑΡΙΝΟ: ΟΧΙ 13:00-17:00
        // -----------------------------------------------------------------------
        addPreferred(preferred, "ΒΕΡΓΟΣ|Χ", slots(
            "MONDAY_9","MONDAY_10","TUESDAY_9","TUESDAY_10",
            "WEDNESDAY_9","WEDNESDAY_10","THURSDAY_9","THURSDAY_10",
            "FRIDAY_9","FRIDAY_10"));
        addBlocked(blocked, "ΒΕΡΓΟΣ|Χ", slots(
            "MONDAY_13","MONDAY_14","MONDAY_15","MONDAY_16",
            "TUESDAY_13","TUESDAY_14","TUESDAY_15","TUESDAY_16",
            "WEDNESDAY_13","WEDNESDAY_14","WEDNESDAY_15","WEDNESDAY_16",
            "THURSDAY_13","THURSDAY_14","THURSDAY_15","THURSDAY_16",
            "FRIDAY_13","FRIDAY_14","FRIDAY_15","FRIDAY_16"));

        // -----------------------------------------------------------------------
        // Μεγαλοοικονόμου Β. — ΟΧΙ μετά 13:00
        // -----------------------------------------------------------------------
        addBlocked(blocked, "ΜΕΓΑΛΟΟΙΚΟΝΟΜΟΥ|Β", hoursOnAfter("MONDAY",    13));
        addBlocked(blocked, "ΜΕΓΑΛΟΟΙΚΟΝΟΜΟΥ|Β", hoursOnAfter("TUESDAY",   13));
        addBlocked(blocked, "ΜΕΓΑΛΟΟΙΚΟΝΟΜΟΥ|Β", hoursOnAfter("WEDNESDAY", 13));
        addBlocked(blocked, "ΜΕΓΑΛΟΟΙΚΟΝΟΜΟΥ|Β", hoursOnAfter("THURSDAY",  13));
        addBlocked(blocked, "ΜΕΓΑΛΟΟΙΚΟΝΟΜΟΥ|Β", hoursOnAfter("FRIDAY",    13));

        // -----------------------------------------------------------------------
        // Ξένος Μ. — ΟΧΙ Δευτέρα και Παρασκευή
        // -----------------------------------------------------------------------
        addBlocked(blocked, "ΞΕΝΟΣ|Μ", allHoursOn("MONDAY", "FRIDAY"));

        // -----------------------------------------------------------------------
        // Σκλάβος Ν.
        // ΧΕΙΜΕΡΙΝΟ: Κυβερνοασφάλεια Τετ 16-19, Μικροϋπολογιστές Πεμ 13-17
        // ΕΑΡΙΝΟ: Ασφάλεια Υλικού Τετ 16-19, Ενσωματωμένα Πεμπ/Παρ
        // -----------------------------------------------------------------------
        addPreferred(preferred, "ΣΚΛΑΒΟΣ|Ν",
            slots("WEDNESDAY_16","WEDNESDAY_17","WEDNESDAY_18",
                  "THURSDAY_10","THURSDAY_11","THURSDAY_12","THURSDAY_13",
                  "THURSDAY_14","THURSDAY_15","THURSDAY_16",
                  "FRIDAY_10","FRIDAY_11","FRIDAY_12","FRIDAY_13","FRIDAY_14"));

        // -----------------------------------------------------------------------
        // Παπαδημητρίου Γ. — 11:00-17:00
        // -----------------------------------------------------------------------
        addBlocked(blocked, "ΠΑΠΑΔΗΜΗΤΡΙΟΥ|Γ", hoursOnBefore("MONDAY",    11));
        addBlocked(blocked, "ΠΑΠΑΔΗΜΗΤΡΙΟΥ|Γ", hoursOnBefore("TUESDAY",   11));
        addBlocked(blocked, "ΠΑΠΑΔΗΜΗΤΡΙΟΥ|Γ", hoursOnBefore("WEDNESDAY", 11));
        addBlocked(blocked, "ΠΑΠΑΔΗΜΗΤΡΙΟΥ|Γ", hoursOnBefore("THURSDAY",  11));
        addBlocked(blocked, "ΠΑΠΑΔΗΜΗΤΡΙΟΥ|Γ", hoursOnBefore("FRIDAY",    11));
        addBlocked(blocked, "ΠΑΠΑΔΗΜΗΤΡΙΟΥ|Γ", hoursOnAfter("MONDAY",    17));
        addBlocked(blocked, "ΠΑΠΑΔΗΜΗΤΡΙΟΥ|Γ", hoursOnAfter("TUESDAY",   17));
        addBlocked(blocked, "ΠΑΠΑΔΗΜΗΤΡΙΟΥ|Γ", hoursOnAfter("WEDNESDAY", 17));
        addBlocked(blocked, "ΠΑΠΑΔΗΜΗΤΡΙΟΥ|Γ", hoursOnAfter("THURSDAY",  17));
        addBlocked(blocked, "ΠΑΠΑΔΗΜΗΤΡΙΟΥ|Γ", hoursOnAfter("FRIDAY",    17));

        // -----------------------------------------------------------------------
        // Χατζηδούκας Π.
        // -----------------------------------------------------------------------
        addPreferred(preferred, "ΧΑΤΖΗΔΟΥΚΑΣ|Π",
            slots("THURSDAY_9","THURSDAY_10","THURSDAY_11",
                  "MONDAY_16","MONDAY_17","WEDNESDAY_16","WEDNESDAY_17"));

        // -----------------------------------------------------------------------
        // Μακρής Χ. (μετά deduplication id=144)
        // -----------------------------------------------------------------------
        addPreferred(preferred, "ΜΑΚΡΗΣ|Χ",
            slots("MONDAY_13","MONDAY_14","MONDAY_15",
                  "WEDNESDAY_9","WEDNESDAY_10",
                  "THURSDAY_11","THURSDAY_12",
                  "FRIDAY_11","FRIDAY_12"));

        // -----------------------------------------------------------------------
        // Κοσμαδάκης Σ. — ΟΧΙ μετά 18:00
        // -----------------------------------------------------------------------
        addBlocked(blocked, "ΚΟΣΜΑΔΑΚΗΣ|Σ", hoursOnAfter("MONDAY",    18));
        addBlocked(blocked, "ΚΟΣΜΑΔΑΚΗΣ|Σ", hoursOnAfter("TUESDAY",   18));
        addBlocked(blocked, "ΚΟΣΜΑΔΑΚΗΣ|Σ", hoursOnAfter("WEDNESDAY", 18));
        addBlocked(blocked, "ΚΟΣΜΑΔΑΚΗΣ|Σ", hoursOnAfter("THURSDAY",  18));
        addBlocked(blocked, "ΚΟΣΜΑΔΑΚΗΣ|Σ", hoursOnAfter("FRIDAY",    18));

        // -----------------------------------------------------------------------
        // Γαροφαλάκης Ι.
        // -----------------------------------------------------------------------
        addPreferred(preferred, "ΓΑΡΟΦΑΛΑΚΗΣ|Ι",
            slots("WEDNESDAY_18","WEDNESDAY_19","THURSDAY_18","THURSDAY_19",
                  "MONDAY_14","MONDAY_15","MONDAY_16",
                  "WEDNESDAY_14","WEDNESDAY_15","THURSDAY_14","THURSDAY_15"));

        // -----------------------------------------------------------------------
        // Νικολετσέας Σ.
        // ΧΕΙΜΕΡΙΝΟ: Παρ 10-13, Δευτ 11-13
        // ΕΑΡΙΝΟ: Παρ 11-13, Πεμπ 10-13
        // -----------------------------------------------------------------------
        addPreferred(preferred, "ΝΙΚΟΛΕΤΣΕΑΣ|Σ",
            slots("FRIDAY_10","FRIDAY_11","FRIDAY_12",
                  "MONDAY_11","MONDAY_12",
                  "THURSDAY_10","THURSDAY_11","THURSDAY_12"));

        // -----------------------------------------------------------------------
        // Ζερβάκης Γ. — VLSI: Πέμπτη 12-15
        // -----------------------------------------------------------------------
        addPreferred(preferred, "ΖΕΡΒΑΚΗΣ|Γ",
            slots("THURSDAY_12","THURSDAY_13","THURSDAY_14"));

        // -----------------------------------------------------------------------
        // Δούναβη Μ.-Ε. — ΟΧΙ μετά 15:00
        // key: "Μ.-Ε. Δούναβη" → NFD → "Μ Ε ΔΟΥΝΑΒΗ" → "ΔΟΥΝΑΒΗ|Μ"
        // -----------------------------------------------------------------------
        addBlocked(blocked, "ΔΟΥΝΑΒΗ|Μ", hoursOnAfter("MONDAY",    15));
        addBlocked(blocked, "ΔΟΥΝΑΒΗ|Μ", hoursOnAfter("TUESDAY",   15));
        addBlocked(blocked, "ΔΟΥΝΑΒΗ|Μ", hoursOnAfter("WEDNESDAY", 15));
        addBlocked(blocked, "ΔΟΥΝΑΒΗ|Μ", hoursOnAfter("THURSDAY",  15));
        addBlocked(blocked, "ΔΟΥΝΑΒΗ|Μ", hoursOnAfter("FRIDAY",    15));

        // -----------------------------------------------------------------------
        // Σιούτας Σ.
        // ΧΕΙΜΕΡΙΝΟ: Προτιμά Τρίτη 15-18, Τετάρτη 9-11
        // ΕΑΡΙΝΟ: ΟΧΙ Τρίτη μετά 17, Πέμπτη μετά 12
        // -----------------------------------------------------------------------
        addPreferred(preferred, "ΣΙΟΥΤΑΣ|Σ",
            slots("TUESDAY_15","TUESDAY_16","TUESDAY_17",
                  "WEDNESDAY_9","WEDNESDAY_10"));
        addBlocked(blocked, "ΣΙΟΥΤΑΣ|Σ", hoursOnAfter("TUESDAY",  17));
        addBlocked(blocked, "ΣΙΟΥΤΑΣ|Σ", hoursOnAfter("THURSDAY", 12));

        // -----------------------------------------------------------------------
        // Στεφανόπουλος Ε.
        // -----------------------------------------------------------------------
        addPreferred(preferred, "ΣΤΕΦΑΝΟΠΟΥΛΟΣ|Ε",
            slots("TUESDAY_15","TUESDAY_16","TUESDAY_17",
                  "THURSDAY_15","THURSDAY_16","THURSDAY_17"));

        // -----------------------------------------------------------------------
        // Χρηστίδης Χ. (Εαρινό)
        // -----------------------------------------------------------------------
        addPreferred(preferred, "ΧΡΗΣΤΙΔΗΣ|Χ",
            slots("MONDAY_14","MONDAY_15","MONDAY_16","MONDAY_17",
                  "TUESDAY_14","TUESDAY_15","TUESDAY_16","TUESDAY_17"));

        // -----------------------------------------------------------------------
        // Κομνηνός Α. — ΟΧΙ Δευτ & Τετ μετά 13, υπόλοιπες μετά 17
        // -----------------------------------------------------------------------
        addBlocked(blocked, "ΚΟΜΝΗΝΟΣ|Α", hoursOnAfter("MONDAY",    13));
        addBlocked(blocked, "ΚΟΜΝΗΝΟΣ|Α", hoursOnAfter("WEDNESDAY", 13));
        addBlocked(blocked, "ΚΟΜΝΗΝΟΣ|Α", hoursOnAfter("TUESDAY",   17));
        addBlocked(blocked, "ΚΟΜΝΗΝΟΣ|Α", hoursOnAfter("THURSDAY",  17));
        addBlocked(blocked, "ΚΟΜΝΗΝΟΣ|Α", hoursOnAfter("FRIDAY",    17));

        // -----------------------------------------------------------------------
        // Ανδρικόπουλος Α.
        // -----------------------------------------------------------------------
        addPreferred(preferred, "ΑΝΔΡΙΚΟΠΟΥΛΟΣ|Α",
            slots("MONDAY_17","MONDAY_18","TUESDAY_17","TUESDAY_18",
                  "WEDNESDAY_17","WEDNESDAY_18","THURSDAY_17","THURSDAY_18",
                  "FRIDAY_17","FRIDAY_18"));

        // -----------------------------------------------------------------------
        // Σισμάνογλου Π. — ΜΟΝΟ Δευτέρα 18-21 (HARD)
        // Σημ: "Π. Σισμάνογλου" → "ΣΙΣΜΑΝΟΓΛΟΥ|Π"
        // -----------------------------------------------------------------------
        addBlocked(blocked, "ΣΙΣΜΑΝΟΓΛΟΥ|Π", allHoursOn("TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"));
        addBlocked(blocked, "ΣΙΣΜΑΝΟΓΛΟΥ|Π", hoursOnBefore("MONDAY", 18));
        addPreferred(preferred, "ΣΙΣΜΑΝΟΓΛΟΥ|Π",
            slots("MONDAY_18","MONDAY_19","MONDAY_20"));

        // -----------------------------------------------------------------------
        TeacherAvailabilityConstraints.BLOCKED_SLOTS   = Collections.unmodifiableMap(blocked);
        TeacherAvailabilityConstraints.PREFERRED_SLOTS = Collections.unmodifiableMap(preferred);
    }

    // -----------------------------------------------------------------------
    // Builder helpers
    // -----------------------------------------------------------------------

    private static void addBlocked(Map<String, Set<String>> map, String key, Set<String> slots) {
        map.computeIfAbsent(key, k -> new HashSet<>()).addAll(slots);
    }

    private static void addPreferred(Map<String, Set<String>> map, String key, Set<String> slots) {
        map.computeIfAbsent(key, k -> new HashSet<>()).addAll(slots);
    }

    private static Set<String> allHoursOn(String... days) {
        Set<String> result = new HashSet<>();
        for (String day : days)
            for (int h = 9; h <= 20; h++)
                result.add(day + "_" + h);
        return result;
    }

    private static Set<String> hoursOnBefore(String day, int cutoffHour) {
        Set<String> result = new HashSet<>();
        for (int h = 9; h < cutoffHour; h++)
            result.add(day + "_" + h);
        return result;
    }

    private static Set<String> hoursOnAfter(String day, int cutoffHour) {
        Set<String> result = new HashSet<>();
        for (int h = cutoffHour; h <= 20; h++)
            result.add(day + "_" + h);
        return result;
    }

    @SafeVarargs
    private static Set<String> slots(String... keys) {
        return new HashSet<>(Arrays.asList(keys));
    }
}