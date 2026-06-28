package gr.upatras.ceid.timetable.controller;

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
import gr.upatras.ceid.timetable.entity.TimetableScopedCourse;
import gr.upatras.ceid.timetable.repository.CourseRepository;
import gr.upatras.ceid.timetable.repository.CourseTeacherRepository;
import gr.upatras.ceid.timetable.repository.RoomConstraintRepository;
import gr.upatras.ceid.timetable.repository.RoomRepository;
import gr.upatras.ceid.timetable.repository.TeacherConstraintRepository;
import gr.upatras.ceid.timetable.repository.TeacherRepository;
import gr.upatras.ceid.timetable.repository.TimeSlotRepository;
import gr.upatras.ceid.timetable.repository.TimetableAssignmentRepository;
import gr.upatras.ceid.timetable.repository.TimetableRepository;
import gr.upatras.ceid.timetable.repository.TimetableScopedCourseRepository;
import gr.upatras.ceid.timetable.solver.RoomAvailabilityConstraints;
import gr.upatras.ceid.timetable.solver.SolverWeights;
import gr.upatras.ceid.timetable.solver.TeacherAvailabilityConstraints;
import org.junit.jupiter.api.AfterEach;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Φ-SV2b-ii-β2 (THE FLIP) — full-report regression. Καλεί το ΠΡΑΓΜΑΤΙΚΟ
 * {@link TimetableController#validateTimetableReport} και αποδεικνύει:
 *  A) τα HARD codes πιάνονται μέσω engine (incl. 2 NEW + aggregate full message),
 *  B) το integrity layer (SEMESTER_MISMATCH/completeness/SHARED_EXAM_ROOM) ΔΕΝ έσπασε,
 *  C) immutability (frozen scope) ανέπαφη,
 *  D) parity: έγκυρο πρόγραμμα → 0 hard errors (hardScore 0 ⇔ 0 hard errors).
 *
 * Ντετερμινισμός (BL-9): seed MARK data, assert ΜΟΝΟ στο δικό μας πρόγραμμα (φίλτρο by
 * code/referenceId), πλήρες teardown + reset static registries σε @AfterEach.
 */
@SpringBootTest
class ValidationReportFlipTest {

    private static final String MARK = "TEST_FLIP_";

    @Autowired TimetableController controller;
    @Autowired TimetableRepository timetableRepo;
    @Autowired TimetableAssignmentRepository assignmentRepo;
    @Autowired CourseRepository courseRepo;
    @Autowired RoomRepository roomRepo;
    @Autowired TimeSlotRepository timeSlotRepo;
    @Autowired TeacherRepository teacherRepo;
    @Autowired CourseTeacherRepository courseTeacherRepo;
    @Autowired TeacherConstraintRepository teacherConstraintRepo;
    @Autowired RoomConstraintRepository roomConstraintRepo;
    @Autowired TimetableScopedCourseRepository scopedCourseRepo;

    private final List<TimetableAssignment> savedAssignments = new ArrayList<>();
    private final List<TimetableScopedCourse> savedScoped = new ArrayList<>();
    private final List<CourseTeacher> savedCourseTeachers = new ArrayList<>();
    private final List<TeacherConstraint> savedTeacherConstraints = new ArrayList<>();
    private final List<RoomConstraint> savedRoomConstraints = new ArrayList<>();
    private final List<Course> savedCourses = new ArrayList<>();
    private final List<Room> savedRooms = new ArrayList<>();
    private final List<TimeSlot> savedTimeSlots = new ArrayList<>();
    private final List<Teacher> savedTeachers = new ArrayList<>();
    private final List<Timetable> savedTimetables = new ArrayList<>();

    // ====================================================================
    // A) Engine HARD codes — πιάνονται μέσω του live report (μετά το flip)
    //    (κανένα scoped row → καμία completaness θόρυβος· φίλτρο by code)
    // ====================================================================

    @Test
    void roomConflict_reportedByEngine() {
        Timetable tt = seedTt(Timetable.TimetableType.SEMESTER, Timetable.SemesterType.FALL);
        Room r = seedRoom("A1", Room.RoomType.CLASSROOM);
        TimeSlot s = seedSlot(DayOfWeek.MONDAY, 9);
        Course c1 = seedCourse("RC1", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.FALL);
        Course c2 = seedCourse("RC2", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.FALL);
        linkTeacher(c1, seedTeacher("Δοκιμή Πρώτος", "A1T1"));
        linkTeacher(c2, seedTeacher("Δοκιμή Δεύτερος", "A1T2"));
        TimetableAssignment a1 = seedAssign(tt, c1, r, s, AssignmentType.LECTURE);
        TimetableAssignment a2 = seedAssign(tt, c2, r, s, AssignmentType.LECTURE);

        List<Map<String, Object>> rc = byCode(errorsOf(report(tt)), "ROOM_CONFLICT");
        assertEquals(1, rc.size(), "ROOM_CONFLICT μέσω engine");
        assertEquals(Math.min(a1.getId(), a2.getId()), refId(rc.get(0)));
        assertTrue(msg(rc.get(0)).contains(r.getCode()));
    }

