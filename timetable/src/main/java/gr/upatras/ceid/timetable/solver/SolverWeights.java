package gr.upatras.ceid.timetable.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth για τα βάρη των solver constraints (S4b-1/S4b-2a).
 *
 * Τα defaults ισούνται ΑΚΡΙΒΩΣ με τα προηγούμενα hardcoded literals των δύο
 * ConstraintProviders — η εξωτερίκευση είναι position-preserving: κανένα
 * penalizesBy δεν αλλάζει (βλ. thesis note S4a §4). Οι providers διαβάζουν το
 * βάρος κάθε κανόνα από εδώ:
 *   - {@link #hard}/{@link #soft}: βάρος στο base score («Στυλ-1», weigher=1).
 *   - {@link #w}: ακέραιο βάρος στον weigher («Στυλ-2», base=ONE_*).
 *
 * S4b-2a: το {@link #catalog()} εκθέτει για κάθε κανόνα πλήρη μεταδεδομένα
 * ({@link Def}) — scope/level/default/ελληνικό label/περιγραφή — ώστε ο
 * {@code ConstraintWeightSeeder} να γεμίζει τον πίνακα {@code
 * constraint_weight_config}. Το catalog είναι η ΜΟΝΗ πηγή: ο χάρτης των defaults
 * παράγεται από αυτό.
 *
 * Δομή για το S4b-2b: τα lookups διαβάζουν από εσωτερικό mutable map
 * αρχικοποιημένο στα defaults· το S4b-2b θα επικαλύπτει τιμές από τη ΒΔ ΧΩΡΙΣ να
 * αγγίξει τα call sites (ίδιο overlay-on-defaults μοτίβο με τα
 * TeacherAvailabilityConstraints/RoomAvailabilityConstraints). Τα solves είναι
 * σειριακά (ένα SolverFactory ανά solve) — global mutable static χωρίς
 * concurrency στην πράξη.
 */
public final class SolverWeights {

    private SolverWeights() {}

    private static final Logger log = LoggerFactory.getLogger(SolverWeights.class);

    /**
     * Μεταδεδομένα ενός constraint κανόνα (immutable).
     *
     * @param key          μοναδικό scope-prefixed key (ταιριάζει 1-1 με τα call sites)
     * @param scope        "WEEKLY" | "EXAM"
     * @param level        "HARD" | "SOFT"
     * @param defaultWeight το σημερινό literal βάρος (πηγή των defaults)
     * @param displayName  σύντομο ελληνικό label (UI-facing)
     * @param description  μία πρόταση: τι ποινικοποιεί ο κανόνας
     */
    public record Def(String key, String scope, String level, int defaultWeight,
                      String displayName, String description) {}

    /** Ο πλήρης κατάλογος των 34 κανόνων — η αυθεντική πηγή. Immutable. */
    private static final List<Def> CATALOG = buildCatalog();

    /** Compiled defaults (key -> weight), παραγόμενα από το catalog. Immutable. */
    private static final Map<String, Integer> DEFAULTS = buildDefaults(CATALOG);

    /** Live βάρη: defaults αρχικά· το S4b-2b επικαλύπτει DB τιμές εδώ. */
    private static final Map<String, Integer> WEIGHTS = new HashMap<>(DEFAULTS);

    private static List<Def> buildCatalog() {
        List<Def> c = new ArrayList<>();

        // ===== WEEKLY (CeidConstraintProvider) — HARD =====
        c.add(new Def("WEEKLY_ROOM_CONFLICT", "WEEKLY", "HARD", 1,
                "Σύγκρουση αίθουσας",
                "Δύο μαθήματα στην ίδια αίθουσα την ίδια ώρα."));
        c.add(new Def("WEEKLY_TEACHER_CONFLICT", "WEEKLY", "HARD", 1,
                "Σύγκρουση διδάσκοντα",
                "Ο ίδιος διδάσκων σε δύο διαφορετικά μαθήματα την ίδια ώρα."));
        c.add(new Def("WEEKLY_SAME_COURSE_CONFLICT", "WEEKLY", "HARD", 1,
                "Σύγκρουση ίδιου μαθήματος",
                "Δύο ώρες του ίδιου μαθήματος τοποθετημένες στο ίδιο slot."));
        c.add(new Def("WEEKLY_REQUIRED_SAME_YEAR", "WEEKLY", "HARD", 5,
                "Υποχρεωτικά ίδιου έτους",
                "Δύο υποχρεωτικά μαθήματα του ίδιου έτους την ίδια ώρα."));
        c.add(new Def("WEEKLY_LAB_IN_LAB_ROOM", "WEEKLY", "HARD", 1,
                "Εργαστήριο εκτός αίθουσας εργαστηρίου",
                "Εργαστήριο τοποθετημένο σε αίθουσα που δεν είναι εργαστηριακή."));
        c.add(new Def("WEEKLY_FIRST_YEAR_ONLY_GAMMA", "WEEKLY", "HARD", 1,
                "1ο έτος μόνο στο αμφιθέατρο Γ",
                "Μάθημα 1ου έτους (θεωρία/φροντιστήριο) εκτός της αίθουσας Γ."));
        c.add(new Def("WEEKLY_REQUIRED_ONLY_B_OR_G", "WEEKLY", "HARD", 1,
                "Υποχρεωτικά μόνο σε Β ή Γ",
                "Υποχρεωτικό μάθημα (θεωρία/φροντ.) εκτός των αμφιθεάτρων Β/Γ."));
        c.add(new Def("WEEKLY_DAILY_LECTURE_LIMIT", "WEEKLY", "HARD", 1,
                "Όριο διαλέξεων ημέρας",
                "Πάνω από 6 υποχρεωτικές διαλέξεις ίδιου έτους σε μία μέρα."));
        c.add(new Def("WEEKLY_LUNCH_BREAK", "WEEKLY", "HARD", 3,
                "Διάλειμμα μεσημεριανού",
                "Κατειλημμένο όλο το μεσημεριανό παράθυρο (12-15) στα 3 πρώτα έτη."));
        c.add(new Def("WEEKLY_TEACHER_BLOCKED", "WEEKLY", "HARD", 10,
                "Μη διαθέσιμη ώρα διδάσκοντα",
                "Μάθημα σε ώρα που ο διδάσκων έχει δηλώσει μη διαθέσιμη."));
        c.add(new Def("WEEKLY_ROOM_BLOCKED", "WEEKLY", "HARD", 10,
                "Δεσμευμένη ώρα αίθουσας",
                "Μάθημα σε ώρα δεσμευμένη για τη συγκεκριμένη αίθουσα."));

        // ===== WEEKLY — SOFT =====
        c.add(new Def("WEEKLY_ROOM_CAPACITY", "WEEKLY", "SOFT", 1,
                "Υπέρβαση χωρητικότητας αίθουσας",
                "Αναμενόμενοι φοιτητές περισσότεροι από τη χωρητικότητα της αίθουσας."));
        c.add(new Def("WEEKLY_PREFER_NORMAL_HOURS", "WEEKLY", "SOFT", 2,
                "Προτίμηση κανονικών ωρών",
                "Μάθημα τοποθετημένο σε βραδινή ώρα (από 18:00 και μετά)."));
        c.add(new Def("WEEKLY_AVOID_OVERLOADED_DAY", "WEEKLY", "SOFT", 1,
                "Αποφυγή υπερφορτωμένης μέρας",
                "Πάνω από 4 υποχρεωτικές διαλέξεις ίδιου έτους σε μία μέρα."));
        c.add(new Def("WEEKLY_TEACHER_PREFERRED", "WEEKLY", "SOFT", 3,
                "Προτιμώμενη ώρα διδάσκοντα",
                "Μάθημα εκτός των ωρών που έχει δηλώσει ως προτιμώμενες ο διδάσκων."));
        c.add(new Def("WEEKLY_REQUIRED_SAME_YEAR_GAPS", "WEEKLY", "SOFT", 1,
                "Κενά υποχρεωτικών ίδιου έτους",
                "Κενά στο ημερήσιο πρόγραμμα υποχρεωτικών μαθημάτων του ίδιου έτους."));
        c.add(new Def("WEEKLY_DIRECTION_GROUP_A", "WEEKLY", "SOFT", 5,
                "Σύμπτωση μαθημάτων κατεύθυνσης (Ομάδα Α)",
                "Μαθήματα Ομάδας Α της ίδιας κατεύθυνσης τοποθετημένα την ίδια ώρα."));

        // ===== WEEKLY — SOFT (B: συνοχή block ίδιου μαθήματος) =====
        c.add(new Def("WEEKLY_SAME_COURSE_DIFFERENT_DAY", "WEEKLY", "SOFT", 8,
                "Ίδιο μάθημα σε διαφορετική μέρα",
                "Ώρες του ίδιου μαθήματος/τύπου τοποθετημένες σε διαφορετικές ημέρες (εμποδίζει ενιαίο block)."));
        c.add(new Def("WEEKLY_SAME_COURSE_NONADJACENT", "WEEKLY", "SOFT", 5,
                "Μη διαδοχικές ώρες ίδιου μαθήματος",
                "Κενά ανάμεσα στις ώρες του ίδιου μαθήματος/τύπου την ίδια μέρα (να γίνουν διαδοχικές)."));
        c.add(new Def("WEEKLY_SAME_COURSE_DIFFERENT_ROOM", "WEEKLY", "SOFT", 3,
                "Ίδιο μάθημα σε διαφορετική αίθουσα",
                "Ώρες του ίδιου μαθήματος/τύπου σε διαφορετικές αίθουσες (ενιαία αίθουσα για το block)."));

        // ===== EXAM (ExamConstraintProvider) — HARD =====
        c.add(new Def("EXAM_TEACHER_CONFLICT", "EXAM", "HARD", 1,
                "Σύγκρουση διδάσκοντα (εξέταση)",
                "Ο ίδιος διδάσκων σε δύο εξετάσεις το ίδιο 3ωρο."));
        c.add(new Def("EXAM_REQUIRED_SAME_YEAR_SAME_DAY", "EXAM", "HARD", 5,
                "Υποχρεωτικά ίδιου έτους ίδια μέρα",
                "Δύο υποχρεωτικές εξετάσεις ίδιου έτους την ίδια ημερομηνία."));
        c.add(new Def("EXAM_ROOM_BLOCKED", "EXAM", "HARD", 10,
                "Δεσμευμένη ώρα αίθουσας (εξέταση)",
                "Εξέταση σε ώρα δεσμευμένη για τη συγκεκριμένη αίθουσα."));

        // ===== EXAM — SOFT =====
        c.add(new Def("EXAM_PREFERRED_ROOM", "EXAM", "SOFT", 4,
                "Προτιμώμενη αίθουσα εξέτασης",
                "Εξέταση εκτός των αιθουσών που έχει δηλώσει προτιμώμενες ο διδάσκων."));
        c.add(new Def("EXAM_PREFERRED_HOUR", "EXAM", "SOFT", 4,
                "Προτιμώμενη ώρα εξέτασης",
                "Εξέταση εκτός των ωρών έναρξης που έχει δηλώσει προτιμώμενες ο διδάσκων."));
        c.add(new Def("EXAM_SHARED_ROOM_CAPACITY", "EXAM", "SOFT", 1,
                "Υπέρβαση χωρητικότητας (κοινή αίθουσα)",
                "Άθροισμα φοιτητών εξετάσεων στο ίδιο slot πάνω από τη χωρητικότητα."));
        c.add(new Def("EXAM_SAME_YEAR_SAME_DAY", "EXAM", "SOFT", 6,
                "Στοίβαξη εξετάσεων ίδιου έτους",
                "Εξετάσεις ίδιου έτους (με τουλάχιστον μία επιλογής) την ίδια μέρα."));
        c.add(new Def("EXAM_DAILY_LOAD_BALANCE", "EXAM", "SOFT", 1,
                "Εξισορρόπηση φόρτου ημέρας",
                "Συγκέντρωση πολλών εξετάσεων στην ίδια ημερομηνία."));
        c.add(new Def("EXAM_PREFER_DISTINCT_ROOMS", "EXAM", "SOFT", 1,
                "Διαφορετικές αίθουσες ανά slot",
                "Εξετάσεις του ίδιου slot στην ίδια αίθουσα (tie-breaker κατανομής)."));
        c.add(new Def("EXAM_REQUIRED_BEFORE_ELECTIVES", "EXAM", "SOFT", 1,
                "Υποχρεωτικά πριν τα επιλογής",
                "Υποχρεωτική εξέταση προγραμματισμένη μετά από εξέταση επιλογής."));
        c.add(new Def("EXAM_SPREAD_SAME_YEAR", "EXAM", "SOFT", 3,
                "Διασπορά υποχρεωτικών ίδιου έτους",
                "Υποχρεωτικές εξετάσεις ίδιου έτους σε διαδοχικές ημέρες."));
        c.add(new Def("EXAM_DIRECTION_GROUP_A_SAME_DAY", "EXAM", "SOFT", 5,
                "Μαθήματα κατεύθυνσης ίδια μέρα (Ομάδα Α)",
                "Εξετάσεις Ομάδας Α της ίδιας κατεύθυνσης την ίδια ημερομηνία."));
        c.add(new Def("EXAM_TEACHER_MULTIPLE_SAME_DAY", "EXAM", "SOFT", 2,
                "Πολλές εξετάσεις διδάσκοντα ίδια μέρα",
                "Ο ίδιος διδάσκων σε πολλές εξετάσεις την ίδια ημερομηνία."));
        c.add(new Def("EXAM_PREFER_MORNING_LARGE", "EXAM", "SOFT", 1,
                "Πρωινή εξέταση μεγάλων μαθημάτων",
                "Μεγάλο μάθημα (>150 φοιτητές) σε απογευματινό/βραδινό slot."));

        return List.copyOf(c);
    }

    private static Map<String, Integer> buildDefaults(List<Def> catalog) {
        Map<String, Integer> m = new HashMap<>();
        for (Def d : catalog) {
            m.put(d.key(), d.defaultWeight());
        }
        return Map.copyOf(m);
    }

    /** Ο πλήρης κατάλογος των κανόνων (immutable) — για τον seeder/UI. */
    public static List<Def> catalog() {
        return CATALOG;
    }

    /**
     * Ακέραιο βάρος κανόνα (για weigher-based constraints, «Στυλ-2»).
     * Unknown key = programming error (typo) → ρητή αποτυχία, ΠΟΤΕ σιωπηλό 0
     * (που θα απενεργοποιούσε τον κανόνα).
     */
    public static int w(String key) {
        Integer v = WEIGHTS.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Unknown constraint weight key: " + key);
        }
        return v;
    }

    /**
     * S4b-2b: επικάλυψη του βάρους ενός κανόνα από persisted (DB) τιμή, per-solve.
     * Άγνωστο key (π.χ. stale row μετά από κατάργηση κανόνα) → log.warn + skip:
     * ένα orphan row ΔΕΝ πρέπει να σπάει το solve. Το WEIGHTS παραμένει το ίδιο
     * mutable map· {@link #resetToDefaults()} επαναφέρει στα defaults.
     */
    public static void applyOverride(String key, int weight) {
        if (!WEIGHTS.containsKey(key)) {
            log.warn("Ignoring weight override for unknown constraint key: {}", key);
            return;
        }
        WEIGHTS.put(key, weight);
    }

    /** HARD score στο βάρος του κανόνα (για base-score constraints, «Στυλ-1»). */
    public static HardSoftScore hard(String key) {
        return HardSoftScore.ofHard(w(key));
    }

    /** SOFT score στο βάρος του κανόνα (για base-score constraints, «Στυλ-1»). */
    public static HardSoftScore soft(String key) {
        return HardSoftScore.ofSoft(w(key));
    }

    /**
     * Επαναφορά των in-memory βαρών στα compiled defaults. Forward-safety για
     * το DB overlay του S4b-2b: τα *ConstraintProviderTest το καλούν στο
     * {@code @AfterEach} ώστε ένα test που (μελλοντικά) αλλάζει βάρος να μη
     * «μολύνει» τα επόμενα. Σήμερα (χωρίς overlay) είναι ουσιαστικά no-op.
     */
    public static void resetToDefaults() {
        WEIGHTS.clear();
        WEIGHTS.putAll(DEFAULTS);
    }
}
