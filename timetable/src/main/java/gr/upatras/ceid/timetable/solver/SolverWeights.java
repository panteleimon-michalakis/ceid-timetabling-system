package gr.upatras.ceid.timetable.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;

import java.util.HashMap;
import java.util.Map;

/**
 * Single source of truth για τα βάρη των solver constraints (S4b-1).
 *
 * Τα defaults ισούνται ΑΚΡΙΒΩΣ με τα προηγούμενα hardcoded literals των δύο
 * ConstraintProviders — η εξωτερίκευση είναι position-preserving: κανένα
 * penalizesBy δεν αλλάζει (βλ. thesis note S4a §4). Οι providers διαβάζουν το
 * βάρος κάθε κανόνα από εδώ:
 *   - {@link #hard}/{@link #soft}: βάρος στο base score («Στυλ-1», weigher=1).
 *   - {@link #w}: ακέραιο βάρος στον weigher («Στυλ-2», base=ONE_*).
 *
 * Δομή για το S4b-2: τα lookups διαβάζουν από εσωτερικό mutable map
 * αρχικοποιημένο στα defaults· το S4b-2 θα επικαλύπτει τιμές από τη ΒΔ ΧΩΡΙΣ να
 * αγγίξει τα call sites (ίδιο overlay-on-defaults μοτίβο με τα
 * TeacherAvailabilityConstraints/RoomAvailabilityConstraints). Τα solves είναι
 * σειριακά (ένα SolverFactory ανά solve) — global mutable static χωρίς
 * concurrency στην πράξη.
 */
public final class SolverWeights {

    private SolverWeights() {}

    /** Compiled defaults — η αυθεντική πηγή των βαρών. Immutable. */
    private static final Map<String, Integer> DEFAULTS = buildDefaults();

    /** Live βάρη: defaults αρχικά· το S4b-2 επικαλύπτει DB τιμές εδώ. */
    private static final Map<String, Integer> WEIGHTS = new HashMap<>(DEFAULTS);

    private static Map<String, Integer> buildDefaults() {
        Map<String, Integer> m = new HashMap<>();

        // ===== WEEKLY (CeidConstraintProvider) =====
        // HARD
        m.put("WEEKLY_ROOM_CONFLICT", 1);
        m.put("WEEKLY_TEACHER_CONFLICT", 1);
        m.put("WEEKLY_SAME_COURSE_CONFLICT", 1);
        m.put("WEEKLY_REQUIRED_SAME_YEAR", 5);
        m.put("WEEKLY_LAB_IN_LAB_ROOM", 1);
        m.put("WEEKLY_FIRST_YEAR_ONLY_GAMMA", 1);
        m.put("WEEKLY_REQUIRED_ONLY_B_OR_G", 1);
        m.put("WEEKLY_DAILY_LECTURE_LIMIT", 1);     // weigher: w * (lectureCount - 6)
        m.put("WEEKLY_LUNCH_BREAK", 3);
        m.put("WEEKLY_TEACHER_BLOCKED", 10);
        m.put("WEEKLY_ROOM_BLOCKED", 10);
        // SOFT
        m.put("WEEKLY_ROOM_CAPACITY", 1);           // weigher: w * overflow
        m.put("WEEKLY_PREFER_NORMAL_HOURS", 2);
        m.put("WEEKLY_AVOID_OVERLOADED_DAY", 1);    // weigher: w * (lectureCount - 4)
        m.put("WEEKLY_TEACHER_PREFERRED", 3);
        m.put("WEEKLY_REQUIRED_SAME_YEAR_GAPS", 1); // weigher: w * dailyGapPenalty
        m.put("WEEKLY_DIRECTION_GROUP_A", 5);

        // ===== EXAM (ExamConstraintProvider) =====
        // HARD
        m.put("EXAM_TEACHER_CONFLICT", 1);
        m.put("EXAM_REQUIRED_SAME_YEAR_SAME_DAY", 5);
        m.put("EXAM_ROOM_BLOCKED", 10);
        // SOFT
        m.put("EXAM_PREFERRED_ROOM", 4);
        m.put("EXAM_PREFERRED_HOUR", 4);
        m.put("EXAM_SHARED_ROOM_CAPACITY", 1);      // weigher: w * overflow
        m.put("EXAM_SAME_YEAR_SAME_DAY", 6);
        m.put("EXAM_DAILY_LOAD_BALANCE", 1);
        m.put("EXAM_PREFER_DISTINCT_ROOMS", 1);
        m.put("EXAM_REQUIRED_BEFORE_ELECTIVES", 1);
        m.put("EXAM_SPREAD_SAME_YEAR", 3);
        m.put("EXAM_DIRECTION_GROUP_A_SAME_DAY", 5);
        m.put("EXAM_TEACHER_MULTIPLE_SAME_DAY", 2);
        m.put("EXAM_PREFER_MORNING_LARGE", 1);

        return Map.copyOf(m);
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
     * το DB overlay του S4b-2: τα *ConstraintProviderTest το καλούν στο
     * {@code @AfterEach} ώστε ένα test που (μελλοντικά) αλλάζει βάρος να μη
     * «μολύνει» τα επόμενα. Σήμερα (χωρίς overlay) είναι ουσιαστικά no-op.
     */
    public static void resetToDefaults() {
        WEIGHTS.clear();
        WEIGHTS.putAll(DEFAULTS);
    }
}