    @Test
    void labRoomRequired_reportedByEngine() {
        Timetable tt = seedTt(Timetable.TimetableType.SEMESTER, Timetable.SemesterType.FALL);
        Room r = seedRoom("A2", Room.RoomType.CLASSROOM);
        Course c = seedCourse("LAB", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.FALL);
        linkTeacher(c, seedTeacher("Δοκιμή Εργ", "A2T1"));
        TimetableAssignment a = seedAssign(tt, c, r, seedSlot(DayOfWeek.MONDAY, 9), AssignmentType.LAB);

        List<Map<String, Object>> lab = byCode(errorsOf(report(tt)), "LAB_ROOM_REQUIRED");
        assertEquals(1, lab.size());
        assertEquals(a.getId(), refId(lab.get(0)));
    }

    @Test
    void teacherConflict_reportedWithSharedName() {
        Timetable tt = seedTt(Timetable.TimetableType.SEMESTER, Timetable.SemesterType.FALL);
        TimeSlot s = seedSlot(DayOfWeek.TUESDAY, 10);
        Course c1 = seedCourse("TC1", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.FALL);
        Course c2 = seedCourse("TC2", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.FALL);
        Teacher shared = seedTeacher("Κοινός Δοκιμαστής", "TC_SH");
        linkTeacher(c1, shared);
        linkTeacher(c2, shared);
        seedAssign(tt, c1, seedRoom("A3a", Room.RoomType.CLASSROOM), s, AssignmentType.LECTURE);
        seedAssign(tt, c2, seedRoom("A3b", Room.RoomType.CLASSROOM), s, AssignmentType.LECTURE);

        List<Map<String, Object>> tc = byCode(errorsOf(report(tt)), "TEACHER_CONFLICT");
        assertEquals(1, tc.size());
        assertTrue(msg(tc.get(0)).contains(shared.getName()), "όνομα κοινού διδάσκοντα: " + msg(tc.get(0)));
    }

    @Test
    void dailyLectureLimit_reportedWithFullMessage() {
        Timetable tt = seedTt(Timetable.TimetableType.SEMESTER, Timetable.SemesterType.FALL);
        Room r = seedRoom("A4", Room.RoomType.CLASSROOM);
        Course c = seedCourse("DLL", Course.CourseType.REQUIRED, 2, Course.SemesterType.FALL);
        linkTeacher(c, seedTeacher("Δοκιμή Διαλ", "A4T1"));
        for (int h : new int[]{9, 10, 11, 15, 16, 17, 18}) {
            seedAssign(tt, c, r, seedSlot(DayOfWeek.MONDAY, h), AssignmentType.LECTURE);
        }

        List<Map<String, Object>> dll = byCode(errorsOf(report(tt)), "DAILY_LECTURE_LIMIT");
        assertEquals(1, dll.size());
        assertEquals("Το 2ο έτος έχει 7 ώρες θεωρίας την ημέρα Δευτέρα. Το μέγιστο επιτρεπτό είναι 6.",
                msg(dll.get(0)));
    }

    @Test
    void teacherBlocked_reportedByEngine_NEW() {
        Timetable tt = seedTt(Timetable.TimetableType.SEMESTER, Timetable.SemesterType.FALL);
        Room r = seedRoom("A5", Room.RoomType.CLASSROOM);
        TimeSlot s = seedSlot(DayOfWeek.WEDNESDAY, 9);
        Course c = seedCourse("TB", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.FALL);
        Teacher t = seedTeacher("Δοκιμαστής Άλφα", "A5T1");
        linkTeacher(c, t);
        blockTeacher(t, DayOfWeek.WEDNESDAY, 9);
        TimetableAssignment a = seedAssign(tt, c, r, s, AssignmentType.LECTURE);

        List<Map<String, Object>> tb = byCode(errorsOf(report(tt)), "TEACHER_BLOCKED");
        assertEquals(1, tb.size(), "TEACHER_BLOCKED (NEW) μέσω engine");
        assertEquals(a.getId(), refId(tb.get(0)));
    }

