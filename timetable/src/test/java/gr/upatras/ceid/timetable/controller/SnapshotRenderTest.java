package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.*;
import gr.upatras.ceid.timetable.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3d — render-from-snapshot: το {@code assignmentToDto} επικαλύπτει snapshot-first
 * τα 16 display πεδία πάνω στα live sub-DTOs. Πρώτο ΟΡΑΤΟ αποτέλεσμα του snapshot
 * invariant (#1): τα προγράμματα δείχνουν ό,τι αποτυπώθηκε κατά την τοποθέτηση,
 * ανέπαφα σε μετέπειτα αλλαγή/soft-delete των master δεδομένων.
 *
 * Καλύπτει: (1) live master ΑΛΛΑΞΕ (rename) → δείχνεται snapshot, όχι live· (2)
 * live soft-deleted (active=false) + rename → snapshot· (3) null live entity
 * (διαγραμμένο master — το nullable=false FK δεν το επιτρέπει στη ΒΔ, οπότε direct
 * call) → display χτισμένο από snapshot χωρίς NPE, id=null.
 *
 * Στα (1)/(2) αλλάζουμε ΜΟΝΟ το name (όχι code) ώστε το marker/code-based cleanup
 * να παραμένει αξιόπιστο μετά το rename. Σκόπιμα ΧΩΡΙΣ @Transactional: τα writes
 * γίνονται commit (render διαβάζει committed state)· καθαρισμός με markers.
 */
@SpringBootTest
class SnapshotRenderTest {

    private static final String MARK = "TEST_S3D_";
    private static final String COURSE_CODE = MARK + "C1";
    private static final String ROOM_CODE = MARK + "R1";
    private static final String COURSE_NAME = "S3d Snapshot Course";
    private static final String ROOM_NAME = "S3d Snapshot Room";

    @Autowired TimetableController controller;
    @Autowired TimetableRepository timetableRepo;
    @Autowired TimetableAssignmentRepository assignmentRepo;
    @Autowired CourseRepository courseRepo;
    @Autowired RoomRepository roomRepo;
    @Autowired TimeSlotRepository timeSlotRepo;

    private Long timetableId;
    private Long courseId;
    private Long roomId;
    private Long slotId;

    @BeforeEach
    void setUp() { cleanup(); seed(); }

    @AfterEach
    void tearDown() { cleanup(); }

    // ── (1) live master ΑΛΛΑΞΕ (rename) → snapshot-first δείχνει το frozen ──────────
    @Test
    void renamedLiveCourse_rendersSnapshotName_keepsLiveId() {
        place();

        Course c = courseRepo.findById(courseId).orElseThrow();
        c.setName("RENAMED LIVE — δεν πρέπει να φανεί");
        courseRepo.save(c);

        Map<String, Object> course = subDto("course");
        assertEquals(COURSE_NAME, course.get("name"),
                "snapshot-first: δείχνεται το frozen name, ΟΧΙ το renamed live");
        assertEquals(COURSE_CODE, course.get("code"));
        assertEquals(courseId, course.get("id"), "το id/FK ΜΕΝΕΙ live");
    }

    // ── (2) live soft-deleted (active=false) + rename → snapshot ────────────────────
    @Test
    void softDeletedAndRenamedLiveRoom_rendersSnapshot_keepsLiveId() {
        place();

        Room r = roomRepo.findById(roomId).orElseThrow();
        r.setActive(false);
        r.setName("RENAMED ROOM — δεν πρέπει να φανεί");
        roomRepo.save(r);

        Map<String, Object> room = subDto("room");
        assertEquals(ROOM_NAME, room.get("name"),
                "snapshot-first: soft-deleted+renamed room δείχνει snapshot name");
        assertEquals(ROOM_CODE, room.get("code"));
        assertEquals(roomId, room.get("id"), "το id/FK ΜΕΝΕΙ live");
    }

    // ── (3) null live entity (διαγραμμένο master) → display από snapshot, id=null ───
    @Test
    @SuppressWarnings("unchecked")
    void nullLiveMasters_buildDisplayFromSnapshot_noNpe() {
        // In-memory ανάθεση χωρίς live course/room/timeSlot (το nullable=false FK δεν
        // επιτρέπει null στη ΒΔ — εδώ ελέγχουμε καθαρά το null-safe branch του overlay).
        TimetableAssignment a = TimetableAssignment.builder()
                .id(999L)
                .assignmentType(TimetableAssignment.AssignmentType.LECTURE)
                .manuallyAssigned(true).isLocked(false)
                .snapshotCourseCode("SNAP_C").snapshotCourseName("Snap Course")
                .snapshotSemester(5).snapshotStudyYear(3)
                .snapshotCourseType("ELECTIVE").snapshotTeachersText("Α. Παπαδόπουλος")
                .snapshotRoomCode("SNAP_R").snapshotRoomName("Snap Room")
                .snapshotRoomCapacity(120).snapshotRoomType("CLASSROOM")
                .snapshotDayOfWeek("WEDNESDAY")
                .snapshotStartTime(LocalTime.of(11, 0)).snapshotEndTime(LocalTime.of(13, 0))
                .snapshotSlotType("SEMESTER")
                .build();

        Map<String, Object> dto = controller.assignmentToDto(a);

        assertEquals(999L, dto.get("id"));

        Map<String, Object> course = (Map<String, Object>) dto.get("course");
        assertNotNull(course, "null live course + snapshot → map από snapshot");
        assertNull(course.get("id"), "live FK χάθηκε → id null");
        assertEquals("SNAP_C", course.get("code"));
        assertEquals("Snap Course", course.get("name"));
        assertEquals(5, course.get("semester"));
        assertEquals(3, course.get("studyYear"));
        assertEquals("ELECTIVE", course.get("courseType"));
        assertEquals("Α. Παπαδόπουλος", course.get("teachersText"));

        Map<String, Object> room = (Map<String, Object>) dto.get("room");
        assertNull(room.get("id"));
        assertEquals("SNAP_R", room.get("code"));
        assertEquals("Snap Room", room.get("name"));
        assertEquals(120, room.get("capacity"));
        assertEquals("CLASSROOM", room.get("roomType"));

        Map<String, Object> ts = (Map<String, Object>) dto.get("timeSlot");
        assertNull(ts.get("id"));
        assertEquals("WEDNESDAY", ts.get("dayOfWeek"));
        assertEquals("11:00", ts.get("startTime"), "LocalTime → toString ταιριάζει με το live format");
        assertEquals("13:00", ts.get("endTime"));
        assertEquals("SEMESTER", ts.get("slotType"));
    }

    // ── (4) χωρίς αλλαγή master: snapshot == live → ίδιο αποτέλεσμα (μη-regression) ──
    @Test
    void unchangedMaster_rendersSameValues() {
        place();
        Map<String, Object> course = subDto("course");
        assertEquals(COURSE_NAME, course.get("name"));
        assertEquals(COURSE_CODE, course.get("code"));
        assertEquals(courseId, course.get("id"));
    }

    // ── helpers ────────────────────────────────────────────────────────────────────
    private void place() {
        ResponseEntity<?> resp = controller.addAssignment(timetableId, Map.of(
                "courseId", courseId, "roomId", roomId, "timeSlotId", slotId,
                "assignmentType", "LECTURE"));
        assertEquals(200, resp.getStatusCode().value(), "place: " + resp.getBody());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> subDto(String key) {
        ResponseEntity<?> resp = controller.getAssignments(timetableId);
        assertEquals(200, resp.getStatusCode().value());
        List<Map<String, Object>> list = (List<Map<String, Object>>) resp.getBody();
        assertNotNull(list);
        assertEquals(1, list.size(), "μία ανάθεση");
        Map<String, Object> sub = (Map<String, Object>) list.get(0).get(key);
        assertNotNull(sub, key + " sub-dto");
        return sub;
    }

    private void seed() {
        timetableId = timetableRepo.save(Timetable.builder()
                .name(MARK + "TT").academicYear("2025-26")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(Timetable.Status.DRAFT)
                .build()).getId();

        // GENERAL_EDUCATION + 2ο έτος + AMPHITHEATER ώστε το addAssignment να μην
        // απορρίπτεται από room-κανόνες (ίδια λογική με το AssignmentSnapshotWiringTest).
        courseId = courseRepo.save(Course.builder()
                .code(COURSE_CODE).name(COURSE_NAME)
                .semester(3).studyYear(2)
                .courseType(Course.CourseType.GENERAL_EDUCATION)
                .lectureHours(2).tutorialHours(0).labHours(0)
                .expectedStudents(10)
                .teachersText("Καρβέλης")
                .semesterType(Course.SemesterType.FALL)
                .active(true).visibleInTimetable(true)
                .build()).getId();

        roomId = roomRepo.save(Room.builder()
                .name(ROOM_NAME).code(ROOM_CODE).capacity(200)
                .roomType(Room.RoomType.AMPHITHEATER)
                .hasProjector(false).hasComputers(false)
                .availableForExams(false).availableForSemester(true)
                .active(true).build()).getId();

        slotId = timeSlotRepo.save(TimeSlot.builder()
                .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
                .slotType(TimeSlot.SlotType.SEMESTER).build()).getId();
    }

    private void cleanup() {
        timetableRepo.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().startsWith(MARK))
                .forEach(t -> {
                    assignmentRepo.deleteAll(assignmentRepo.findByTimetableId(t.getId()));
                    timetableRepo.deleteById(t.getId());
                });
        roomRepo.findByCode(ROOM_CODE).ifPresent(r -> roomRepo.deleteById(r.getId()));
        courseRepo.findAll().stream()
                .filter(c -> COURSE_CODE.equals(c.getCode()))
                .forEach(c -> courseRepo.deleteById(c.getId()));
        if (slotId != null) {
            timeSlotRepo.findById(slotId).ifPresent(s -> timeSlotRepo.deleteById(s.getId()));
        }
    }
}
