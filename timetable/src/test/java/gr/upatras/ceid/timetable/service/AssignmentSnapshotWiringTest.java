package gr.upatras.ceid.timetable.service;

import gr.upatras.ceid.timetable.controller.TimetableController;
import gr.upatras.ceid.timetable.entity.*;
import gr.upatras.ceid.timetable.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wiring test του snapshot-on-write (S3b-2): επιβεβαιώνει ότι ΚΑΘΕ ένα από τα 3
 * controller write-paths καλεί το {@code AssignmentSnapshotStamper.stamp} πριν το
 * save, ώστε η αποθηκευμένη ανάθεση να έχει γεμάτο το 16-πεδιο snapshot — όχι μόνο
 * ένα path. Επιπλέον ότι το move (1522) ξανα-stamp-άρει room/slot στη νέα θέση.
 *
 * Το test course είναι ΓΠ (GENERAL_EDUCATION) + 2ο έτος ώστε να αποφεύγει τους
 * room-κανόνες «1ο έτος → Γ» και «υποχρεωτικά → Β/Γ» και να μπαίνει σε synthetic
 * αίθουσες. Το auto-schedule path απομονώνεται με pre-fill (βλ. μέθοδο) ώστε ο
 * greedy να τοποθετεί ΜΟΝΟ το test course (αλλιώς θα έχτιζε όλο το dataset).
 *
 * Σκόπιμα ΧΩΡΙΣ @Transactional: τα writes των controllers γίνονται commit (το
 * transaction του controller είναι το unit under test). Καθαρισμός με markers.
 */
@SpringBootTest
class AssignmentSnapshotWiringTest {

    private static final String MARK = "TEST_SNAP_";
    private static final String COURSE_CODE = MARK + "C1";
    private static final String ROOM_A = MARK + "RA";
    private static final String ROOM_B = MARK + "RB";

    @Autowired TimetableController controller;
    @Autowired TimetableRepository timetableRepo;
    @Autowired TimetableAssignmentRepository assignmentRepo;
    @Autowired CourseRepository courseRepo;
    @Autowired RoomRepository roomRepo;
    @Autowired TimeSlotRepository timeSlotRepo;

    private Long timetableId;
    private Long courseId;
    private Long roomAId;
    private Long roomBId;
    private Long slot1Id;
    private Long slot2Id;

    @BeforeEach
    void setUp() { cleanup(); seed(); }

    @AfterEach
    void tearDown() { cleanup(); }

    // ── path 298: manual placement ─────────────────────────────────────────────
    @Test
    void placePath_stampsAll16Fields() {
        ResponseEntity<?> resp = controller.addAssignment(timetableId, Map.of(
                "courseId", courseId, "roomId", roomAId, "timeSlotId", slot1Id,
                "assignmentType", "LECTURE"));
        assertEquals(200, resp.getStatusCode().value(), "place: " + resp.getBody());

        List<TimetableAssignment> placed = assignmentRepo.findByTimetableId(timetableId);
        assertEquals(1, placed.size());
        assertSnapshotFull(placed.get(0), ROOM_A, "MONDAY", LocalTime.of(9, 0));
    }

    // ── path 1522: move (re-stamp room/slot) ───────────────────────────────────
    @Test
    void movePath_reStampsRoomAndSlot_courseUnchanged() {
        ResponseEntity<?> place = controller.addAssignment(timetableId, Map.of(
                "courseId", courseId, "roomId", roomAId, "timeSlotId", slot1Id,
                "assignmentType", "LECTURE"));
        assertEquals(200, place.getStatusCode().value(), "place: " + place.getBody());
        Long aId = assignmentRepo.findByTimetableId(timetableId).get(0).getId();

        ResponseEntity<?> move = controller.moveAssignment(aId, Map.of(
                "roomId", roomBId, "timeSlotId", slot2Id));
        assertEquals(200, move.getStatusCode().value(), "move: " + move.getBody());

        TimetableAssignment a = assignmentRepo.findById(aId).orElseThrow();
        // re-stamped στη νέα θέση
        assertEquals(ROOM_B, a.getSnapshotRoomCode());
        assertEquals("FRIDAY", a.getSnapshotDayOfWeek());
        assertEquals(LocalTime.of(13, 0), a.getSnapshotStartTime());
        // course αμετάβλητο
        assertEquals(COURSE_CODE, a.getSnapshotCourseCode());
    }