    @Test
    void roomBlocked_reportedByEngine_NEW() {
        Timetable tt = seedTt(Timetable.TimetableType.SEMESTER, Timetable.SemesterType.FALL);
        Room r = seedRoom("A6", Room.RoomType.CLASSROOM);
        TimeSlot s = seedSlot(DayOfWeek.WEDNESDAY, 12);
        Course c = seedCourse("RB", Course.CourseType.GENERAL_EDUCATION, 4, Course.SemesterType.FALL);
        linkTeacher(c, seedTeacher("Δοκιμή Αιθ", "A6T1"));
        blockRoom(r, DayOfWeek.WEDNESDAY, 12);
        TimetableAssignment a = seedAssign(tt, c, r, s, AssignmentType.LECTURE);

        List<Map<String, Object>> rb = byCode(errorsOf(report(tt)), "ROOM_BLOCKED");
        assertEquals(1, rb.size(), "ROOM_BLOCKED (NEW) μέσω engine");
        assertEquals(a.getId(), refId(rb.get(0)));
    }

    // ====================================================================
    // B) Integrity layer — ΔΕΝ έσπασε από την αφαίρεση
    // ====================================================================

    @Test
    void semesterMismatch_stillReported() {
        Timetable tt = seedTt(Timetable.TimetableType.SEMESTER, Timetable.SemesterType.FALL);
        Course spring = seedCourse("SM", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.SPRING);
        linkTeacher(spring, seedTeacher("Δοκιμή Εαρ", "BT1"));
        TimetableAssignment a = seedAssign(tt, spring, seedRoom("B1", Room.RoomType.CLASSROOM),
                seedSlot(DayOfWeek.MONDAY, 9), AssignmentType.LECTURE);

        List<Map<String, Object>> sm = byCode(errorsOf(report(tt)), "SEMESTER_MISMATCH");
        assertEquals(1, sm.size(), "SEMESTER_MISMATCH (integrity) ακόμη πυροδοτείται");
        assertEquals(a.getId(), refId(sm.get(0)));
    }

    @Test
    void missingHours_stillReported() {
        Timetable tt = seedTt(Timetable.TimetableType.SEMESTER, Timetable.SemesterType.FALL);
        Course c = seedCourse("MH", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.FALL);
        linkTeacher(c, seedTeacher("Δοκιμή Ελλ", "BT2"));
        seedScope(tt, c, 2, 0, 0, true);   // απαιτεί 2 θεωρίες
        seedAssign(tt, c, seedRoom("B2", Room.RoomType.CLASSROOM), seedSlot(DayOfWeek.MONDAY, 9), AssignmentType.LECTURE); // 1/2

        List<Map<String, Object>> mh = byCode(warningsOf(report(tt)), "MISSING_HOURS");
        assertEquals(1, mh.size());
        assertEquals(c.getId(), refId(mh.get(0)));
    }

    @Test
    void tooManyHours_stillReported() {
        Timetable tt = seedTt(Timetable.TimetableType.SEMESTER, Timetable.SemesterType.FALL);
        Room r = seedRoom("B3", Room.RoomType.CLASSROOM);
        Course c = seedCourse("TM", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.FALL);
        linkTeacher(c, seedTeacher("Δοκιμή Πολλ", "BT3"));
        seedScope(tt, c, 1, 0, 0, true);   // απαιτεί 1 θεωρία
        seedAssign(tt, c, r, seedSlot(DayOfWeek.MONDAY, 9), AssignmentType.LECTURE);
        seedAssign(tt, c, r, seedSlot(DayOfWeek.MONDAY, 10), AssignmentType.LECTURE); // 2/1

        List<Map<String, Object>> tm = byCode(errorsOf(report(tt)), "TOO_MANY_HOURS");
        assertEquals(1, tm.size());
        assertEquals(c.getId(), refId(tm.get(0)));
    }

