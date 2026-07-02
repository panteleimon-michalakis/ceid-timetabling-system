package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.RoomConstraint;
import gr.upatras.ceid.timetable.entity.Timetable;
import gr.upatras.ceid.timetable.entity.TimetableAssignment;
import gr.upatras.ceid.timetable.repository.RoomConstraintRepository;
import gr.upatras.ceid.timetable.repository.TimetableAssignmentRepository;
import gr.upatras.ceid.timetable.repository.TimetableRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E-1 regression: το autoSchedule() είχε unbounded hang (jstack: >5 λεπτά χωρίς να
 * τελειώσει, CPU-bound 100%) επειδή roomBlockedMessage() έκανε ζωντανή repo query
 * ανά (room, slot) μέσα στο τριπλά φωλιασμένο hot loop — κάθε query πυροδοτούσε
 * πλήρες Hibernate auto-flush πάνω σε όλο το persistence context. Fix: buildRoomConstraintMap
 * hoisted πριν το loop (ίδιο pattern με το προϋπάρχον courseTeacherKeyMap). Το test
 * τρέχει σε ρεαλιστικού μεγέθους dataset (DataSeeder: ~58 FALL μαθήματα ενεργά στο
 * dev/test context) ώστε να μην ξαναγυρίσει σιωπηλά το unbounded hang, ΚΑΙ επιβεβαιώνει
 * ότι τα παραγόμενα assignments παραμένουν έγκυρα (κανένα σε BLOCKED room-slot).
 */
@SpringBootTest
class AutoSchedulePerformanceTest {

    private static final String MARK = "TEST_ASPERF_";
    private static final long TIMEOUT_MS = 30_000;

    @Autowired TimetableController timetableController;
    @Autowired TimetableRepository timetableRepo;
    @Autowired TimetableAssignmentRepository assignmentRepo;
    @Autowired RoomConstraintRepository roomConstraintRepo;

    private Long ttId;

    @BeforeEach void before() { cleanup(); seed(); }
    @AfterEach  void after()  { cleanup(); }

    @Test
    void autoSchedule_onFreshFallTimetable_completesWithinTimeoutAndProducesValidAssignments() {
        long start = System.nanoTime();
        ResponseEntity<?> resp = timetableController.autoSchedule(ttId);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(200, resp.getStatusCode().value());
        assertTrue(elapsedMs < TIMEOUT_MS,
                "autoSchedule πήρε " + elapsedMs + "ms (όριο " + TIMEOUT_MS
                        + "ms) — πιθανό E-1 regression (unbounded hang στο roomBlockedMessage)");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertNotNull(body);

        int totalPlaced = (int) body.get("totalPlaced");
        assertTrue(totalPlaced > 0, "πρέπει να τοποθετηθούν ώρες σε ρεαλιστικό dataset, ήταν " + totalPlaced);

        List<TimetableAssignment> assignments = assignmentRepo.findByTimetableId(ttId);
        assertEquals(totalPlaced, assignments.size(), "persisted assignments != totalPlaced");

        // Κανένα assignment δεν πρέπει να πέφτει σε BLOCKED room-constraint slot —
        // ακριβώς ο έλεγχος που το E-1 fix hoisted σε in-memory map.
        for (TimetableAssignment a : assignments) {
            List<RoomConstraint> constraints = roomConstraintRepo.findByRoomId(a.getRoom().getId());
            String day = a.getTimeSlot().getDayOfWeek().name();
            int hour = a.getTimeSlot().getStartTime().getHour();
            boolean blocked = constraints.stream().anyMatch(c ->
                    c.getConstraintType() == RoomConstraint.ConstraintType.BLOCKED
                            && day.equals(c.getDayOfWeek()) && c.getHour() == hour);
            assertFalse(blocked, "assignment id=" + a.getId() + " σε BLOCKED room-slot");
        }
    }

    private void seed() {
        Timetable tt = timetableRepo.save(Timetable.builder()
                .name(MARK + "TT").academicYear("2025-26")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(Timetable.Status.DRAFT).build());
        ttId = tt.getId();
    }

    private void cleanup() {
        timetableRepo.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().startsWith(MARK))
                .forEach(t -> {
                    assignmentRepo.deleteAll(assignmentRepo.findByTimetableId(t.getId()));
                    timetableRepo.deleteById(t.getId());
                });
    }
}
