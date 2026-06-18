package gr.upatras.ceid.timetable.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Κανονικοποίηση & εμφάνιση ονομάτων διδασκόντων από το ελεύθερο
 * {@code Course.teachersText}.
 *
 * <p>Εξαγωγή (S3b-1) από τον {@code TimetableController} ώστε το snapshot-on-write
 * ({@code AssignmentSnapshotStamper}) και το {@code assignmentToDto} να μοιράζονται
 * ΕΝΑ μόνο implementation (single source): αλλιώς το stamped snapshot θα απέκλινε
 * από το live render. Καθαρές, stateless συναρτήσεις — καμία πρόσβαση σε DB/instance
 * state, ώστε να καλούνται και μέσα στο solver persistence loop χωρίς join.
 *
 * <p>Ο {@code CourseController} κρατάει προσωρινά δικό του αντίγραφο της ίδιας
 * αλυσίδας μέχρι το BL-7 (dedup).
 */
public final class TeacherDisplayText {

    private TeacherDisplayText() {
    }

    // ── Public API (καλείται από TimetableController) ────────────────────────

    /** Καθαρισμένη, deduped (ανά key) & ταξινομημένη λίστα ονομάτων ως "Α, Β, Γ". */
    public static String normalizeTeachersTextForDto(String teachersText) {
        List<String> sortedNames = sortTeacherDisplayNames(
                extractTeacherDisplayByKey(teachersText).values()
        );

        return String.join(", ", sortedNames);
    }

    /** Map από κανονικοποιημένο key → καλύτερο display name (dedup co-taught). */
    public static Map<String, String> extractTeacherDisplayByKey(String teachersText) {
        Map<String, String> result = new LinkedHashMap<>();

        for (String display : splitAndAttachTeacherRoles(teachersText)) {
            String key = teacherKeyFromDisplayName(display);
            if (!key.isBlank()) {
                result.merge(key, display, TeacherDisplayText::chooseBetterTeacherDisplay);
            }
        }

        return result;
    }

    /** Ταξινόμηση: generic placeholders τελευταία, μετά αλφαβητικά (accent-insensitive). */
    public static List<String> sortTeacherDisplayNames(Collection<String> names) {
        return names.stream()
                .filter(Objects::nonNull)
                .map(TeacherDisplayText::cleanTeacherDisplayName)
                .filter(name -> !name.isBlank())
                .distinct()
                .sorted(
                        Comparator
                                .comparing((String name) -> isGenericTeacherPlaceholder(name) ? 1 : 0)
                                .thenComparing(TeacherDisplayText::normalizeSortKey)
                )
                .toList();
    }

    /** Επιλέγει το «πληρέστερο» (μακρύτερο) display μεταξύ δύο για το ίδιο key. */
    public static String chooseBetterTeacherDisplay(String first, String second) {
        if (first == null || first.isBlank()) {
            return second == null ? "" : second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return second.length() > first.length() ? second : first;
    }

    public static boolean isGenericPlaceholder(String name) {
        if (name == null || name.isBlank()) return true;
        String upper = name.toUpperCase()
                .replace("Ά", "Α").replace("Έ", "Ε").replace("Ή", "Η")
                .replace("Ί", "Ι").replace("Ό", "Ο").replace("Ύ", "Υ")
                .replace("Ώ", "Ω");
        return upper.contains("ΕΝΤΕΤΑΛΜΕΝΟΣ")
                || upper.contains("ΕΝΤΕΤ.")
                || upper.contains("ΑΑΔΕ")
                || upper.contains("ΕΔΙΠ")
                || upper.contains("ENTETAL")
                || upper.contains("(ΑΑΔΕ)")
                || upper.contains("( ΑΑΔΕ)")
                || upper.equals("ΑΑΔΕ");
    }

    public static String cleanTeacherDisplayName(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("(Υ)", "")
                .replace("(Θ)", "")
                .replace("(Ε)", "")
                .replace("Ε.ΔΙ.Π.", "ΕΔΙΠ")
                .replace("Ε.ΔΙ.Π", "ΕΔΙΠ")
                .replace("Εντεταλμένος Διδασκων", "Εντεταλμένος Διδάσκων")
                .replace("Εντεταλμένων Διδάσκων", "Εντεταλμένος Διδάσκων")
                .replace("Α. Ηλία (ΕΔΙΠ)", "Α. Ηλίας (ΕΔΙΠ)")
                .replace("Α. Ηλία", "Α. Ηλίας")
                .replace("  ", " ")
                .trim();
    }

    /** Κανονικοποιημένο key (επώνυμο|αρχικό) για dedup ανεξαρτήτως τόνων/σειράς. */
    public static String teacherKeyFromDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isBlank()) {
            return "";
        }

