package gr.upatras.ceid.timetable.safety;

import gr.upatras.ceid.timetable.controller.RoomController;
import gr.upatras.ceid.timetable.controller.TimetableController;
import gr.upatras.ceid.timetable.entity.*;
import gr.upatras.ceid.timetable.repository.*;
import gr.upatras.ceid.timetable.solver.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Data-integrity guardrail (Φ0 [0.2]): επιβεβαιώνει ότι bulk/multi-record write
 * operations είναι ΑΤΟΜΙΚΑ — exception στη μέση -> πλήρες rollback, η βάση μένει
 * ΑΜΕΤΑΒΛΗΤΗ (count/state πριν == μετά).
 *
 * Σκόπιμα ΧΩΡΙΣ @Transactional στην κλάση: τα fixtures γίνονται commit, ώστε το
 * transaction του controller/service να είναι το πραγματικό unit under test και
 * οι έλεγχοι να βλέπουν committed state. Καθαρισμός με markers σε @BeforeEach/
 * @AfterEach ώστε να μη μένουν test δεδομένα στη βάση.
 */
@SpringBootTest
class TransactionalRollbackTest {

    private static final String TT_NAME = "Test Rollback TT";
    private static final List<String> TEST_ROOM_CODES =
            List.of("TEST_RB_ROOM_A", "TEST_RB_ROOM_B", "TEST_RB_ROOM_C");
    private static final int TEST_YEAR = 2099;

    @Autowired TimetableController timetableController;
    @Autowired RoomController roomController;
    @Autowired SolverService solverService;
    @Autowired SolutionPersistenceService solutionPersistence;

    @Autowired RoomConstraintRepository constraintRepo;
    @Autowired CourseRepository courseRepo;

    // Spied ώστε να μπορούμε να προκαλέσουμε exception στη μέση ενός bulk op.
    @MockitoSpyBean TimetableRepository timetableRepo;
    @MockitoSpyBean RoomRepository roomRepo;
    @MockitoSpyBean TimeSlotRepository timeSlotRepo;
    @MockitoSpyBean TimetableAssignmentRepository assignmentRepo;

    @BeforeEach
    void clean() { cleanupTestData(); }

    @AfterEach
    void cleanAfter() { cleanupTestData(); }

    // ====================================================================
    // A1: DELETE /api/timetables/{id} — deleteAll(assignments) + deleteById
    // ====================================================================
    @Test
    void timetableDelete_rollsBack_onFailure_dbUnchanged() {
        Timetable t = createTimetable();
        Room seededRoom = roomRepo.findAll().get(0);
        createAssignment(t, seededRoom);
        createAssignment(t, seededRoom);
        createAssignment(t, seededRoom);

        long before = assignmentRepo.findByTimetableId(t.getId()).size();
        assertEquals(3, before, "precondition: 3 αναθέσεις");

        // Αποτυχία στο 2ο write (delete του timetable), αφού έχουν ήδη
        // «διαγραφεί» οι αναθέσεις μέσα στο ίδιο transaction.
        doThrow(new RuntimeException("boom-delete-timetable"))
                .when(timetableRepo).deleteById(t.getId());

        assertThrows(RuntimeException.class, () -> timetableController.delete(t.getId()));

        // ΑΜΕΤΑΒΛΗΤΗ βάση: timetable + όλες οι αναθέσεις παραμένουν.
        assertTrue(timetableRepo.existsById(t.getId()), "το timetable δεν πρέπει να διαγράφηκε");
        assertEquals(before, assignmentRepo.findByTimetableId(t.getId()).size(),
                "καμία ανάθεση δεν πρέπει να χάθηκε (rollback)");
    }

    // ====================================================================
    // A2: SolverService.generateExamSlotsForTimetable — loop save(slot)
    // ====================================================================
    @Test
    void examSlotGeneration_rollsBack_onMidLoopFailure_noSlotsPersisted() {
        Timetable t = createTimetable();
        t.setStartDate(LocalDate.of(TEST_YEAR, 6, 1));
        t.setEndDate(LocalDate.of(TEST_YEAR, 6, 7));
        t = timetableRepo.save(t);

        long before = countTestExamSlots();
        assertEquals(0, before, "precondition: κανένα test exam slot");

        // Exception στη ΜΕΣΗ του loop: αποτυγχάνει στο 3ο slot της ημέρας (15:00),
        // αφού έχουν ήδη αποθηκευτεί τα 9:00 & 12:00 στο ίδιο transaction.
        doAnswer(inv -> {
            TimeSlot s = inv.getArgument(0);
            if (s.getStartTime() != null && s.getStartTime().getHour() == 15) {
                throw new RuntimeException("boom-mid-loop-15:00");
            }
            return inv.callRealMethod();
        }).when(timeSlotRepo).save(any());

        final Timetable target = t;
        assertThrows(RuntimeException.class,
                () -> solverService.generateExamSlotsForTimetable(target));

        assertEquals(before, countTestExamSlots(),
                "κανένα exam slot δεν πρέπει να παρέμεινε μετά το rollback");
    }

