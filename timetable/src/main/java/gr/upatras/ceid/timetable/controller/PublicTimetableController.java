package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.Timetable;
import gr.upatras.ceid.timetable.repository.TimetableAssignmentRepository;
import gr.upatras.ceid.timetable.repository.TimetableRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Account-less, read-only δημόσια πρόσβαση ΜΟΝΟ σε δημοσιευμένα (PUBLISHED)
 * προγράμματα. Namespace {@code /api/public/timetables} — permitAll ΜΟΝΟ για GET
 * στο {@code SecurityConfig}. Δεν αγγίζει τα authenticated endpoints του
 * {@link TimetableController}.
 *
 * Επαναχρησιμοποιεί το package-private {@code assignmentToDto} του
 * {@link TimetableController} (ίδιο package, νόμιμη κλήση) → ίδια snapshot-first
 * render λογική με την authenticated προβολή, χωρίς αντιγραφή.
 */
@RestController
@RequestMapping("/api/public/timetables")
public class PublicTimetableController {

    private final TimetableRepository timetableRepo;
    private final TimetableAssignmentRepository assignmentRepo;
    private final TimetableController timetableController;

    public PublicTimetableController(TimetableRepository timetableRepo,
                                     TimetableAssignmentRepository assignmentRepo,
                                     TimetableController timetableController) {
        this.timetableRepo = timetableRepo;
        this.assignmentRepo = assignmentRepo;
        this.timetableController = timetableController;
    }

    /** Λίστα ΜΟΝΟ δημοσιευμένων προγραμμάτων (νεότερα δημοσιευμένα πρώτα). */
    @GetMapping
    public List<PublicTimetableDto> getPublished() {
        Sort sort = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
        return timetableRepo.findByStatus(Timetable.Status.PUBLISHED, sort).stream()
                .map(PublicTimetableDto::from)
                .toList();
    }

    /**
     * Αναθέσεις ΜΟΝΟ δημοσιευμένου προγράμματος. Επιστρέφει 404 αν το πρόγραμμα δεν
     * υπάρχει Ή δεν είναι PUBLISHED (404 και για DRAFT — ώστε να μη διαρρέει η ύπαρξη
     * ή το περιεχόμενο μη-δημόσιου προγράμματος). Αλλιώς 200 με snapshot-first DTOs.
     */
    @GetMapping("/{id}/assignments")
    public ResponseEntity<?> getPublishedAssignments(@PathVariable Long id) {
        var timetableOpt = timetableRepo.findById(id);
        if (timetableOpt.isEmpty()
                || timetableOpt.get().getStatus() != Timetable.Status.PUBLISHED) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> result = assignmentRepo.findByTimetableId(id).stream()
                .map(timetableController::assignmentToDto)
                .toList();

        return ResponseEntity.ok(result);
    }
}
