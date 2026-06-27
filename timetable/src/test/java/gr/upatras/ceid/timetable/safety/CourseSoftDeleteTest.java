package gr.upatras.ceid.timetable.safety;

import gr.upatras.ceid.timetable.controller.CourseController;
import gr.upatras.ceid.timetable.controller.TeacherController;
import gr.upatras.ceid.timetable.entity.*;
import gr.upatras.ceid.timetable.repository.*;
import gr.upatras.ceid.timetable.service.TimetableScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #4 — Course soft-delete (Option B). Το delete θέτει {@code deleted=true} αντί
 * για hard delete: το μάθημα φεύγει από τον ζωντανό κατάλογο (listing/solver/
 * νέο scope/teacher views) αλλά η γραμμή μένει → υπάρχοντα προγράμματα το κρατούν
 * ακέραιο (S3 snapshot + #5 frozen scope) και το association resolution μένει
 * αφιλτράριστο (καμία global @SQLRestriction).
 *
 * Σκόπιμα ΧΩΡΙΣ @Transactional: τα writes γίνονται commit ώστε το πραγματικό
 * transaction του controller/service να είναι το unit under test· καθαρισμός με
 * MARK prefix.
 */
@SpringBootTest
class CourseSoftDeleteTest {

    private static final String MARK = "TEST_SOFTDEL_";

    @Autowired CourseController courseController;
    @Autowired TeacherController teacherController;
    @Autowired TimetableScopeService scopeService;

    @Autowired CourseRepository courseRepo;
    @Autowired TeacherRepository teacherRepo;
    @Autowired CourseTeacherRepository courseTeacherRepo;
    @Autowired TimetableRepository timetableRepo;
    @Autowired TimetableAssignmentRepository assignmentRepo;
    @Autowired TimetableScopedCourseRepository scopedCourseRepo;
    @Autowired RoomRepository roomRepo;
    @Autowired TimeSlotRepository timeSlotRepo;

    private Long createdTimeSlotId;

    @BeforeEach
    void clean() { cleanup(); }

    @AfterEach
    void cleanAfter() { cleanup(); }

    // ====================================================================
    // 1. soft not hard: DELETE → 204, η γραμμή μένει με deleted=true
    // ====================================================================
    @Test
    void delete_isSoftNotHard() {
        Course c = saveCourse("C1");

        ResponseEntity<Void> resp = courseController.delete(c.getId());

        assertEquals(204, resp.getStatusCode().value(), "delete -> 204");
        Course reloaded = courseRepo.findById(c.getId()).orElseThrow();
        assertTrue(Boolean.TRUE.equals(reloaded.getDeleted()),
                "η γραμμή μένει με deleted=true (soft, όχι hard)");
    }

    // ====================================================================
    // 2. hidden from catalog: live listing ΔΕΝ το περιέχει
    // ====================================================================
    @Test
    void softDeleted_hiddenFromLiveCatalog() {
        Course c = saveCourse("C2");
        courseController.delete(c.getId());

        assertTrue(courseRepo.findByDeletedFalse().stream().noneMatch(x -> c.getId().equals(x.getId())),
                "findByDeletedFalse εξαιρεί το deleted");
        assertTrue(courseController.getAll().stream().noneMatch(m -> c.getId().equals(toLong(m.get("id")))),
                "GET /courses (DTO) εξαιρεί το deleted");
    }

    // ====================================================================
    // 3. resolvable by id: findById το επιστρέφει (για υπάρχουσες αναθέσεις)
    // ====================================================================
    @Test
    void softDeleted_stillResolvableById() {
        Course c = saveCourse("C3");
        courseController.delete(c.getId());

        assertTrue(courseRepo.findById(c.getId()).isPresent(),
                "findById επιστρέφει το deleted (αφιλτράριστο association resolution)");
    }

    // ====================================================================
    // 4. excluded from new scope: πρόγραμμα ΜΕΤΑ το delete δεν το παγώνει
    // ====================================================================
    @Test
    void softDeleted_excludedFromNewTimetableScope() {
        Course c = saveCourse("C4");
        courseController.delete(c.getId());

        Timetable fresh = seedTimetableWithScope("FRESH");

        assertTrue(scopedCourseRepo.findByTimetableId(fresh.getId()).stream()
                        .noneMatch(s -> c.getId().equals(s.getCourseId())),
                "νέο πρόγραμμα (μετά το delete) ΔΕΝ παγώνει το deleted μάθημα");
    }

    // ====================================================================
    // 5. existing timetable intact: scope rows + αναθέσεις ΠΑΡΑΜΕΝΟΥΝ
    // ====================================================================
    @Test
    void existingTimetable_keepsCourseIntactAfterDelete() {
        Course c = saveCourse("C5");
        Timetable old = seedTimetableWithScope("OLD"); // scope frozen ΠΡΙΝ το delete (περιέχει το c)
        TimetableAssignment a = createAssignment(old, c);

        assertTrue(scopedCourseRepo.findByTimetableId(old.getId()).stream()
                        .anyMatch(s -> c.getId().equals(s.getCourseId())),
                "precondition: το μάθημα στο frozen scope");

        courseController.delete(c.getId());

        // scope row παραμένει (membership immutability — #5 frozen scope)
        assertTrue(scopedCourseRepo.findByTimetableId(old.getId()).stream()
                        .anyMatch(s -> c.getId().equals(s.getCourseId())),
                "η scoped row παραμένει μετά το soft-delete");
        // ανάθεση παραμένει και το (deleted) μάθημα ακόμη resolves — ΟΧΙ global filter
        TimetableAssignment reloaded = assignmentRepo.findById(a.getId()).orElseThrow();
        assertNotNull(reloaded.getCourse(), "η ανάθεση κρατά αφιλτράριστο το course association");
        assertEquals(c.getId(), reloaded.getCourse().getId(),
                "η ανάθεση παραμένει συνδεδεμένη με το soft-deleted μάθημα (hard delete θα είχε σπάσει στο FK)");
    }

    // ====================================================================
    // 6. teacher view excludes: GET /teachers/{id}/courses χωρίς το deleted
    // ====================================================================
    @Test
    void teacherCoursesView_excludesSoftDeleted() {
        Course c = saveCourse("C6");
        Teacher t = saveTeacher("T6");
        courseTeacherRepo.save(CourseTeacher.builder()
                .course(c).teacher(t).role(CourseTeacher.Role.PRIMARY).build());

        courseController.delete(c.getId());

        ResponseEntity<?> resp = teacherController.getTeacherCourses(t.getId());
        assertEquals(200, resp.getStatusCode().value());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) resp.getBody();
        assertNotNull(courses);
        assertTrue(courses.stream().noneMatch(m -> c.getId().equals(toLong(m.get("courseId")))),
                "GET /teachers/{id}/courses εξαιρεί το soft-deleted μάθημα");
    }

    // ====================================================================
    // Helpers
    // ====================================================================
    private Course saveCourse(String suffix) {
        return courseRepo.save(Course.builder()
                .code(MARK + suffix).name("Soft Delete Course " + suffix)
                .semester(1).studyYear(1)
                .courseType(Course.CourseType.REQUIRED)
                .lectureHours(2).tutorialHours(0).labHours(0)
                .expectedStudents(10)
                .semesterType(Course.SemesterType.FALL)
                .active(true).visibleInTimetable(true)
                .build());
    }

    private Teacher saveTeacher(String suffix) {
        return teacherRepo.save(Teacher.builder()
                .name(MARK + suffix)
                .teacherType(Teacher.TeacherType.PROFESSOR)
                .build());
    }

    private Timetable seedTimetableWithScope(String suffix) {
        Timetable tt = timetableRepo.save(Timetable.builder()
                .name(MARK + suffix).academicYear("2025-26")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(Timetable.Status.DRAFT)
                .createdAt(LocalDateTime.now())
                .build());
        scopeService.materializeScopeIfAbsent(tt);
        return tt;
    }

    private TimetableAssignment createAssignment(Timetable tt, Course course) {
        Room room = roomRepo.save(Room.builder()
                .name("Soft Delete Room").code(MARK + "R5").capacity(50)
                .roomType(Room.RoomType.CLASSROOM)
                .hasProjector(false).hasComputers(false)
                .availableForExams(false).availableForSemester(true)
                .active(true).build());
        TimeSlot slot = timeSlotRepo.save(TimeSlot.builder()
                .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
                .slotType(TimeSlot.SlotType.SEMESTER).build());
        createdTimeSlotId = slot.getId();
        return assignmentRepo.save(TimetableAssignment.builder()
                .timetable(tt).course(course).room(room).timeSlot(slot)
                .assignmentType(TimetableAssignment.AssignmentType.LECTURE)
                .isLocked(false).manuallyAssigned(true)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private static Long toLong(Object o) {
        return (o instanceof Number n) ? n.longValue() : null;
    }

    private void cleanup() {
        // 1. marked προγράμματα: scope rows + αναθέσεις + πρόγραμμα
        timetableRepo.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().startsWith(MARK))
                .forEach(t -> {
                    scopedCourseRepo.deleteAll(scopedCourseRepo.findByTimetableId(t.getId()));
                    assignmentRepo.deleteAll(assignmentRepo.findByTimetableId(t.getId()));
                    timetableRepo.deleteById(t.getId());
                });
        // 2. marked μαθήματα: M2M links + (hard) η γραμμή
        courseRepo.findAll().stream()
                .filter(c -> c.getCode() != null && c.getCode().startsWith(MARK))
                .forEach(c -> {
                    courseTeacherRepo.deleteAll(courseTeacherRepo.findByCourseId(c.getId()));
                    courseRepo.deleteById(c.getId());
                });
        // 3. marked διδάσκοντες: M2M links + η γραμμή
        teacherRepo.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().startsWith(MARK))
                .forEach(t -> {
                    courseTeacherRepo.deleteAll(courseTeacherRepo.findByTeacherId(t.getId()));
                    teacherRepo.deleteById(t.getId());
                });
        // 4. marked αίθουσες
        roomRepo.findAll().stream()
                .filter(r -> r.getCode() != null && r.getCode().startsWith(MARK))
                .forEach(r -> roomRepo.deleteById(r.getId()));
        // 5. tracked timeslot
        if (createdTimeSlotId != null) {
            timeSlotRepo.findById(createdTimeSlotId).ifPresent(s -> timeSlotRepo.deleteById(s.getId()));
            createdTimeSlotId = null;
        }
    }
}