        String[] parts = normalized.split(" ");
        if (parts.length == 1) {
            return parts[0];
        }

        String surname = parts[parts.length - 1];
        String firstToken = parts[0];
        String firstInitial = firstToken.substring(0, 1);

        return surname + "|" + firstInitial;
    }

    /** Accent-insensitive uppercase key για αλφαβητική ταξινόμηση. */
    public static String normalizeSortKey(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .trim();
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private static List<String> splitAndAttachTeacherRoles(String teachersText) {
        if (teachersText == null || teachersText.isBlank()) {
            return List.of();
        }

        String cleaned = teachersText
                .replace("\n", ",")
                .replace("\r", ",")
                .replace(";", ",")
                .replace(" και ", ",")
                .replace(" & ", ",");

        String[] parts = cleaned.split(",");
        List<String> result = new ArrayList<>();

        for (String part : parts) {
            String display = cleanTeacherDisplayName(part);
            if (display.isBlank()) {
                continue;
            }

            if (isStandaloneTeacherRole(display)) {
                if (!result.isEmpty()) {
                    int last = result.size() - 1;
                    result.set(last, appendRoleToTeacher(result.get(last), normalizeStandaloneTeacherRole(display)));
                } else {
                    result.add(display);
                }
                continue;
            }

            result.add(display);
        }

        return result;
    }

    private static boolean isStandaloneTeacherRole(String value) {
        String key = normalizeRoleKey(value);

        return key.equals("ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ")
                || key.equals("Η ΚΑΙ ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ")
                || key.equals("ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ Η ΑΑΔΕ")
                || key.equals("ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ ΑΑΔΕ")
                || key.equals("ΕΝΤΕΤΑΛΜΕΝΩΝ ΔΙΔΑΣΚΩΝ Η ΑΑΔΕ")
                || key.equals("ΑΑΔΕ")
                || key.equals("ΕΔΙΠ")
                || key.equals("Ε ΔΙ Π");
    }

    private static String normalizeStandaloneTeacherRole(String value) {
        String key = normalizeRoleKey(value);

        if (key.equals("ΕΔΙΠ") || key.equals("Ε ΔΙ Π")) {
            return "ΕΔΙΠ";
        }

        if (key.equals("Η ΚΑΙ ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ")) {
            return "ή/και Εντεταλμένος Διδάσκων";
        }

        if (key.equals("ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ Η ΑΑΔΕ")
                || key.equals("ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ ΑΑΔΕ")
                || key.equals("ΕΝΤΕΤΑΛΜΕΝΩΝ ΔΙΔΑΣΚΩΝ Η ΑΑΔΕ")) {
            return "Εντεταλμένος Διδάσκων ή ΑΑΔΕ";
        }

        if (key.equals("ΑΑΔΕ")) {
            return "ΑΑΔΕ";
        }

        return "Εντεταλμένος Διδάσκων";
    }

    private static String appendRoleToTeacher(String teacherDisplay, String roleDisplay) {
        if (teacherDisplay == null || teacherDisplay.isBlank()) {
            return roleDisplay == null ? "" : roleDisplay;
        }
        if (roleDisplay == null || roleDisplay.isBlank()) {
            return teacherDisplay;
        }

        int open = teacherDisplay.lastIndexOf('(');
        int close = teacherDisplay.endsWith(")") ? teacherDisplay.length() - 1 : -1;

        if (open >= 0 && close > open) {
            String base = teacherDisplay.substring(0, open).trim();
            String existing = teacherDisplay.substring(open + 1, close).trim();

            LinkedHashSet<String> roles = new LinkedHashSet<>();
            for (String part : existing.split("\\s*,\\s*")) {
                if (!part.isBlank()) {
                    roles.add(part.trim());
                }
            }
            roles.add(roleDisplay);

            return base + " (" + String.join(", ", roles) + ")";
        }

        return teacherDisplay + " (" + roleDisplay + ")";
    }

    private static String normalizeRoleKey(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeTeacherRoleKey(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean isGenericTeacherPlaceholder(String value) {
        String normalized = normalizeTeacherRoleKey(value);
        String compact = normalized.replace(" ", "");

        return normalized.contains("ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ")
                || compact.equals("ΕΔΙΠ")
                || compact.equals("ΑΑΔΕ");
    }
}