    // ── path 1964: auto-schedule (bulk) ────────────────────────────────────────
    @Test
    void autoSchedulePath_stampsPlacedAssignment() {
        // Απομόνωση: γεμίζουμε τις ώρες όλων των real courses → ο greedy τα κάνει
        // skip (remaining ≤ 0) και τοποθετεί ΜΟΝΟ το test course (γρήγορα).
        prefillRealCoursesSoTheyAreSkipped();

        ResponseEntity<?> resp = controller.autoSchedule(timetableId);
        assertEquals(200, resp.getStatusCode().value());

        List<TimetableAssignment> testCoursePlaced = assignmentRepo.findByTimetableId(timetableId).stream()
                .filter(a -> a.getCourse() != null && courseId.equals(a.getCourse().getId()))
                .toList();
        assertFalse(testCoursePlaced.isEmpty(), "ο auto-scheduler τοποθέτησε το test course");
        // ΚΑΘΕ bulk-placed ανάθεση του test course έχει stamped snapshot
        for (TimetableAssignment a : testCoursePlaced) {
            assertEquals(COURSE_CODE, a.getSnapshotCourseCode());
            assertNotNull(a.getSnapshotCourseName());
            assertNotNull(a.getSnapshotRoomCode());
            assertNotNull(a.getSnapshotDayOfWeek());
            assertNotNull(a.getSnapshotSlotType());
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────
    private void assertSnapshotFull(TimetableAssignment a, String roomCode, String day, LocalTime start) {
        // Course (6)
        assertEquals(COURSE_CODE, a.getSnapshotCourseCode());
        assertEquals("Snapshot Test Course", a.getSnapshotCourseName());
        assertEquals(3, a.getSnapshotSemester().intValue());
        assertEquals(2, a.getSnapshotStudyYear().intValue());
        assertEquals("GENERAL_EDUCATION", a.getSnapshotCourseType());
        assertEquals("Αντωνόπουλος, Καρβέλης", a.getSnapshotTeachersText());
        // Room (4)
        assertEquals(roomCode, a.getSnapshotRoomCode());
        assertNotNull(a.getSnapshotRoomName());
        assertEquals(200, a.getSnapshotRoomCapacity().intValue());
        assertEquals("AMPHITHEATER", a.getSnapshotRoomType());
        // TimeSlot (6)
        assertEquals(day, a.getSnapshotDayOfWeek());
        assertEquals(start, a.getSnapshotStartTime());
        assertNotNull(a.getSnapshotEndTime());
        assertEquals("SEMESTER", a.getSnapshotSlotType());
        assertNull(a.getSnapshotSpecificDate());
        assertNull(a.getSnapshotExamPeriodLabel());
    }

    /**
     * Γεμίζει τις απαιτούμενες ώρες ΟΛΩΝ των πραγματικών μαθημάτων (όχι του test
     * course) με dummy αναθέσεις, ώστε το auto-schedule να βρει remaining ≤ 0 και
     * να τα προσπεράσει — κρατώντας τον greedy φτηνό. Reuse μίας αίθουσας/slot:
     * το countPlacedHoursForCourseAndType μετράει ανά course+type, όχι ανά κελί.
     */
    private void prefillRealCoursesSoTheyAreSkipped() {
        Timetable t = timetableRepo.findById(timetableId).orElseThrow();
        Room r = roomRepo.findById(roomAId).orElseThrow();
        TimeSlot s = timeSlotRepo.findById(slot1Id).orElseThrow();
        List<TimetableAssignment> batch = new ArrayList<>();
        for (Course c : courseRepo.findAll()) {
            if (COURSE_CODE.equals(c.getCode())) {
                continue; // το test course μένει unplaced ώστε να το τοποθετήσει ο greedy
            }
            addPrefill(batch, t, c, r, s, TimetableAssignment.AssignmentType.LECTURE, c.getLectureHours());
            addPrefill(batch, t, c, r, s, TimetableAssignment.AssignmentType.TUTORIAL, c.getTutorialHours());
            addPrefill(batch, t, c, r, s, TimetableAssignment.AssignmentType.LAB, c.getLabHours());
        }
        assignmentRepo.saveAll(batch); // ένα batch αντί ~266 ξεχωριστά commits
    }

    private void addPrefill(List<TimetableAssignment> batch, Timetable t, Course c, Room r, TimeSlot s,
                            TimetableAssignment.AssignmentType type, Integer hours) {
        int n = hours == null ? 0 : Math.max(0, hours);
        for (int i = 0; i < n; i++) {
            batch.add(TimetableAssignment.builder()
                    .timetable(t).course(c).room(r).timeSlot(s)
                    .assignmentType(type).isLocked(false).manuallyAssigned(true)
                    .createdAt(LocalDateTime.now()).build());
        }
    }

    private void seed() {
        timetableId = timetableRepo.save(Timetable.builder()
                .name(MARK + "TT").academicYear("2025-26")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(Timetable.Status.DRAFT)
                .build()).getId();

        courseId = courseRepo.save(Course.builder()
                .code(COURSE_CODE).name("Snapshot Test Course")
                .semester(3).studyYear(2)
                .courseType(Course.CourseType.GENERAL_EDUCATION)
                .lectureHours(2).tutorialHours(0).labHours(0)
                .expectedStudents(10)
                .teachersText("Καρβέλης, Αντωνόπουλος")
                .semesterType(Course.SemesterType.FALL)
                .active(true).visibleInTimetable(true)
                .build()).getId();

        roomAId = roomRepo.save(testRoom(ROOM_A)).getId();
        roomBId = roomRepo.save(testRoom(ROOM_B)).getId();
        slot1Id = timeSlotRepo.save(testSlot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))).getId();
        slot2Id = timeSlotRepo.save(testSlot(DayOfWeek.FRIDAY, LocalTime.of(13, 0), LocalTime.of(14, 0))).getId();
    }

