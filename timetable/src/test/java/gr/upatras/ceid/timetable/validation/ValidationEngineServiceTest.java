package gr.upatras.ceid.timetable.validation;

import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.CourseTeacher;
import gr.upatras.ceid.timetable.entity.Room;
import gr.upatras.ceid.timetable.entity.RoomConstraint;
import gr.upatras.ceid.timetable.entity.Teacher;
import gr.upatras.ceid.timetable.entity.TeacherConstraint;
import gr.upatras.ceid.timetable.entity.TimeSlot;
import gr.upatras.ceid.timetable.entity.Timetable;
import gr.upatras.ceid.timetable.entity.TimetableAssignment;
import gr.upatras.ceid.timetable.entity.TimetableAssignment.AssignmentType;
import gr.upatras.ceid.timetable.repository.CourseRepository;
import gr.upatras.ceid.timetable.repository.CourseTeacherRepository;
import gr.upatras.ceid.timetable.repository.RoomConstraintRepository;
import gr.upatras.ceid.timetable.repository.RoomRepository;
import gr.upatras.ceid.timetable.repository.TeacherConstraintRepository;
import gr.upatras.ceid.timetable.repository.TeacherRepository;
import gr.upatras.ceid.timetable.repository.TimeSlotRepository;
import gr.upatras.ceid.timetable.repository.TimetableAssignmentRepository;
import gr.upatras.ceid.timetable.repository.TimetableRepository;
import gr.upatras.ceid.timetable.solver.RoomAvailabilityConstraints;
import gr.upatras.ceid.timetable.solver.SolverWeights;
import gr.upatras.ceid.timetable.solver.TeacherAvailabilityConstraints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Φ-SV2b-ii-β1: DB regression test του {@link ValidationEngineService} — αποδεικνύει
 * end-to-end (πραγματική DB) ότι τα HARD validation issues παράγονται από τη μηχανή
 * (engine → translator), ΣΥΜΠΕΡΙΛΑΜΒΑΝΟΜΕΝΩΝ των 2 NEW (TEACHER_BLOCKED, ROOM_BLOCKED)
 * και του aggregate με πλήρες μήνυμα.
 *
 * Ντετερμινισμός (BL-9 lesson): seed MARK-prefixed δεδομένα, assert ΜΟΝΟ στο δικό μας
 * πρόγραμμα (φίλτρο by code), πλήρες cleanup σε @AfterEach (incl. reset των static
 * registries που γεμίζει το loadConstraintsFromDb).
 */
@SpringBootTest
class ValidationEngineServiceTest {

    private static final String MARK = "TEST_SV2BIIB_";

    @Autowired ValidationEngineService validationEngineService;
    @Autowired TimetableRepository timetableRepo;
    @Autowired TimetableAssignmentRepository assignmentRepo;
    @Autowired CourseRepository courseRepo;
    @Autowired RoomRepository roomRepo;
    @Autowired TimeSlotRepository timeSlotRepo;
    @Autowired TeacherRepository teacherRepo;
    @Autowired CourseTeacherRepository courseTeacherRepo;
    @Autowired TeacherConstraintRepository teacherConstraintRepo;
    @Autowired RoomConstraintRepository roomConstraintRepo;

    // ---------- seeded refs (cleaned in @AfterEach) ----------
    private final List<TimetableAssignment> savedAssignments = new ArrayList<>();
    private final List<CourseTeacher> savedCourseTeachers = new ArrayList<>();
    private final List<TeacherConstraint> savedTeacherConstraints = new ArrayList<>();
    private final List<RoomConstraint> savedRoomConstraints = new ArrayList<>();
    private final List<Course> savedCourses = new ArrayList<>();
    private final List<Room> savedRooms = new ArrayList<>();
    private final List<TimeSlot> savedTimeSlots = new ArrayList<>();
    private final List<Teacher> savedTeachers = new ArrayList<>();
    private Timetable savedTimetable;

    // ====================================================================
    // 1. ROOM_CONFLICT
    // ====================================================================
    @Test
    void roomConflict_producesIssue() {
        Timetable tt = seedTimetable();
        Room r = seedRoom("R1", Room.RoomType.CLASSROOM);
        TimeSlot s = seedSlot(DayOfWeek.MONDAY, 9);
        Course c1 = seedCourse("RC1", Course.CourseType.GENERAL_EDUCATION, 2);
        Course c2 = seedCourse("RC2", Course.CourseType.GENERAL_EDUCATION, 2);
        linkTeacher(c1, seedTeacher("Δοκιμή Πρώτος", "RC_T1"));
        linkTeacher(c2, seedTeacher("Δοκιμή Δεύτερος", "RC_T2"));
        TimetableAssignment a1 = seedAssignment(tt, c1, r, s, AssignmentType.LECTURE);
        TimetableAssignment a2 = seedAssignment(tt, c2, r, s, AssignmentType.LECTURE);

        Map<String, Object> issue = single(byCode(
                validationEngineService.analyzeHardIssues(tt.getId()), "ROOM_CONFLICT"));
        assertEquals("ERROR", issue.get("type"));
        assertEquals(Long.valueOf(Math.min(a1.getId(), a2.getId())), issue.get("referenceId"));
        String msg = (String) issue.get("message");
        assertTrue(msg.contains(r.getCode()), "message has roomCode: " + msg);
        assertTrue(msg.contains(c1.getName()) && msg.contains(c2.getName()),
                "message has both course names: " + msg);
    }

