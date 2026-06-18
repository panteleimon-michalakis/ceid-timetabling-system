package gr.upatras.ceid.timetable.config;

import gr.upatras.ceid.timetable.entity.*;
import gr.upatras.ceid.timetable.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3e — {@link SnapshotBackfillRunner}: γεμίζει το snapshot σε αναθέσεις
 * γραμμένες πριν το snapshot-on-write, ΜΟΝΟ όπου {@code snapshot_course_code IS NULL},
 * μέσω του ΙΔΙΟΥ stamper (single source). ΔΕΝ αγγίζει frozen snapshots· idempotent.
 *
 * Σκόπιμα ΧΩΡΙΣ @Transactional: τα fixtures γίνονται commit ώστε το direct
 * {@code runner.run(...)} να βλέπει/γράφει committed state· καθαρισμός με markers.
 */
@SpringBootTest
class SnapshotBackfillRunnerTest {

    private static final String MARK = "TEST_S3E_";
    private static final String LIVE_CODE = MARK + "LIVE";       // τρέχον live course code
    private static final String FROZEN_CODE = MARK + "FROZEN";   // υπάρχον snapshot, διαφορετικό από live

    @Autowired SnapshotBackfillRunner runner;
    @Autowired TimetableRepository timetableRepo;
    @Autowired TimetableAssignmentRepository assignmentRepo;
    @Autowired CourseRepository courseRepo;
    @Autowired RoomRepository roomRepo;
    @Autowired TimeSlotRepository timeSlotRepo;

    private Long timetableId;
    private Long courseId;
    private Long roomId;
    private Long slotId;
    private Long nullSnapId;     // A1: χωρίς snapshot → πρέπει να backfill-αριστεί
    private Long frozenSnapId;   // A2: με υπάρχον snapshot → πρέπει να μείνει frozen

    @BeforeEach
    void setUp() { cleanup(); seed(); }

    @AfterEach
    void tearDown() { cleanup(); }

    @Test
    void backfillsOnlyNullSnapshots_preservesFrozen_andIsIdempotent() {
        // precondition: A1 null, A2 frozen
        assertNull(assignmentRepo.findById(nullSnapId).orElseThrow().getSnapshotCourseCode(),
                "precondition: A1 χωρίς snapshot");
        assertEquals(FROZEN_CODE, assignmentRepo.findById(frozenSnapId).orElseThrow().getSnapshotCourseCode(),
                "precondition: A2 με frozen snapshot");

        runner.run(new DefaultApplicationArguments());

        // A1: backfilled από τα ΤΡΕΧΟΝΤΑ live (course code = LIVE_CODE) + room/slot ομάδες
        TimetableAssignment a1 = assignmentRepo.findById(nullSnapId).orElseThrow();
        assertEquals(LIVE_CODE, a1.getSnapshotCourseCode(), "A1 backfilled από live course code");
        assertEquals("Live Course Name", a1.getSnapshotCourseName());
        assertNotNull(a1.getSnapshotRoomCode(), "A1 room snapshot backfilled");
        assertNotNull(a1.getSnapshotDayOfWeek(), "A1 slot snapshot backfilled");

        // A2: frozen ΔΕΝ ξανα-stamp-αρίστηκε (ΟΧΙ refresh στο live LIVE_CODE) — το core του S3e
        TimetableAssignment a2 = assignmentRepo.findById(frozenSnapId).orElseThrow();
        assertEquals(FROZEN_CODE, a2.getSnapshotCourseCode(),
                "frozen snapshot ΔΕΝ πρέπει να ξανα-stamp-αριστεί στις live τιμές");
        assertEquals("Frozen Name", a2.getSnapshotCourseName());

        // idempotent: 2η εκτέλεση = no-op (A1 ήδη non-null → εκτός query, A2 frozen αμετάβλητο)
        runner.run(new DefaultApplicationArguments());
        assertEquals(LIVE_CODE, assignmentRepo.findById(nullSnapId).orElseThrow().getSnapshotCourseCode(),
                "idempotent: A1 αμετάβλητο");
        assertEquals(FROZEN_CODE, assignmentRepo.findById(frozenSnapId).orElseThrow().getSnapshotCourseCode(),
                "idempotent: A2 αμετάβλητο");
    }

    private void seed() {
        timetableId = timetableRepo.save(Timetable.builder()
                .name(MARK + "TT").academicYear("2025-26")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(Timetable.Status.DRAFT)
                .build()).getId();

        courseId = courseRepo.save(Course.builder()
                .code(LIVE_CODE).name("Live Course Name")
                .semester(3).studyYear(2)
                .courseType(Course.CourseType.GENERAL_EDUCATION)
                .lectureHours(2).tutorialHours(0).labHours(0)
                .expectedStudents(10).teachersText("Καρβέλης")
                .semesterType(Course.SemesterType.FALL)
                .active(true).visibleInTimetable(true)
                .build()).getId();

        roomId = roomRepo.save(Room.builder()
                .name("S3E Room").code(MARK + "R1").capacity(50)
                .roomType(Room.RoomType.CLASSROOM)
                .hasProjector(false).hasComputers(false)
                .availableForExams(false).availableForSemester(true)
                .active(true).build()).getId();

        slotId = timeSlotRepo.save(TimeSlot.builder()
                .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
                .slotType(TimeSlot.SlotType.SEMESTER).build()).getId();

        Course course = courseRepo.findById(courseId).orElseThrow();
        Room room = roomRepo.findById(roomId).orElseThrow();
        TimeSlot slot = timeSlotRepo.findById(slotId).orElseThrow();
        Timetable tt = timetableRepo.findById(timetableId).orElseThrow();

        // A1: ΧΩΡΙΣ snapshot (direct builder save — bypass stamper)
        nullSnapId = assignmentRepo.save(TimetableAssignment.builder()
                .timetable(tt).course(course).room(room).timeSlot(slot)
                .assignmentType(TimetableAssignment.AssignmentType.LECTURE)
                .isLocked(false).manuallyAssigned(true)
                .createdAt(LocalDateTime.now())
                .build()).getId();

        // A2: με ΥΠΑΡΧΟΝ snapshot (διαφορετικό από το live) → frozen, εκτός backfill query
        frozenSnapId = assignmentRepo.save(TimetableAssignment.builder()
                .timetable(tt).course(course).room(room).timeSlot(slot)
                .assignmentType(TimetableAssignment.AssignmentType.LECTURE)
                .isLocked(false).manuallyAssigned(true)
                .createdAt(LocalDateTime.now())
                .snapshotCourseCode(FROZEN_CODE).snapshotCourseName("Frozen Name")
                .build()).getId();
    }

    private void cleanup() {
        timetableRepo.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().startsWith(MARK))
                .forEach(t -> {
                    assignmentRepo.deleteAll(assignmentRepo.findByTimetableId(t.getId()));
                    timetableRepo.deleteById(t.getId());
                });
        roomRepo.findByCode(MARK + "R1").ifPresent(r -> roomRepo.deleteById(r.getId()));
        courseRepo.findAll().stream()
                .filter(c -> LIVE_CODE.equals(c.getCode()))
                .forEach(c -> courseRepo.deleteById(c.getId()));
        if (slotId != null) {
            timeSlotRepo.findById(slotId).ifPresent(s -> timeSlotRepo.deleteById(s.getId()));
        }
    }
}
