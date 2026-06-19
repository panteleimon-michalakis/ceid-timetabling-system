package gr.upatras.ceid.timetable.config;

import gr.upatras.ceid.timetable.entity.ConstraintWeightConfig;
import gr.upatras.ceid.timetable.repository.ConstraintWeightConfigRepository;
import gr.upatras.ceid.timetable.solver.SolverWeights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * S4b-2a: γεμίζει τον πίνακα {@code constraint_weight_config} από το
 * {@link SolverWeights#catalog()} (single source). <b>Insert-if-absent</b>:
 * κάθε υπάρχον row (πιθανό admin edit) ΔΕΝ πειράζεται — κανένα UPDATE — ώστε
 * αλλαγές βαρών να ΜΗΝ χάνονται σε restart. Idempotent / re-runnable: σε ήδη-
 * seeded ή CI-clean-then-seeded βάση τα 31 rows μένουν άθικτα.
 *
 * <p>Ίδιο idiom με {@code SnapshotBackfillRunner}. {@code @Order(50)}: μετά τον
 * {@code UserSeeder} (@Order 1), ανεξάρτητο από τον {@code SnapshotBackfillRunner}
 * (@Order 100). ΠΡΟΣΟΧΗ (S4b-2a): ο solver ΔΕΝ διαβάζει ακόμα αυτά τα βάρη.
 */
@Component
@Order(50)
public class ConstraintWeightSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConstraintWeightSeeder.class);

    private final ConstraintWeightConfigRepository repo;

    public ConstraintWeightSeeder(ConstraintWeightConfigRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(ApplicationArguments args) {
        int seeded = 0;
        for (SolverWeights.Def d : SolverWeights.catalog()) {
            if (repo.existsByConstraintKey(d.key())) {
                continue; // insert-if-absent: υπάρχον row (πιθανό admin edit) δεν πειράζεται
            }
            repo.save(ConstraintWeightConfig.builder()
                    .constraintKey(d.key())
                    .scope(d.scope())
                    .scoreLevel(d.level())
                    .weight(d.defaultWeight())
                    .enabled(true)
                    .displayName(d.displayName())
                    .description(d.description())
                    .build());
            seeded++;
        }
        if (seeded > 0) {
            log.info("ConstraintWeightSeeder: seeded {} new constraint weights.", seeded);
        }
    }
}