    // ====================================================================
    // 2. LAB_ROOM_REQUIRED
    // ====================================================================
    @Test
    void labInNonLabRoom_producesIssue() {
        Timetable tt = seedTimetable();
        Room r = seedRoom("R2", Room.RoomType.CLASSROOM); // μη-LAB
        TimeSlot s = seedSlot(DayOfWeek.MONDAY, 9);
        Course c = seedCourse("LAB1", Course.CourseType.GENERAL_EDUCATION, 2);
        linkTeacher(c, seedTeacher("Δοκιμή Εργαστηρίου", "LAB_T1"));
        TimetableAssignment a = seedAssignment(tt, c, r, s, AssignmentType.LAB);

        Map<String, Object> issue = single(byCode(
                validationEngineService.analyzeHardIssues(tt.getId()), "LAB_ROOM_REQUIRED"));
        assertEquals("ERROR", issue.get("type"));
        assertEquals(a.getId(), issue.get("referenceId"));
        assertTrue(((String) issue.get("message")).contains(c.getName()));
    }

    // ====================================================================
    // 3. TEACHER_BLOCKED (NEW)
    // ====================================================================
    @Test
    void teacherBlocked_producesIssue() {
        Timetable tt = seedTimetable();
        Room r = seedRoom("R3", Room.RoomType.CLASSROOM);
        TimeSlot s = seedSlot(DayOfWeek.WEDNESDAY, 9);
        Course c = seedCourse("TB1", Course.CourseType.GENERAL_EDUCATION, 2);
        Teacher t = seedTeacher("Δοκιμαστής Άλφα", "TB_T1");
        linkTeacher(c, t);
        blockTeacher(t, DayOfWeek.WEDNESDAY, 9);   // ΠΡΙΝ την κλήση: το loadConstraintsFromDb θα το φορτώσει
        TimetableAssignment a = seedAssignment(tt, c, r, s, AssignmentType.LECTURE);

        Map<String, Object> issue = single(byCode(
                validationEngineService.analyzeHardIssues(tt.getId()), "TEACHER_BLOCKED"));
        assertEquals("ERROR", issue.get("type"));
        assertEquals(a.getId(), issue.get("referenceId"));
        assertTrue(((String) issue.get("message")).contains(c.getName()));
    }

    // ====================================================================
    // 4. ROOM_BLOCKED (NEW)
    // ====================================================================
    @Test
    void roomBlocked_producesIssue() {
        Timetable tt = seedTimetable();
        Room r = seedRoom("R4", Room.RoomType.CLASSROOM);
        TimeSlot s = seedSlot(DayOfWeek.WEDNESDAY, 12);
        Course c = seedCourse("RB1", Course.CourseType.GENERAL_EDUCATION, 4);
        linkTeacher(c, seedTeacher("Δοκιμή Αίθουσας", "RB_T1"));
        blockRoom(r, DayOfWeek.WEDNESDAY, 12);
        TimetableAssignment a = seedAssignment(tt, c, r, s, AssignmentType.LECTURE);

        Map<String, Object> issue = single(byCode(
                validationEngineService.analyzeHardIssues(tt.getId()), "ROOM_BLOCKED"));
        assertEquals("ERROR", issue.get("type"));
        assertEquals(a.getId(), issue.get("referenceId"));
        String msg = (String) issue.get("message");
        assertTrue(msg.contains(r.getCode()) && msg.contains(c.getName()), "message: " + msg);
    }