    @Test
    void unnecessaryHours_stillReported() {
        Timetable tt = seedTt(Timetable.TimetableType.SEMESTER, Timetable.SemesterType.FALL);
        Room r = seedRoom("B4", Room.RoomType.CLASSROOM);
        Course c = seedCourse("UH", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.FALL);
        linkTeacher(c, seedTeacher("Δοκιμή Περ", "BT4"));
        seedScope(tt, c, 1, 0, 0, true);   // 0 φροντιστήρια προβλέπονται
        seedAssign(tt, c, r, seedSlot(DayOfWeek.MONDAY, 9), AssignmentType.LECTURE);   // lecture 1/1 ok
        seedAssign(tt, c, r, seedSlot(DayOfWeek.MONDAY, 10), AssignmentType.TUTORIAL); // tutorial 1/0 -> UNNECESSARY

        List<Map<String, Object>> uh = byCode(errorsOf(report(tt)), "UNNECESSARY_HOURS");
        assertEquals(1, uh.size());
        assertEquals(c.getId(), refId(uh.get(0)));
    }

    @Test
    void missingExam_stillReported() {
        Timetable tt = seedTt(Timetable.TimetableType.EXAM, Timetable.SemesterType.FALL);
        Course c = seedCourse("ME", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.FALL);
        linkTeacher(c, seedTeacher("Δοκιμή Εξετ", "BT5"));
        seedScope(tt, c, 0, 0, 0, true);   // αναμένεται εξέταση, καμία δεν τοποθετήθηκε

        List<Map<String, Object>> me = byCode(warningsOf(report(tt)), "MISSING_EXAM");
        assertEquals(1, me.size());
        assertEquals(c.getId(), refId(me.get(0)));
    }

    @Test
    void sharedExamRoom_stillWarned() {
        Timetable tt = seedTt(Timetable.TimetableType.EXAM, Timetable.SemesterType.FALL);
        Room r = seedRoom("B6", Room.RoomType.CLASSROOM);
        TimeSlot s = seedSlot(DayOfWeek.MONDAY, 9);
        Course c1 = seedCourse("SE1", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.FALL);
        Course c2 = seedCourse("SE2", Course.CourseType.GENERAL_EDUCATION, 3, Course.SemesterType.FALL);
        linkTeacher(c1, seedTeacher("Δοκιμή Εξ Α", "BT6a"));
        linkTeacher(c2, seedTeacher("Δοκιμή Εξ Β", "BT6b"));
        seedAssign(tt, c1, r, s, AssignmentType.EXAM);
        seedAssign(tt, c2, r, s, AssignmentType.EXAM);

        List<Map<String, Object>> se = byCode(warningsOf(report(tt)), "SHARED_EXAM_ROOM");
        assertEquals(1, se.size(), "SHARED_EXAM_ROOM (advisory) ακόμη πυροδοτείται");
    }

    // ====================================================================
    // C) Immutability (frozen scope)
    // ====================================================================

    @Test
    void newCourseAfterFreeze_notFlaggedMissing() {
        Timetable tt = seedTt(Timetable.TimetableType.SEMESTER, Timetable.SemesterType.FALL);
        Room r = seedRoom("C1", Room.RoomType.CLASSROOM);
        Course c1 = seedCourse("FZ1", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.FALL);
        linkTeacher(c1, seedTeacher("Δοκιμή Παγ", "CT1"));
        seedScope(tt, c1, 1, 0, 0, true);   // frozen scope = {c1}
        seedAssign(tt, c1, r, seedSlot(DayOfWeek.MONDAY, 9), AssignmentType.LECTURE);

        // ΝΕΟ relevant μάθημα ΜΕΤΑ το freeze — ΧΩΡΙΣ scope row για το tt.
        Course c2 = seedCourse("FZ2", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.FALL);
        linkTeacher(c2, seedTeacher("Δοκιμή Νέος", "CT2"));

        Map<String, Object> body = report(tt);
        Long c2Id = c2.getId();
        assertFalse(errorsOf(body).stream().anyMatch(i -> c2Id.equals(refId(i))),
                "το νέο μάθημα ΔΕΝ διαρρέει ως error στο παγωμένο πρόγραμμα");
        assertFalse(warningsOf(body).stream().anyMatch(i -> c2Id.equals(refId(i))),
                "το νέο μάθημα ΔΕΝ διαρρέει ως warning (MISSING_HOURS) στο παγωμένο πρόγραμμα");
    }