    // ====================================================================
    // A3 (S3c/BL-1): SolutionPersistenceService.persist — deleteAll(old) +
    // loop save(new). Exception στη μέση -> πλήρες rollback: ΟΥΤΕ οι παλιές
    // αναθέσεις χάνονται ΟΥΤΕ μένουν μερικές νέες (το core του BL-1: το παλιό
    // private saveSolution έτρεχε σε auto-commit -> μη ατομικό).
    // ====================================================================
    @Test
    void solverPersistence_rollsBack_onMidSaveFailure_noPartialWrite() {
        Timetable t = createTimetable();
        Room room = roomRepo.findAll().get(0);

        // Προϋπάρχουσα AUTO ανάθεση: ο στόχος του deleteAll μέσα στο persist.
        TimetableAssignment preExisting = createAutoAssignment(t, room);
        Long preExistingId = preExisting.getId();
        assertEquals(1, assignmentRepo.findByTimetableId(t.getId()).size(),
                "precondition: 1 auto ανάθεση committed");

        // Λύση με 2 placed lessons -> 2 saves μέσα στο persist.
        CeidTimetable solution = solutionWithTwoPlacedLessons(room);

        // Σκάσιμο (unchecked) στο 2ο save: το deleteAll(old) + το 1ο save(new) έχουν
        // ήδη εκτελεστεί στο ΙΔΙΟ tx -> πρέπει ΟΛΑ να αναιρεθούν (default rollback
        // του @Transactional σε RuntimeException).
        AtomicInteger saves = new AtomicInteger(0);
        doAnswer(inv -> {
            if (saves.incrementAndGet() == 2) {
                throw new RuntimeException("boom-mid-persist-save-2");
            }
            return inv.callRealMethod();
        }).when(assignmentRepo).save(any());

        assertThrows(RuntimeException.class,
                () -> solutionPersistence.persist(t, solution, 0L));

        // ΑΤΟΜΙΚΟΤΗΤΑ: η παλιά ανάθεση επανήλθε (deleteAll αναιρέθηκε), καμία μερική
        // νέα δεν έμεινε (διαφορετικά το count θα ήταν 2 — το ακριβές BL-1 bug).
        List<TimetableAssignment> after = assignmentRepo.findByTimetableId(t.getId());
        assertEquals(1, after.size(),
                "rollback: ακριβώς η προϋπάρχουσα ανάθεση, καμία μερική νέα εγγραφή");
        assertEquals(preExistingId, after.get(0).getId(),
                "ίδιο row — το deleteAll αναιρέθηκε, δεν είναι νέα ανάθεση");

        // Το status save (τέλος του persist) αναιρέθηκε/δεν εκτελέστηκε.
        Timetable reloaded = timetableRepo.findById(t.getId()).orElseThrow();
        assertNotEquals(Timetable.Status.SOLVED, reloaded.getStatus(),
                "το timetable δεν πρέπει να έμεινε SOLVED μετά το rollback");
    }

    // ====================================================================
    // A4 (S3c): επιτυχές persist -> ο 4ος (solver) write-path γράφει snapshot,
    // συμμετρικά με place/move/auto-schedule (AssignmentSnapshotWiringTest).
    // ====================================================================
    @Test
    void solverPersistence_stampsSnapshot_onSuccessfulPersist() {
        Timetable t = createTimetable();
        Room room = roomRepo.findAll().get(0);
        Course course = courseRepo.findAll().get(0);
        TimeSlot slot = timeSlotRepo.findAll().get(0);

        SolverRoom sRoom = new SolverRoom(room.getId(), room.getCode(), room.getCapacity(),
                room.getRoomType() != null ? room.getRoomType().name() : "CLASSROOM");
        SolverTimeSlot sSlot = new SolverTimeSlot(slot.getId(), "MONDAY", 9);
        Lesson l = new Lesson(1L, course.getId(), course.getCode(), course.getName(),
                1, "REQUIRED", "LECTURE", 50, "FALL", 1);
        l.setRoom(sRoom);
        l.setTimeSlot(sSlot);
        CeidTimetable solution = new CeidTimetable(List.of(sSlot), List.of(sRoom), List.of(l));

        solutionPersistence.persist(t, solution, 0L);

        List<TimetableAssignment> saved = assignmentRepo.findByTimetableId(t.getId());
        assertEquals(1, saved.size(), "1 ανάθεση αποθηκεύτηκε");
        TimetableAssignment a = saved.get(0);
        assertEquals(course.getCode(), a.getSnapshotCourseCode(),
                "ο solver path πρέπει να γράφει snapshot course code (όπως place/move/auto)");
        assertEquals(course.getName(), a.getSnapshotCourseName(), "snapshot course name");
        assertEquals(room.getCode(), a.getSnapshotRoomCode(), "snapshot room code");
    }

