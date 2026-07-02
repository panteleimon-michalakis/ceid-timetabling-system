package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.Room;
import gr.upatras.ceid.timetable.entity.Teacher;
import gr.upatras.ceid.timetable.repository.RoomRepository;
import gr.upatras.ceid.timetable.repository.TeacherRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 1 (soft-delete list filtering): τα GET list endpoints
 * ({@link RoomController#getAll} / {@link TeacherController#getAll}) ΔΕΝ
 * επιστρέφουν soft-deleted (active=false) οντότητες — συνεπές με τα active-aware
 * reads του solver/placement και με τα μαθήματα (findByDeletedFalse).
 *
 * Integration (@SpringBootTest, πραγματική DB): καλεί απευθείας τους controllers
 * (τα getAll ΕΙΝΑΙ οι handlers των GET /api/rooms|teachers). Marker-based
 * seed/cleanup, ΧΩΡΙΣ @Transactional ώστε τα writes να είναι το πραγματικό state.
 */
@SpringBootTest
class SoftDeleteListFilteringTest {

    private static final String ACTIVE_ROOM   = "TEST_SDLF_ROOM_ON";
    private static final String INACTIVE_ROOM = "TEST_SDLF_ROOM_OFF";
    private static final String TEACHER_MARK  = "TEST_SDLF_TEACHER";
    private static final String ACTIVE_TEACHER   = TEACHER_MARK + "_ON";
    private static final String INACTIVE_TEACHER = TEACHER_MARK + "_OFF";

    @Autowired RoomController roomController;
    @Autowired TeacherController teacherController;
    @Autowired RoomRepository roomRepo;
    @Autowired TeacherRepository teacherRepo;

    @BeforeEach void before() { cleanup(); seed(); }
    @AfterEach  void after()  { cleanup(); }

    @Test
    void getAllRooms_excludesInactive() {
        List<Room> rooms = roomController.getAll();
        assertTrue(rooms.stream().anyMatch(r -> ACTIVE_ROOM.equals(r.getCode())),
                "η ενεργή αίθουσα εμφανίζεται στη λίστα");
        assertFalse(rooms.stream().anyMatch(r -> INACTIVE_ROOM.equals(r.getCode())),
                "η soft-deleted (active=false) αίθουσα ΔΕΝ εμφανίζεται");
    }

    @Test
    void getAllTeachers_excludesInactive() {
        List<Teacher> teachers = teacherController.getAll();
        assertTrue(teachers.stream().anyMatch(t -> ACTIVE_TEACHER.equals(t.getName())),
                "ο ενεργός καθηγητής εμφανίζεται στη λίστα");
        assertFalse(teachers.stream().anyMatch(t -> INACTIVE_TEACHER.equals(t.getName())),
                "ο soft-deleted (active=false) καθηγητής ΔΕΝ εμφανίζεται");
    }

    private void seed() {
        roomRepo.save(room(ACTIVE_ROOM, true));
        roomRepo.save(room(INACTIVE_ROOM, false));
        teacherRepo.save(teacher(ACTIVE_TEACHER, true));
        teacherRepo.save(teacher(INACTIVE_TEACHER, false));
    }

    private Room room(String code, boolean active) {
        return Room.builder()
                .name("SDLF " + code).code(code).capacity(10)
                .roomType(Room.RoomType.CLASSROOM)
                .hasProjector(false).hasComputers(false)
                .availableForExams(false).availableForSemester(true)
                .active(active).build();
    }

    private Teacher teacher(String name, boolean active) {
        return Teacher.builder()
                .name(name).teacherType(Teacher.TeacherType.PROFESSOR)
                .active(active).build();
    }

    private void cleanup() {
        for (String code : List.of(ACTIVE_ROOM, INACTIVE_ROOM)) {
            roomRepo.findByCode(code).ifPresent(r -> roomRepo.deleteById(r.getId()));
        }
        teacherRepo.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().startsWith(TEACHER_MARK))
                .forEach(t -> teacherRepo.deleteById(t.getId()));
    }
}
