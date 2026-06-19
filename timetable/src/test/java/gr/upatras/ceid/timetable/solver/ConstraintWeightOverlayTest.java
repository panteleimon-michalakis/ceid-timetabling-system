package gr.upatras.ceid.timetable.solver;

import gr.upatras.ceid.timetable.entity.ConstraintWeightConfig;
import gr.upatras.ceid.timetable.repository.ConstraintWeightConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * S4b-2b — επικάλυψη persisted βαρών στο {@link SolverWeights} μέσω
 * {@link SolverService#applyConstraintWeightOverrides()}. Επικυρώνει την
 * κλειδωμένη policy: <b>HARD-floor</b> (ποτέ disable/μηδέν → fallback default)
 * vs <b>SOFT-disable</b> (→ βάρος 0).
 *
 * Τα rows μεταλλάσσονται και ΕΠΑΝΑΦΕΡΟΝΤΑΙ ανά test (save+restore στο finally)
 * ώστε να μη μένει μόνιμο edit στη dev DB· {@code @AfterEach} επαναφέρει και το
 * in-memory SolverWeights (να μη «μολύνει» τα 52 verifier tests).
 */
@SpringBootTest
class ConstraintWeightOverlayTest {

    private static final String SOFT_KEY = "WEEKLY_PREFER_NORMAL_HOURS"; // default 2
    private static final String HARD_KEY = "WEEKLY_REQUIRED_SAME_YEAR";  // default 5
    private static final int SOFT_DEFAULT = 2;
    private static final int HARD_DEFAULT = 5;

    @Autowired SolverService solverService;
    @Autowired ConstraintWeightConfigRepository cwcRepo;

    @AfterEach
    void resetInMemoryWeights() {
        SolverWeights.resetToDefaults();
    }

    // ---- NO-OP: seeded defaults → baseline αμετάβλητο ----

    @Test
    void seededDefaults_overlayIsNoOp() {
        solverService.applyConstraintWeightOverrides();
        assertEquals(SOFT_DEFAULT, SolverWeights.w(SOFT_KEY), "SOFT default αμετάβλητο");
        assertEquals(HARD_DEFAULT, SolverWeights.w(HARD_KEY), "HARD default αμετάβλητο");
        assertEquals(10, SolverWeights.w("WEEKLY_TEACHER_BLOCKED"), "HARD default 10");
        assertEquals(6, SolverWeights.w("EXAM_SAME_YEAR_SAME_DAY"), "SOFT default 6");
    }

    // ---- SOFT: weight change + disable ----

    @Test
    void softWeightChange_isApplied() {
        withRow(SOFT_KEY, r -> r.setWeight(99), () ->
                assertEquals(99, SolverWeights.w(SOFT_KEY), "SOFT weight override εφαρμόζεται"));
    }

    @Test
    void softDisable_zeroesRule() {
        withRow(SOFT_KEY, r -> r.setEnabled(false), () ->
                assertEquals(0, SolverWeights.w(SOFT_KEY), "disabled SOFT → βάρος 0"));
    }

    // ---- HARD floor: ποτέ disable/μηδέν ----

    @Test
    void hardDisable_fallsBackToDefault_neverDropped() {
        withRow(HARD_KEY, r -> r.setEnabled(false), () ->
                assertEquals(HARD_DEFAULT, SolverWeights.w(HARD_KEY),
                        "disabled HARD → default (ΟΧΙ 0)"));
    }

    @Test
    void hardZeroWeight_fallsBackToDefault() {
        withRow(HARD_KEY, r -> r.setWeight(0), () ->
                assertEquals(HARD_DEFAULT, SolverWeights.w(HARD_KEY),
                        "HARD weight<1 → default"));
    }

    @Test
    void hardRaise_isApplied() {
        withRow(HARD_KEY, r -> r.setWeight(8), () ->
                assertEquals(8, SolverWeights.w(HARD_KEY), "HARD raise (enabled, >=1) εφαρμόζεται"));
    }

    /** Μετάλλαξη ενός row → overlay → assertion → επαναφορά original (save+restore). */
    private void withRow(String key, Consumer<ConstraintWeightConfig> mutator, Runnable assertion) {
        ConstraintWeightConfig row = cwcRepo.findByConstraintKey(key).orElseThrow();
        int origWeight = row.getWeight();
        boolean origEnabled = row.isEnabled();
        mutator.accept(row);
        cwcRepo.save(row);
        try {
            solverService.applyConstraintWeightOverrides();
            assertion.run();
        } finally {
            ConstraintWeightConfig back = cwcRepo.findByConstraintKey(key).orElseThrow();
            back.setWeight(origWeight);
            back.setEnabled(origEnabled);
            cwcRepo.save(back);
        }
    }
}
