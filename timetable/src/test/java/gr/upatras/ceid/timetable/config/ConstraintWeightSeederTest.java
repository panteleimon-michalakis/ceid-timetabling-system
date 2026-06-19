package gr.upatras.ceid.timetable.config;

import gr.upatras.ceid.timetable.entity.ConstraintWeightConfig;
import gr.upatras.ceid.timetable.repository.ConstraintWeightConfigRepository;
import gr.upatras.ceid.timetable.solver.SolverWeights;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S4b-2a — {@link ConstraintWeightSeeder}: seed του {@code constraint_weight_config}
 * από το {@link SolverWeights#catalog()} με insert-if-absent.
 *
 * Σκόπιμα ΧΩΡΙΣ @Transactional (ίδιο με SnapshotBackfillRunnerTest): τα writes
 * γίνονται commit ώστε το direct {@code seeder.run(...)} να βλέπει committed state.
 * @BeforeEach/@AfterEach επαναφέρουν τον πίνακα σε καθαρά seeded defaults.
 */
@SpringBootTest
class ConstraintWeightSeederTest {

    private static final String EDIT_KEY = "WEEKLY_ROOM_CONFLICT";

    @Autowired ConstraintWeightSeeder seeder;
    @Autowired ConstraintWeightConfigRepository repo;

    @BeforeEach
    void cleanSeed() {
        repo.deleteAll();
        seeder.run(new DefaultApplicationArguments());
    }

    @AfterEach
    void restoreDefaults() {
        repo.deleteAll();
        seeder.run(new DefaultApplicationArguments());
    }

    @Test
    void cleanDb_seedsEveryCatalogRowWithDefaults() {
        // Καμία ορφανή/λείπουσα γραμμή: ένα row ανά catalog Def.
        assertEquals(31, SolverWeights.catalog().size(), "ο κατάλογος έχει 31 κανόνες");
        assertEquals(SolverWeights.catalog().size(), repo.count(),
                "ένα persisted row ανά catalog Def");

        // Μοναδικότητα keys (κανένα διπλό).
        long distinctKeys = SolverWeights.catalog().stream()
                .map(SolverWeights.Def::key).distinct().count();
        assertEquals(31, distinctKeys, "όλα τα catalog keys μοναδικά");

        for (SolverWeights.Def d : SolverWeights.catalog()) {
            ConstraintWeightConfig row = repo.findByConstraintKey(d.key())
                    .orElseThrow(() -> new AssertionError("λείπει seeded key: " + d.key()));
            assertEquals(d.defaultWeight(), row.getWeight(), "default weight για " + d.key());
            assertEquals(d.scope(), row.getScope(), "scope για " + d.key());
            assertEquals(d.level(), row.getScoreLevel(), "level για " + d.key());
            assertEquals(d.displayName(), row.getDisplayName(), "displayName για " + d.key());
            assertTrue(row.isEnabled(), "enabled by default για " + d.key());
            assertNotNull(row.getCreatedAt(), "createdAt set για " + d.key());
            assertNull(row.getParams(), "params κενό στο 2a για " + d.key());
        }
    }

    @Test
    void rerun_isIdempotent_andPreservesManualEdits() {
        long before = repo.count();

        // Admin edit: χειροκίνητη αλλαγή ενός βάρους.
        ConstraintWeightConfig edited = repo.findByConstraintKey(EDIT_KEY).orElseThrow();
        int bumped = edited.getWeight() + 99;
        edited.setWeight(bumped);
        repo.save(edited);

        // Re-run του seeder.
        seeder.run(new DefaultApplicationArguments());

        assertEquals(before, repo.count(), "re-run: καμία νέα γραμμή (insert-if-absent)");
        assertEquals(bumped,
                repo.findByConstraintKey(EDIT_KEY).orElseThrow().getWeight(),
                "re-run ΔΕΝ επαναφέρει admin edit (κανένα UPDATE)");
    }

    @Test
    void catalogKeys_matchPersistedKeys_noOrphans() {
        var catalogKeys = SolverWeights.catalog().stream()
                .map(SolverWeights.Def::key).collect(Collectors.toSet());
        var persistedKeys = repo.findAll().stream()
                .map(ConstraintWeightConfig::getConstraintKey).collect(Collectors.toSet());
        assertEquals(catalogKeys, persistedKeys,
                "τα persisted keys ταυτίζονται με τα catalog keys (κανένα ορφανό/λείπον)");
    }
}
