package gr.upatras.ceid.timetable.config;

import gr.upatras.ceid.timetable.entity.Timetable;
import gr.upatras.ceid.timetable.repository.TimetableRepository;
import gr.upatras.ceid.timetable.service.TimetableScopeService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * #5 backfill: για κάθε ΥΠΑΡΧΟΝ πρόγραμμα χωρίς scope rows, υλοποιεί scope από
 * τον ΣΗΜΕΡΙΝΟ relevant κατάλογο (best-available· το ιστορικό scope δεν υπάρχει).
 * Μη-regressive: τα warnings υπαρχόντων δεν αλλάζουν τη στιγμή του backfill — από
 * εκεί και μετά σταματά η διαρροή. Idempotent (existsByTimetableId guard): σε
 * ήδη-backfilled/καθαρή βάση = no-op.
 */
@Component
@Order(101) // μετά τον SnapshotBackfillRunner (@Order(100))
public class TimetableScopeBackfillRunner implements ApplicationRunner {

    private final TimetableRepository timetableRepo;
    private final TimetableScopeService scopeService;

    public TimetableScopeBackfillRunner(TimetableRepository timetableRepo,
                                        TimetableScopeService scopeService) {
        this.timetableRepo = timetableRepo;
        this.scopeService = scopeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Timetable> all = timetableRepo.findAll();
        int filled = 0;
        for (Timetable t : all) {
            if (scopeService.materializeScopeIfAbsent(t) > 0) filled++;
        }
        if (filled > 0) {
            System.out.println(">>> TimetableScopeBackfillRunner: υλοποιήθηκε scope σε "
                    + filled + " προγράμματα.");
        }
    }
}
