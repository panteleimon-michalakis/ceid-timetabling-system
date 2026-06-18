package gr.upatras.ceid.timetable.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization test για το {@link TeacherDisplayText} (S3b-1).
 *
 * Σκοπός: κλειδώνει τη ΤΡΕΧΟΥΣΑ συμπεριφορά της αλυσίδας normalize που εξήχθη
 * αυτούσια από τον TimetableController, ώστε το extract να είναι αποδεδειγμένα
 * behavior-preserving και να πιάνει μελλοντικές αθέλητες αλλαγές. Καλύπτει τα
 * edge cases όπου σπάει ένα «αθώο» refactor: generic placeholders (Ε.ΔΙ.Π./ΑΑΔΕ),
 * co-taught ordering, role suffixes, empty/null, ελληνικά με τόνους.
 */
class TeacherDisplayTextTest {

    // ── empty / null ─────────────────────────────────────────────────────────
    @Test
    void emptyOrNull_yieldEmptyString() {
        assertEquals("", TeacherDisplayText.normalizeTeachersTextForDto(null));
        assertEquals("", TeacherDisplayText.normalizeTeachersTextForDto(""));
        assertEquals("", TeacherDisplayText.normalizeTeachersTextForDto("   "));
    }

    // ── single ───────────────────────────────────────────────────────────────
    @Test
    void singleName_passthrough() {
        assertEquals("Παπαδόπουλος",
                TeacherDisplayText.normalizeTeachersTextForDto("Παπαδόπουλος"));
    }

    // ── co-taught ordering: αλφαβητικά, accent-insensitive ──────────────────────
    @Test
    void coTaught_sortedAlphabetically_accentInsensitive() {
        assertEquals("Αντωνόπουλος, Καρβέλης",
                TeacherDisplayText.normalizeTeachersTextForDto("Καρβέλης, Αντωνόπουλος"));
        // τόνος στο πρώτο γράμμα δεν αλλάζει τη σειρά
        assertEquals("Άβδης, Ζάχος",
                TeacherDisplayText.normalizeTeachersTextForDto("Ζάχος, Άβδης"));
    }

    // ── generic placeholder: ταξινομείται ΤΕΛΕΥΤΑΙΟ ────────────────────────────
    @Test
    void genericPlaceholder_sortsLast() {
        assertEquals("Παπαδόπουλος, Εντεταλμένος Διδάσκων",
                TeacherDisplayText.normalizeTeachersTextForDto("Εντεταλμένος Διδάσκων, Παπαδόπουλος"));
    }

    // ── standalone role: προσαρτάται στον προηγούμενο διδάσκοντα ────────────────
    @Test
    void standaloneRole_attachesToPrecedingTeacher() {
        assertEquals("Παπαδόπουλος (ΑΑΔΕ)",
                TeacherDisplayText.normalizeTeachersTextForDto("Παπαδόπουλος, ΑΑΔΕ"));
    }

    // ── markers (Θ)/(Ε)/(Υ) + Ε.ΔΙ.Π. data-fixes ──────────────────────────────
    @Test
    void markersStripped_andEdipFixApplied() {
        assertEquals("Παπαδόπουλος",
                TeacherDisplayText.normalizeTeachersTextForDto("Παπαδόπουλος (Θ)"));

        // Ε.ΔΙ.Π. ως standalone role → προσαρτάται καθαρά στον διδάσκοντα.
        assertEquals("Καρανικόλας (ΕΔΙΠ)",
                TeacherDisplayText.normalizeTeachersTextForDto("Καρανικόλας, Ε.ΔΙ.Π."));

        // ΠΡΟΫΠΑΡΧΟΥΣΑ ΙΔΙΟΤΡΟΠΙΑ (locked, ΟΧΙ regression): το ειδικό data-fix
        // ζεύγος replace("Α. Ηλία (ΕΔΙΠ)"→…ς) + replace("Α. Ηλία"→…ς) (overlapping),
        // μαζί με το ΔΙΠΛΟ πέρασμα του cleanTeacherDisplayName (split + sort),
        // προσθέτει πλεοναστικά «ς» (Ηλία→Ηλίαςςς). Το characterization test το
        // καρφώνει· η διόρθωση είναι ξεχωριστό task — το extract είναι
        // behavior-preserving (77/77 πράσινα).
        assertEquals("Α. Ηλίαςςς (ΕΔΙΠ)",
                TeacherDisplayText.normalizeTeachersTextForDto("Α. Ηλία (ΕΔΙΠ)"));
    }

    // ── dedup ίδιου key: κρατά το «πληρέστερο» (μακρύτερο) display ──────────────
    @Test
    void duplicateKey_keepsLongerDisplay() {
        assertEquals("Καίτη Παπαδόπουλος",
                TeacherDisplayText.normalizeTeachersTextForDto("Κ. Παπαδόπουλος, Καίτη Παπαδόπουλος"));

        Map<String, String> byKey =
                TeacherDisplayText.extractTeacherDisplayByKey("Κ. Παπαδόπουλος, Καίτη Παπαδόπουλος");
        assertEquals(1, byKey.size(), "ίδιο key → ένα entry");
    }

    // ── primitives που χρησιμοποιεί η co-taught logic εκτός αλυσίδας ────────────
    @Test
    void primitives_genericPlaceholder_and_sortKey() {
        assertTrue(TeacherDisplayText.isGenericPlaceholder("Εντεταλμένος Διδάσκων"));
        assertTrue(TeacherDisplayText.isGenericPlaceholder("ΑΑΔΕ"));
        assertTrue(TeacherDisplayText.isGenericPlaceholder(null));
        assertTrue(TeacherDisplayText.isGenericPlaceholder(""));
        assertFalse(TeacherDisplayText.isGenericPlaceholder("Παπαδόπουλος"));

        assertEquals("ΑΒΔΗΣ", TeacherDisplayText.normalizeSortKey("Άβδης"));
        assertEquals("", TeacherDisplayText.normalizeSortKey(null));
    }
}