    // ====================================================================
    // 5. DAILY_LECTURE_LIMIT (aggregate — πλήρες μήνυμα από group-key)
    // ====================================================================
    @Test
    void dailyLectureLimit_producesFullMessage() {
        Timetable tt = seedTimetable();
        Room r = seedRoom("R5", Room.RoomType.CLASSROOM);
        Course c = seedCourse("DLL1", Course.CourseType.REQUIRED, 2);
        linkTeacher(c, seedTeacher("Δοκιμή Διαλέξεων", "DLL_T1"));
        // 7 required LECTURE ίδιου έτους/μέρας, ώρες εκτός μεσημεριού → σπάει το Daily lecture limit.
        for (int h : new int[]{9, 10, 11, 15, 16, 17, 18}) {
            seedAssignment(tt, c, r, seedSlot(DayOfWeek.MONDAY, h), AssignmentType.LECTURE);
        }

        Map<String, Object> issue = single(byCode(
                validationEngineService.analyzeHardIssues(tt.getId()), "DAILY_LECTURE_LIMIT"));
        assertEquals("ERROR", issue.get("type"));
        assertNull(issue.get("referenceId"), "aggregate → referenceId null");
        assertEquals("Το 2ο έτος έχει 7 ώρες θεωρίας την ημέρα Δευτέρα. Το μέγιστο επιτρεπτό είναι 6.",
                issue.get("message"));
    }

    // ====================================================================
    // 6. clean — καμία hard issue
    // ====================================================================
    @Test
    void cleanTimetable_noHardIssues() {
        Timetable tt = seedTimetable();
        Room r = seedRoom("R6", Room.RoomType.CLASSROOM);
        Course c1 = seedCourse("CL1", Course.CourseType.GENERAL_EDUCATION, 2);
        Course c2 = seedCourse("CL2", Course.CourseType.GENERAL_EDUCATION, 2);
        linkTeacher(c1, seedTeacher("Δοκιμή Καθαρός Α", "CL_T1"));
        linkTeacher(c2, seedTeacher("Δοκιμή Καθαρός Β", "CL_T2"));
        seedAssignment(tt, c1, r, seedSlot(DayOfWeek.MONDAY, 9), AssignmentType.LECTURE);
        seedAssignment(tt, c2, r, seedSlot(DayOfWeek.MONDAY, 10), AssignmentType.LECTURE);

        assertTrue(validationEngineService.analyzeHardIssues(tt.getId()).isEmpty(),
                "έγκυρο μικρό πρόγραμμα → καμία hard issue");
    }

    // ====================================================================
    // 7. TEACHER_CONFLICT — εξασκεί το teacherNamesFor (M2M) + τομή στον translator
    // ====================================================================
    @Test
    void teacherConflict_namesSharedTeacher() {
        Timetable tt = seedTimetable();
        Room r1 = seedRoom("R7A", Room.RoomType.CLASSROOM);
        Room r2 = seedRoom("R7B", Room.RoomType.CLASSROOM);
        TimeSlot s = seedSlot(DayOfWeek.TUESDAY, 10);
        Course c1 = seedCourse("TC1", Course.CourseType.GENERAL_EDUCATION, 2);
        Course c2 = seedCourse("TC2", Course.CourseType.GENERAL_EDUCATION, 2);
        Teacher shared = seedTeacher("Κοινός Δοκιμαστής", "TC_SHARED");
        linkTeacher(c1, shared);
        linkTeacher(c2, shared);
        seedAssignment(tt, c1, r1, s, AssignmentType.LECTURE);
        seedAssignment(tt, c2, r2, s, AssignmentType.LECTURE);

        Map<String, Object> issue = single(byCode(
                validationEngineService.analyzeHardIssues(tt.getId()), "TEACHER_CONFLICT"));
        assertEquals("ERROR", issue.get("type"));
        String msg = (String) issue.get("message");
        assertTrue(msg.contains(shared.getName()), "message names the shared teacher: " + msg);
        assertTrue(msg.contains(c1.getName()) && msg.contains(c2.getName()), "message: " + msg);
    }

    // ====================================================================
    // Helpers — seed
    // ====================================================================
    private Timetable seedTimetable() {
        savedTimetable = timetableRepo.save(Timetable.builder()
                .name(MARK + "TT").academicYear("2099-00")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(Timetable.Status.DRAFT)
                .createdAt(LocalDateTime.now())
                .build());
        return savedTimetable;
    }

    private Course seedCourse(String suffix, Course.CourseType type, int year) {
        Course c = courseRepo.save(Course.builder()
                .code(MARK + suffix).name("Μάθημα " + suffix)
                .semester(Math.max(1, year * 2 - 1)).studyYear(year)
                .courseType(type)
                .lectureHours(1).tutorialHours(0).labHours(0)
                .ects(5).expectedStudents(30)
                .semesterType(Course.SemesterType.FALL)
                .active(true).visibleInTimetable(true).deleted(false)
                .build());
        savedCourses.add(c);
        return c;
    }

