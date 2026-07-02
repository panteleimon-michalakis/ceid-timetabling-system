package gr.upatras.ceid.timetable.solver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E-6 regression: {@link SolverWeights} χρησιμοποιούσε ένα κοινό static mutable
 * HashMap για τα βάρη· δύο ταυτόχρονα solve/analyze requests (SolverService.solve()/
 * analyzeHardViolations() τρέχουν σε request threads, χωρίς SolverManager/
 * moveThreadCount) έκαναν race στο μη-ατομικό resetToDefaults() (clear()+putAll())
 * έναντι w()/applyOverride() άλλου thread — reader έβρισκε άδειο map →
 * IllegalArgumentException("Unknown constraint weight key: ..."), ενώ writer-vs-
 * writer race έκανε applyOverride() να αγνοεί σιωπηλά valid keys ως "unknown".
 * Fix: {@link ThreadLocal} ανά thread αντί για κοινό static map. Το test τρέχει
 * πολλά reader/writer threads ταυτόχρονα για μερικά δευτερόλεπτα και επιβεβαιώνει
 * ΚΑΝΕΝΑ exception — ώστε το race να μην ξαναγυρίσει σιωπηλά.
 */
class SolverWeightsConcurrencyTest {

    @AfterEach
    void resetInMemoryWeights() {
        SolverWeights.resetToDefaults();
    }

    @Test
    void concurrentResetOverrideAndRead_neverThrows() throws InterruptedException {
        AtomicBoolean stop = new AtomicBoolean(false);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        int writerCount = 3;
        int readerCount = 5;
        CountDownLatch ready = new CountDownLatch(writerCount + readerCount);
        CountDownLatch go = new CountDownLatch(1);

        Runnable writer = () -> {
            ready.countDown();
            await(go);
            try {
                while (!stop.get()) {
                    SolverWeights.resetToDefaults();
                    for (SolverWeights.Def d : SolverWeights.catalog()) {
                        SolverWeights.applyOverride(d.key(), d.defaultWeight());
                    }
                }
            } catch (Throwable t) {
                failures.add(t);
            }
        };

        Runnable reader = () -> {
            ready.countDown();
            await(go);
            try {
                while (!stop.get()) {
                    SolverWeights.w("WEEKLY_PREFER_NORMAL_HOURS");
                    SolverWeights.w("WEEKLY_TEACHER_BLOCKED");
                    SolverWeights.w("EXAM_SAME_YEAR_SAME_DAY");
                }
            } catch (Throwable t) {
                failures.add(t);
            }
        };

        List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < writerCount; i++) threads.add(new Thread(writer, "writer-" + i));
        for (int i = 0; i < readerCount; i++) threads.add(new Thread(reader, "reader-" + i));
        threads.forEach(Thread::start);

        ready.await();
        go.countDown();
        Thread.sleep(2000);
        stop.set(true);
        for (Thread t : threads) t.join(5000);

        assertTrue(failures.isEmpty(),
                "Expected no exceptions from concurrent SolverWeights access, got: " + failures);
    }

    /**
     * Sanity (E236 ΒΗΜΑ 4): η ThreadLocal αλλαγή δεν επηρεάζει τιμές σε κανονικό
     * (μη-concurrent, single-thread) σενάριο — reset+override+read στο ίδιο thread
     * συμπεριφέρεται ακριβώς όπως πριν.
     */
    @Test
    void singleThreaded_resetAndOverride_behavesAsBefore() {
        SolverWeights.resetToDefaults();
        assertEquals(2, SolverWeights.w("WEEKLY_PREFER_NORMAL_HOURS"), "default αμετάβλητο");

        SolverWeights.applyOverride("WEEKLY_PREFER_NORMAL_HOURS", 99);
        assertEquals(99, SolverWeights.w("WEEKLY_PREFER_NORMAL_HOURS"), "override εφαρμόζεται");

        SolverWeights.resetToDefaults();
        assertEquals(2, SolverWeights.w("WEEKLY_PREFER_NORMAL_HOURS"), "reset επαναφέρει default");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