    // ====================================================================
    // B1a (S1): DELETE room σε χρήση -> soft-delete (deactivate), ΤΙΠΟΤΑ δεν
    // σβήνεται. Νέα σημασιολογία soft-delete αντί του παλιού 409 hard-delete.
    // ====================================================================
    @Test
    void roomDelete_whenInUse_deactivates_keepsData() {
        Room room = createRoom("TEST_RB_ROOM_A");
        createConstraint(room);
        Timetable t = createTimetable();
        createAssignment(t, room); // η αίθουσα είναι πλέον σε χρήση

        ResponseEntity<?> resp = roomController.delete(room.getId());

        assertEquals(200, resp.getStatusCode().value(), "deactivate -> 200, όχι 409/204");
        Room reloaded = roomRepo.findById(room.getId()).orElseThrow();
        assertEquals(Boolean.FALSE, reloaded.getActive(), "η αίθουσα απενεργοποιήθηκε");
        assertEquals(1, constraintRepo.findByRoomId(room.getId()).size(),
                "τα constraints ΔΕΝ πρέπει να σβήστηκαν");
        assertTrue(assignmentRepo.existsByRoomId(room.getId()), "η ανάθεση παραμένει");
    }

    // ====================================================================
    // B1b: DELETE ελεύθερης room -> 204, room + constraints σβήνονται ατομικά
    // ====================================================================
    @Test
    void roomDelete_whenFree_deletesRoomAndConstraints() {
        Room room = createRoom("TEST_RB_ROOM_B");
        createConstraint(room);

        ResponseEntity<?> resp = roomController.delete(room.getId());

        assertEquals(204, resp.getStatusCode().value());
        assertFalse(roomRepo.existsById(room.getId()), "η αίθουσα διαγράφηκε");
        assertTrue(constraintRepo.findByRoomId(room.getId()).isEmpty(), "τα constraints διαγράφηκαν");
    }

    // ====================================================================
    // B1c: DELETE ελεύθερης room αλλά αποτυγχάνει το delete -> full rollback
    // ====================================================================
    @Test
    void roomDelete_whenFree_butDeleteFails_rollsBack_dbUnchanged() {
        Room room = createRoom("TEST_RB_ROOM_C");
        createConstraint(room);

        // Η bulk διαγραφή constraints εκτελείται πρώτη· αν αποτύχει το roomRepo
        // delete, πρέπει να αναιρεθεί ΚΑΙ η διαγραφή των constraints.
        doThrow(new RuntimeException("boom-delete-room"))
                .when(roomRepo).deleteById(room.getId());

        assertThrows(RuntimeException.class, () -> roomController.delete(room.getId()));

        assertTrue(roomRepo.existsById(room.getId()), "η αίθουσα παραμένει");
        assertEquals(1, constraintRepo.findByRoomId(room.getId()).size(),
                "η διαγραφή των constraints αναιρέθηκε (atomic rollback)");
    }

    // ====================================================================
    // Helpers
    // ====================================================================
    private Timetable createTimetable() {
        Timetable t = timetableRepo.save(Timetable.builder()
                .name(TT_NAME).academicYear("2099-00")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(Timetable.Status.DRAFT)
                .createdAt(LocalDateTime.now())
                .build());
        return t;
    }

    private TimetableAssignment createAssignment(Timetable t, Room room) {
        Course course = courseRepo.findAll().get(0);
        TimeSlot slot = timeSlotRepo.findAll().get(0);
        return assignmentRepo.save(TimetableAssignment.builder()
                .timetable(t).course(course).room(room).timeSlot(slot)
                .assignmentType(TimetableAssignment.AssignmentType.LECTURE)
                .isLocked(false).manuallyAssigned(true)
                .createdAt(LocalDateTime.now())
                .build());
    }

