package gr.upatras.ceid.timetable.config;

import gr.upatras.ceid.timetable.entity.TimetableAssignment;
import gr.upatras.ceid.timetable.repository.TimetableAssignmentRepository;
import gr.upatras.ceid.timetable.service.AssignmentSnapshotStamper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * S3e: backfill του snapshot σε αναθέσεις γραμμένες ΠΡΙΝ το snapshot-on-write
 * ({@code snapshot_course_code IS NULL}). Γεμίζει μέσω του ΙΔΙΟΥ
 * {@link AssignmentSnapshotStamper} (single source με τα live write-paths και το
 * render του S3d), ώστε backfilled == newly-stamped — η SQL ΔΕΝ αναπαράγει το
 * {@code normalizeTeachersTextForDto} (βλ. S3a απόφαση: backfill σε Java, όχι στο migration).
 *
 * <p><b>ΚΡΙΣΙΜΟ:</b> ΜΟΝΟ null-snapshot rows. Rows με υπάρχον snapshot ΔΕΝ
 * ξανα-stamp-άρονται — αλλιώς το frozen snapshot θα γινόταν refresh στις ΤΡΕΧΟΥΣΕΣ
 * live τιμές, σπάζοντας το freeze (invariant #1). Idempotent / re-runnable: σε
 * καθαρή ή ήδη-backfilled βάση = no-op (καμία εγγραφή → καμία επίδραση στο
 * Flyway/ddl-validate του startup).
 */
@Component
@Order(100) // μετά τους seeders (UserSeeder @Order(1)· ο DataSeeder δεν φτιάχνει αναθέσεις)
public class SnapshotBackfillRunner implements ApplicationRunner {

    private final TimetableAssignmentRepository assignmentRepo;
    private final AssignmentSnapshotStamper snapshotStamper;

    public SnapshotBackfillRunner(TimetableAssignmentRepository assignmentRepo,
                                  AssignmentSnapshotStamper snapshotStamper) {
        this.assignmentRepo = assignmentRepo;
        this.snapshotStamper = snapshotStamper;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<TimetableAssignment> toBackfill = assignmentRepo.findBySnapshotCourseCodeIsNull();
        if (toBackfill.isEmpty()) {
            return; // καθαρή/CI ή ήδη-backfilled βάση → no-op
        }

        toBackfill.forEach(snapshotStamper::stamp);
        assignmentRepo.saveAll(toBackfill);

        System.out.println(">>> SnapshotBackfillRunner: backfilled "
                + toBackfill.size() + " αναθέσεις χωρίς snapshot.");
    }
}