    private Room seedRoom(String suffix, Room.RoomType type) {
        Room r = roomRepo.save(Room.builder()
                .name("Αίθουσα " + suffix).code("TSV2_" + suffix).capacity(100)
                .roomType(type)
                .hasProjector(false).hasComputers(false)
                .availableForExams(false).availableForSemester(true)
                .active(true)
                .build());
        savedRooms.add(r);
        return r;
    }

    private TimeSlot seedSlot(DayOfWeek day, int hour) {
        TimeSlot s = timeSlotRepo.save(TimeSlot.builder()
                .dayOfWeek(day)
                .startTime(LocalTime.of(hour, 0))
                .endTime(LocalTime.of(hour + 1, 0))
                .slotType(TimeSlot.SlotType.SEMESTER)
                .build());
        savedTimeSlots.add(s);
        return s;
    }

    private Teacher seedTeacher(String name, String shortSuffix) {
        Teacher t = teacherRepo.save(Teacher.builder()
                .name(name).shortName(MARK + shortSuffix).active(true)
                .build());
        savedTeachers.add(t);
        return t;
    }

    private void linkTeacher(Course c, Teacher t) {
        savedCourseTeachers.add(courseTeacherRepo.save(CourseTeacher.builder()
                .course(c).teacher(t).role(CourseTeacher.Role.PRIMARY)
                .build()));
    }

    private TimetableAssignment seedAssignment(Timetable tt, Course c, Room r, TimeSlot s,
                                               AssignmentType type) {
        TimetableAssignment a = assignmentRepo.save(TimetableAssignment.builder()
                .timetable(tt).course(c).room(r).timeSlot(s)
                .assignmentType(type)
                .isLocked(false).manuallyAssigned(true)
                .createdAt(LocalDateTime.now())
                .build());
        savedAssignments.add(a);
        return a;
    }

    private void blockTeacher(Teacher t, DayOfWeek day, int hour) {
        savedTeacherConstraints.add(teacherConstraintRepo.save(TeacherConstraint.builder()
                .teacher(t).dayOfWeek(day.name()).hour(hour)
                .constraintType(TeacherConstraint.ConstraintType.BLOCKED)
                .build()));
    }

    private void blockRoom(Room r, DayOfWeek day, int hour) {
        savedRoomConstraints.add(roomConstraintRepo.save(RoomConstraint.builder()
                .room(r).dayOfWeek(day.name()).hour(hour)
                .constraintType(RoomConstraint.ConstraintType.BLOCKED)
                .build()));
    }

    // ---------- helpers — assertions ----------
    private static List<Map<String, Object>> byCode(List<Map<String, Object>> issues, String code) {
        return issues.stream().filter(i -> code.equals(i.get("code"))).toList();
    }

    private static Map<String, Object> single(List<Map<String, Object>> issues) {
        assertEquals(1, issues.size(), "ακριβώς 1 issue του ζητούμενου code");
        return issues.get(0);
    }

    // ====================================================================
    // Teardown — καθάρισε ΟΛΑ τα seeded rows + reset static registries
    // ====================================================================
    @AfterEach
    void cleanup() {
        if (!savedAssignments.isEmpty()) assignmentRepo.deleteAll(savedAssignments);
        if (savedTimetable != null) timetableRepo.deleteById(savedTimetable.getId());
        if (!savedCourseTeachers.isEmpty()) courseTeacherRepo.deleteAll(savedCourseTeachers);
        if (!savedTeacherConstraints.isEmpty()) teacherConstraintRepo.deleteAll(savedTeacherConstraints);
        if (!savedRoomConstraints.isEmpty()) roomConstraintRepo.deleteAll(savedRoomConstraints);
        if (!savedCourses.isEmpty()) courseRepo.deleteAll(savedCourses);
        if (!savedRooms.isEmpty()) roomRepo.deleteAll(savedRooms);
        if (!savedTimeSlots.isEmpty()) timeSlotRepo.deleteAll(savedTimeSlots);
        if (!savedTeachers.isEmpty()) teacherRepo.deleteAll(savedTeachers);

        savedAssignments.clear();
        savedCourseTeachers.clear();
        savedTeacherConstraints.clear();
        savedRoomConstraints.clear();
        savedCourses.clear();
        savedRooms.clear();
        savedTimeSlots.clear();
        savedTeachers.clear();
        savedTimetable = null;

        // Τα analyzeHardIssues καλούν loadConstraintsFromDb → γεμίζει static registries.
        // Reset ώστε να μη μένουν blocked slots/βάρη για άλλα tests.
        TeacherAvailabilityConstraints.BLOCKED_SLOTS = Map.of();
        TeacherAvailabilityConstraints.PREFERRED_SLOTS = Map.of();
        RoomAvailabilityConstraints.BLOCKED_SLOTS = Map.of();
        SolverWeights.resetToDefaults();
    }
}