    @Test
    void softDeletedCourse_stillValidated_andMissingWhenIncomplete() {
        Timetable tt = seedTt(Timetable.TimetableType.SEMESTER, Timetable.SemesterType.FALL);
        Room r = seedRoom("C2", Room.RoomType.CLASSROOM);
        Course c = seedCourse("SD", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.FALL);
        linkTeacher(c, seedTeacher("Δοκιμή Διαγρ", "CT3"));
        seedScope(tt, c, 2, 0, 0, true);   // αναμένει 2 θεωρίες
        TimetableAssignment a1 = seedAssign(tt, c, r, seedSlot(DayOfWeek.MONDAY, 9), AssignmentType.LECTURE);
        seedAssign(tt, c, r, seedSlot(DayOfWeek.MONDAY, 10), AssignmentType.LECTURE);

        Long cId = c.getId();
        // πλήρες (2/2) → καμία MISSING για το c
        assertFalse(byCode(warningsOf(report(tt)), "MISSING_HOURS").stream()
                .anyMatch(i -> cId.equals(refId(i))), "πλήρες: καμία MISSING");

        // soft-delete του μαθήματος — το παλιό πρόγραμμα μένει ανέπαφο (snapshot/scope)
        c.setDeleted(true);
        courseRepo.save(c);
        assertFalse(byCode(warningsOf(report(tt)), "MISSING_HOURS").stream()
                .anyMatch(i -> cId.equals(refId(i))), "soft-deleted αλλά πλήρες: ακόμη καμία MISSING");

        // αφαίρεση μίας ανάθεσης → 1/2 → MISSING (το frozen scope ακόμη το αναμένει)
        assignmentRepo.deleteById(a1.getId());
        savedAssignments.remove(a1);
        List<Map<String, Object>> mh = byCode(warningsOf(report(tt)), "MISSING_HOURS").stream()
                .filter(i -> cId.equals(refId(i))).toList();
        assertEquals(1, mh.size(), "ελλιπές μετά την αφαίρεση: MISSING για το soft-deleted μάθημα");
    }

    // ====================================================================
    // D) Parity — hardScore 0 ⇔ 0 hard errors
    // ====================================================================

    @Test
    void cleanProgram_zeroHardErrors() {
        Timetable tt = seedTt(Timetable.TimetableType.SEMESTER, Timetable.SemesterType.FALL);
        Room r = seedRoom("D1", Room.RoomType.CLASSROOM);
        Course c = seedCourse("CLEAN", Course.CourseType.GENERAL_EDUCATION, 2, Course.SemesterType.FALL);
        linkTeacher(c, seedTeacher("Δοκιμή Καθαρός", "DT1"));
        seedScope(tt, c, 1, 0, 0, false);   // απαιτεί 1 θεωρία
        seedAssign(tt, c, r, seedSlot(DayOfWeek.MONDAY, 9), AssignmentType.LECTURE); // 1/1 — πλήρες

        Map<String, Object> body = report(tt);
        assertEquals(0, ((List<?>) body.get("errors")).size(),
                "έγκυρο πλήρες πρόγραμμα → μηδέν hard errors (engine + integrity)");
        assertEquals(Boolean.TRUE, body.get("valid"));
    }