    private Room testRoom(String code) {
        return Room.builder()
                .name("Snap " + code).code(code).capacity(200)
                .roomType(Room.RoomType.AMPHITHEATER)
                .hasProjector(false).hasComputers(false)
                .availableForExams(false).availableForSemester(true)
                .active(true).build();
    }

    private TimeSlot testSlot(DayOfWeek day, LocalTime start, LocalTime end) {
        return TimeSlot.builder()
                .dayOfWeek(day).startTime(start).endTime(end)
                .slotType(TimeSlot.SlotType.SEMESTER).build();
    }

    private void cleanup() {
        // assignments των test προγραμμάτων πρώτα (FK), μετά τα προγράμματα
        timetableRepo.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().startsWith(MARK))
                .forEach(t -> {
                    assignmentRepo.deleteAll(assignmentRepo.findByTimetableId(t.getId()));
                    timetableRepo.deleteById(t.getId());
                });
        roomRepo.findByCode(ROOM_A).ifPresent(r -> roomRepo.deleteById(r.getId()));
        roomRepo.findByCode(ROOM_B).ifPresent(r -> roomRepo.deleteById(r.getId()));
        courseRepo.findAll().stream()
                .filter(c -> COURSE_CODE.equals(c.getCode()))
                .forEach(c -> courseRepo.deleteById(c.getId()));
        for (Long id : new Long[]{slot1Id, slot2Id}) {
            if (id != null) {
                timeSlotRepo.findById(id).ifPresent(s -> timeSlotRepo.deleteById(s.getId()));
            }
        }
    }
}