    /** AUTO ανάθεση (manuallyAssigned=false, isLocked=false) — στόχος του solver deleteAll. */
    private TimetableAssignment createAutoAssignment(Timetable t, Room room) {
        Course course = courseRepo.findAll().get(0);
        TimeSlot slot = timeSlotRepo.findAll().get(0);
        return assignmentRepo.save(TimetableAssignment.builder()
                .timetable(t).course(course).room(room).timeSlot(slot)
                .assignmentType(TimetableAssignment.AssignmentType.LECTURE)
                .isLocked(false).manuallyAssigned(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    /** Ελάχιστη solver λύση με 2 placed lessons (πραγματικά course/room/slot ids). */
    private CeidTimetable solutionWithTwoPlacedLessons(Room room) {
        Course course = courseRepo.findAll().get(0);
        List<TimeSlot> slots = timeSlotRepo.findAll();
        TimeSlot s1 = slots.get(0);
        TimeSlot s2 = slots.size() > 1 ? slots.get(1) : slots.get(0);

        SolverRoom sRoom = new SolverRoom(room.getId(), room.getCode(), room.getCapacity(),
                room.getRoomType() != null ? room.getRoomType().name() : "CLASSROOM");
        SolverTimeSlot sSlot1 = new SolverTimeSlot(s1.getId(), "MONDAY", 9);
        SolverTimeSlot sSlot2 = new SolverTimeSlot(s2.getId(), "MONDAY", 10);

        Lesson l1 = new Lesson(1L, course.getId(), course.getCode(), course.getName(),
                1, "REQUIRED", "LECTURE", 50, "FALL", 1);
        l1.setRoom(sRoom);
        l1.setTimeSlot(sSlot1);
        Lesson l2 = new Lesson(2L, course.getId(), course.getCode(), course.getName(),
                1, "REQUIRED", "LECTURE", 50, "FALL", 1);
        l2.setRoom(sRoom);
        l2.setTimeSlot(sSlot2);

        return new CeidTimetable(List.of(sSlot1, sSlot2), List.of(sRoom), List.of(l1, l2));
    }

    private Room createRoom(String code) {
        return roomRepo.save(Room.builder()
                .name("Test Rollback " + code).code(code).capacity(10)
                .roomType(Room.RoomType.CLASSROOM)
                .hasProjector(false).hasComputers(false)
                .availableForExams(false).availableForSemester(true)
                .notes("test").build());
    }

    private RoomConstraint createConstraint(Room room) {
        return constraintRepo.save(RoomConstraint.builder()
                .room(room).dayOfWeek("MONDAY").hour(9)
                .constraintType(RoomConstraint.ConstraintType.BLOCKED)
                .build());
    }

    private long countTestExamSlots() {
        return timeSlotRepo.findAll().stream()
                .filter(s -> s.getSlotType() == TimeSlot.SlotType.EXAM)
                .filter(s -> s.getSpecificDate() != null && s.getSpecificDate().getYear() == TEST_YEAR)
                .count();
    }

    /** Marker-based καθαρισμός — ασφαλής να τρέξει πριν & μετά κάθε test. */
    private void cleanupTestData() {
        reset(timetableRepo, roomRepo, timeSlotRepo, assignmentRepo); // καθάρισε stubs πριν τα writes

        for (String code : TEST_ROOM_CODES) {
            roomRepo.findByCode(code).ifPresent(r -> {
                assignmentRepo.deleteAll(assignmentRepo.findByRoomId(r.getId()));
                constraintRepo.deleteAll(constraintRepo.findByRoomId(r.getId()));
                roomRepo.deleteById(r.getId());
            });
        }

        timetableRepo.findAll().stream()
                .filter(tt -> TT_NAME.equals(tt.getName()))
                .forEach(tt -> {
                    assignmentRepo.deleteAll(assignmentRepo.findByTimetableId(tt.getId()));
                    timetableRepo.deleteById(tt.getId());
                });

        List<TimeSlot> examSlots = timeSlotRepo.findAll().stream()
                .filter(s -> s.getSlotType() == TimeSlot.SlotType.EXAM)
                .filter(s -> s.getSpecificDate() != null && s.getSpecificDate().getYear() == TEST_YEAR)
                .toList();
        if (!examSlots.isEmpty()) timeSlotRepo.deleteAll(examSlots);
    }
}