    // ====================================================================
    // Helpers — report extraction
    // ====================================================================
    @SuppressWarnings("unchecked")
    private Map<String, Object> report(Timetable tt) {
        ResponseEntity<?> resp = controller.validateTimetableReport(tt.getId());
        assertEquals(200, resp.getStatusCode().value(), "validation endpoint -> 200");
        return (Map<String, Object>) resp.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> errorsOf(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("errors");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> warningsOf(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("warnings");
    }

    private static List<Map<String, Object>> byCode(List<Map<String, Object>> list, String code) {
        return list.stream().filter(i -> code.equals(i.get("code"))).toList();
    }

    private static Long refId(Map<String, Object> issue) {
        Object r = issue.get("referenceId");
        return (r instanceof Number n) ? n.longValue() : null;
    }

    private static String msg(Map<String, Object> issue) {
        return String.valueOf(issue.get("message"));
    }

    // ====================================================================
    // Helpers — seed
    // ====================================================================
    private Timetable seedTt(Timetable.TimetableType type, Timetable.SemesterType sem) {
        Timetable t = timetableRepo.save(Timetable.builder()
                .name(MARK + "TT").academicYear("2099-00")
                .timetableType(type).semesterType(sem)
                .status(Timetable.Status.DRAFT).createdAt(LocalDateTime.now())
                .build());
        savedTimetables.add(t);
        return t;
    }

    private Course seedCourse(String suffix, Course.CourseType type, int year, Course.SemesterType sem) {
        Course c = courseRepo.save(Course.builder()
                .code(MARK + suffix).name("Μάθημα " + suffix)
                .semester(Math.max(1, year * 2 - 1)).studyYear(year)
                .courseType(type)
                .lectureHours(1).tutorialHours(0).labHours(0)
                .ects(5).expectedStudents(30)
                .semesterType(sem)
                .active(true).visibleInTimetable(true).deleted(false)
                .build());
        savedCourses.add(c);
        return c;
    }

    private Room seedRoom(String suffix, Room.RoomType type) {
        Room r = roomRepo.save(Room.builder()
                .name("Αίθουσα " + suffix).code("TFLIP_" + suffix).capacity(100)
                .roomType(type)
                .hasProjector(false).hasComputers(false)
                .availableForExams(true).availableForSemester(true).active(true)
                .build());
        savedRooms.add(r);
        return r;
    }

    private TimeSlot seedSlot(DayOfWeek day, int hour) {
        TimeSlot s = timeSlotRepo.save(TimeSlot.builder()
                .dayOfWeek(day).startTime(LocalTime.of(hour, 0)).endTime(LocalTime.of(hour + 1, 0))
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
                .course(c).teacher(t).role(CourseTeacher.Role.PRIMARY).build()));
    }

    private TimetableAssignment seedAssign(Timetable tt, Course c, Room r, TimeSlot s, AssignmentType type) {
        TimetableAssignment a = assignmentRepo.save(TimetableAssignment.builder()
                .timetable(tt).course(c).room(r).timeSlot(s)
                .assignmentType(type).isLocked(false).manuallyAssigned(true)
                .createdAt(LocalDateTime.now())
                .build());
        savedAssignments.add(a);
        return a;
    }

    private void seedScope(Timetable tt, Course c, int reqLec, int reqTut, int reqLab, boolean needsExam) {
        savedScoped.add(scopedCourseRepo.save(TimetableScopedCourse.builder()
                .timetable(tt).courseId(c.getId())
                .snapshotCourseCode(c.getCode()).snapshotCourseName(c.getName())
                .snapshotSemester(c.getSemester()).snapshotStudyYear(c.getStudyYear())
                .snapshotCourseType(c.getCourseType().name())
                .reqLectureHours(reqLec).reqTutorialHours(reqTut).reqLabHours(reqLab)
                .needsExam(needsExam).createdAt(LocalDateTime.now())
                .build()));
    }

    private void blockTeacher(Teacher t, DayOfWeek day, int hour) {
        savedTeacherConstraints.add(teacherConstraintRepo.save(TeacherConstraint.builder()
                .teacher(t).dayOfWeek(day.name()).hour(hour)
                .constraintType(TeacherConstraint.ConstraintType.BLOCKED).build()));
    }

    private void blockRoom(Room r, DayOfWeek day, int hour) {
        savedRoomConstraints.add(roomConstraintRepo.save(RoomConstraint.builder()
                .room(r).dayOfWeek(day.name()).hour(hour)
                .constraintType(RoomConstraint.ConstraintType.BLOCKED).build()));
    }

    // ====================================================================
    // Teardown
    // ====================================================================
    @AfterEach
    void cleanup() {
        if (!savedAssignments.isEmpty()) assignmentRepo.deleteAll(savedAssignments);
        if (!savedScoped.isEmpty()) scopedCourseRepo.deleteAll(savedScoped);
        if (!savedTimetables.isEmpty()) timetableRepo.deleteAll(savedTimetables);
        if (!savedCourseTeachers.isEmpty()) courseTeacherRepo.deleteAll(savedCourseTeachers);
        if (!savedTeacherConstraints.isEmpty()) teacherConstraintRepo.deleteAll(savedTeacherConstraints);
        if (!savedRoomConstraints.isEmpty()) roomConstraintRepo.deleteAll(savedRoomConstraints);
        if (!savedCourses.isEmpty()) courseRepo.deleteAll(savedCourses);
        if (!savedRooms.isEmpty()) roomRepo.deleteAll(savedRooms);
        if (!savedTimeSlots.isEmpty()) timeSlotRepo.deleteAll(savedTimeSlots);
        if (!savedTeachers.isEmpty()) teacherRepo.deleteAll(savedTeachers);

        savedAssignments.clear(); savedScoped.clear(); savedTimetables.clear();
        savedCourseTeachers.clear(); savedTeacherConstraints.clear(); savedRoomConstraints.clear();
        savedCourses.clear(); savedRooms.clear(); savedTimeSlots.clear(); savedTeachers.clear();

        TeacherAvailabilityConstraints.BLOCKED_SLOTS = Map.of();
        TeacherAvailabilityConstraints.PREFERRED_SLOTS = Map.of();
        RoomAvailabilityConstraints.BLOCKED_SLOTS = Map.of();
        SolverWeights.resetToDefaults();
    }
}
