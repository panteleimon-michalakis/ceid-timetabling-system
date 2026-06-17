package gr.upatras.ceid.timetable.safety;

import gr.upatras.ceid.timetable.controller.RoomController;
import gr.upatras.ceid.timetable.controller.TeacherController;
import gr.upatras.ceid.timetable.entity.*;
import gr.upatras.ceid.timetable.repository.*;
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
 * S1 soft-delete semantics: smart-DELETE (deactivate-vs-hard-delete), toggle
 * endpoints, και active-aware reads. Συμπληρώνει το TransactionalRollbackTest
 * (που καλύπτει την ατομικότητα του hard-delete path).
 *
 * Σκόπιμα ΧΩΡΙΣ @Transactional: τα writes γίνονται commit ώστε το transaction
 * του controller να είναι το πραγματικό unit under test. Καθαρισμός με markers.
 */
@SpringBootTest
class SoftDeleteSemanticsTest {

    private static final String ROOM_A = "TEST_SD_ROOM_A";
    private static final String ROOM_B = "TEST_SD_ROOM_B";
    private static final List<String> TEST_ROOM_CODES = List.of(ROOM_A, ROOM_B);
    private static final String TEACHER_MARKER = "TEST_SD_TEACHER";

    @Autowired RoomController roomController;
    @Autowired TeacherController teacherController;

    @Autowired RoomRepository roomRepo;
    @Autowired TeacherRepository teacherRepo;
    @Autowired CourseTeacherRepository courseTeacherRepo;
    @Autowired TeacherConstraintRepository teacherConstraintRepo;
    @Autowired RoomConstraintRepository roomConstraintRepo;
    @Autowired TimetableAssignmentRepository assignmentRepo;
    @Autowired CourseRepository courseRepo;

    @BeforeEach
    void clean() { cleanup(); }

    @AfterEach
    void cleanAfter() { cleanup(); }

    // ====================================================================
    // Toggle: deactivate -> reactivate
    // ====================================================================
    @Test
    void roomActiveToggle_deactivateThenReactivate() {
        Room room = createRoom(ROOM_A, true);

        ResponseEntity<?> off = roomController.setActive(room.getId(), Map.of("active", Boolean.FALSE));
        assertEquals(200, off.getStatusCode().value());
        assertEquals(Boolean.FALSE, roomRepo.findById(room.getId()).orElseThrow().getActive());

        ResponseEntity<?> on = roomController.setActive(room.getId(), Map.of("active", Boolean.TRUE));
        assertEquals(200, on.getStatusCode().value());
        assertEquals(Boolean.TRUE, roomRepo.findById(room.getId()).orElseThrow().getActive());
    }

    @Test
    void roomActiveToggle_rejectsNonBooleanBody() {
        Room room = createRoom(ROOM_A, true);
        ResponseEntity<?> resp = roomController.setActive(room.getId(), Map.of("active", "nope"));
        assertEquals(400, resp.getStatusCode().value());
        assertEquals(Boolean.TRUE, roomRepo.findById(room.getId()).orElseThrow().getActive(),
                "μη έγκυρο body δεν αλλάζει την κατάσταση");
    }

    // ====================================================================
    // Active-aware solver read: απενεργοποιημένη αίθουσα δεν προσφέρεται
    // ====================================================================
    @Test
    void solverRooms_excludeInactive() {
        Room active = createRoom(ROOM_A, true);
        Room inactive = createRoom(ROOM_B, false);

        List<Long> ids = roomRepo.findByAvailableForSemesterTrueAndActiveTrue()
                .stream().map(Room::getId).toList();

        assertTrue(ids.contains(active.getId()), "η ενεργή αίθουσα προσφέρεται");
        assertFalse(ids.contains(inactive.getId()), "η απενεργοποιημένη αίθουσα ΔΕΝ προσφέρεται");
    }

    // ====================================================================
    // Teacher smart-DELETE: linked -> deactivate, unused -> hard delete
    // ====================================================================
    @Test
    void teacherDelete_whenLinkedToCourses_deactivates_keepsLinks() {
        Teacher teacher = createTeacher("A");
        Course course = courseRepo.findAll().get(0);
        courseTeacherRepo.save(CourseTeacher.builder()
                .course(course).teacher(teacher).role(CourseTeacher.Role.PRIMARY).build());

        ResponseEntity<?> resp = teacherController.delete(teacher.getId());

        assertEquals(200, resp.getStatusCode().value(), "linked -> deactivate (200)");
        assertEquals(Boolean.FALSE, teacherRepo.findById(teacher.getId()).orElseThrow().getActive());
        assertTrue(courseTeacherRepo.existsByTeacherId(teacher.getId()),
                "τα CourseTeacher links διατηρούνται");
    }

    @Test
    void teacherDelete_whenUnused_hardDeletes() {
        Teacher teacher = createTeacher("B");
        teacherConstraintRepo.save(TeacherConstraint.builder()
                .teacher(teacher).dayOfWeek("MONDAY").hour(9)
                .constraintType(TeacherConstraint.ConstraintType.BLOCKED).build());

        ResponseEntity<?> resp = teacherController.delete(teacher.getId());

        assertEquals(204, resp.getStatusCode().value(), "unused -> hard delete (204)");
        assertFalse(teacherRepo.existsById(teacher.getId()), "ο καθηγητής διαγράφηκε");
        assertTrue(teacherConstraintRepo.findByTeacherId(teacher.getId()).isEmpty(),
                "τα constraints διαγράφηκαν ατομικά");
    }

    // ====================================================================
    // Helpers
    // ====================================================================
    private Room createRoom(String code, boolean active) {
        return roomRepo.save(Room.builder()
                .name("Test SoftDelete " + code).code(code).capacity(10)
                .roomType(Room.RoomType.CLASSROOM)
                .hasProjector(false).hasComputers(false)
                .availableForExams(false).availableForSemester(true)
                .active(active).notes("test").build());
    }

    private Teacher createTeacher(String suffix) {
        return teacherRepo.save(Teacher.builder()
                .name(TEACHER_MARKER + "_" + suffix)
                .teacherType(Teacher.TeacherType.PROFESSOR)
                .build());
    }

    private void cleanup() {
        for (String code : TEST_ROOM_CODES) {
            roomRepo.findByCode(code).ifPresent(r -> {
                assignmentRepo.deleteAll(assignmentRepo.findByRoomId(r.getId()));
                roomConstraintRepo.deleteAll(roomConstraintRepo.findByRoomId(r.getId()));
                roomRepo.deleteById(r.getId());
            });
        }
        teacherRepo.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().startsWith(TEACHER_MARKER))
                .forEach(t -> {
                    courseTeacherRepo.deleteAll(courseTeacherRepo.findByTeacherId(t.getId()));
                    teacherConstraintRepo.deleteAll(teacherConstraintRepo.findByTeacherId(t.getId()));
                    teacherRepo.deleteById(t.getId());
                });
    }
}
