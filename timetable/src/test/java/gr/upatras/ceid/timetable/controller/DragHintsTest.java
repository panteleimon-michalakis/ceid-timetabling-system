package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.CourseTeacher;
import gr.upatras.ceid.timetable.entity.Room;
import gr.upatras.ceid.timetable.entity.Teacher;
import gr.upatras.ceid.timetable.entity.TeacherConstraint;
import gr.upatras.ceid.timetable.entity.TimeSlot;
import gr.upatras.ceid.timetable.entity.Timetable;
import gr.upatras.ceid.timetable.entity.TimetableAssignment;
import gr.upatras.ceid.timetable.entity.TimetableAssignment.AssignmentType;
import gr.upatras.ceid.timetable.repository.CourseRepository;
import gr.upatras.ceid.timetable.repository.CourseTeacherRepository;
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
import org.springframework.http.ResponseEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK 2.1 — smoke + σημασιολογικό test για το READ-ONLY drag-hints endpoint
 * ({@link TimetableController#dragHints}) και τη μηχανή του
 * ({@link gr.upatras.ceid.timetable.solver.SolverService#analyzeDragHardViolations}).
 * Καλύπτει: 404 σε ανύπαρκτο assignment, 400 σε cross-timetable assignment, και ότι
 * ένα slot που θα προκαλούσε teacher-conflict σημειώνεται κόκκινο ενώ ελεύθερα slots όχι.
 *
 * Ίδιο μοτίβο απομόνωσης με το {@link ValidationReportFlipTest}: seed MARK data,
 * assert με anyMatch (η dev/CI βάση μπορεί να έχει κι άλλα SEMESTER slots στο value range),
 * πλήρες teardown + reset static registries.
 */
@SpringBootTest
class DragHintsTest {

    private static final String MARK = "TEST_DRAGHINT_";

    @Autowired TimetableController controller;
    @Autowired gr.upatras.ceid.timetable.solver.SolverService solverService;
    @Autowired TimetableRepository timetableRepo;
    @Autowired TimetableAssignmentRepository assignmentRepo;
    @Autowired CourseRepository courseRepo;
    @Autowired RoomRepository roomRepo;
    @Autowired TimeSlotRepository timeSlotRepo;
    @Autowired TeacherRepository teacherRepo;
    @Autowired CourseTeacherRepository courseTeacherRepo;
    @Autowired TeacherConstraintRepository teacherConstraintRepo;

    private final List<TimetableAssignment> savedAssignments = new ArrayList<>();
    private final List<CourseTeacher> savedCourseTeachers = new ArrayList<>();
    private final List<TeacherConstraint> savedTeacherConstraints = new ArrayList<>();
    private final List<Course> savedCourses = new ArrayList<>();
    private final List<Room> savedRooms = new ArrayList<>();
    private final List<TimeSlot> savedTimeSlots = new ArrayList<>();
    private final List<Teacher> savedTeachers = new ArrayList<>();
    private final List<Timetable> savedTimetables = new ArrayList<>();

    @Test
    void nonexistentAssignment_returns404() {
        Timetable tt = seedTt();
        ResponseEntity<?> resp = controller.dragHints(tt.getId(), 999_999_999L);
        assertEquals(404, resp.getStatusCode().value(), "ανύπαρκτο assignment → 404");
    }

    @Test
    void crossTimetableAssignment_returns400() {
        Timetable tt1 = seedTt();
        Timetable tt2 = seedTt();
        Room r = seedRoom("XT");
        Course c = seedCourse("XT", 2);
        linkTeacher(c, seedTeacher("Δοκιμή ΧΤ", "XT1"));
        TimetableAssignment a = seedAssign(tt2, c, r, seedSlot(DayOfWeek.MONDAY, 9));

        // assignment ανήκει στο tt2, ζητείται μέσω tt1 → 400
        ResponseEntity<?> resp = controller.dragHints(tt1.getId(), a.getId());
        assertEquals(400, resp.getStatusCode().value(), "cross-timetable assignment → 400");
    }

    @Test
    @SuppressWarnings("unchecked")
    void teacherConflictSlot_flaggedRed_freeSlotsClean() {
        Timetable tt = seedTt();
        Room r1 = seedRoom("TCa");
        Room r2 = seedRoom("TCb");
        Course cA = seedCourse("TCA", 2);
        Course cB = seedCourse("TCB", 2);
        Teacher shared = seedTeacher("Κοινός Σύρτης", "SH");
        linkTeacher(cA, shared);
        linkTeacher(cB, shared);

        // B σταθερό στο (MONDAY, 9). A τοποθετημένο αλλού· θα το «σύρουμε».
        seedAssign(tt, cB, r1, seedSlot(DayOfWeek.MONDAY, 9));
        TimetableAssignment a = seedAssign(tt, cA, r2, seedSlot(DayOfWeek.FRIDAY, 20));

        ResponseEntity<?> resp = controller.dragHints(tt.getId(), a.getId());
        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        List<Map<String, Object>> hints = (List<Map<String, Object>>) body.get("hints");
        assertFalse(hints.isEmpty(), "μη-κενό grid → μη-κενά hints");

        // Το slot όπου κάθεται ο B (κοινός διδάσκων) → teacher conflict σε ΚΑΘΕ αίθουσα → κόκκινο.
        assertTrue(hints.stream().anyMatch(h ->
                        "MONDAY".equals(h.get("day"))
                        && ((Number) h.get("hour")).intValue() == 9
                        && Boolean.TRUE.equals(h.get("hardViolation"))),
                "το slot του κοινού-διδάσκοντα conflict σημειώνεται κόκκινο");

        // Υπάρχουν και ελεύθερα (μη-κόκκινα) slots — δεν κοκκινίζουν όλα.
        assertTrue(hints.stream().anyMatch(h -> Boolean.FALSE.equals(h.get("hardViolation"))),
                "ελεύθερα slots παραμένουν μη-κόκκινα");
    }

    @Test
    @SuppressWarnings("unchecked")
    void exam_requiredSameYearSameDay_flaggedRed_otherDatesClean() {
        // Exam true-positive: δύο ΥΠΟΧΡΕΩΤΙΚΕΣ εξετάσεις ίδιου έτους την ίδια ημερομηνία =
        // HARD requiredSameYearSameDay. Σύρουμε την Α· κάθε slot της ημερομηνίας όπου κάθεται
        // η Β (ίδιο έτος, υποχρεωτικό) κοκκινίζει (date-based → όλες οι αίθουσες)· άλλες
        // ημερομηνίες μένουν καθαρές.
        LocalDate d1 = LocalDate.of(2026, 9, 1);   // Τρίτη — placement του A
        LocalDate d2 = LocalDate.of(2026, 9, 2);   // Τετάρτη — εκεί κάθεται ο B (conflict date)
        LocalDate d3 = LocalDate.of(2026, 9, 3);   // Πέμπτη — καθαρή ημερομηνία
        Timetable tt = seedExamTt(d1, LocalDate.of(2026, 9, 10));
        Room r = seedRoom("EX");
        Course cA = seedCourseTyped("EXA", 2, Course.CourseType.REQUIRED);
        Course cB = seedCourseTyped("EXB", 2, Course.CourseType.REQUIRED);
        linkTeacher(cA, seedTeacher("Δοκιμή ΕξΑ", "EXA1"));
        linkTeacher(cB, seedTeacher("Δοκιμή ΕξΒ", "EXB1"));

        // slots (3ωρα exam windows) σε 3 ημερομηνίες
        TimeSlot sA = seedExamSlot(d1, 9);
        TimeSlot sB = seedExamSlot(d2, 9);
        seedExamSlot(d2, 12);   // δεύτερο window ίδιας ημερομηνίας conflict
        seedExamSlot(d3, 9);
        seedExamSlot(d3, 12);

        seedAssignTyped(tt, cB, r, sB, AssignmentType.EXAM);              // B σταθερό στο d2
        TimetableAssignment a = seedAssignTyped(tt, cA, r, sA, AssignmentType.EXAM); // A → drag

        ResponseEntity<?> resp = controller.dragHints(tt.getId(), a.getId());
        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        List<Map<String, Object>> hints = (List<Map<String, Object>>) body.get("hints");
        assertFalse(hints.isEmpty(), "exam grid → μη-κενά hints");

        // ΚΑΘΕ slot της ημερομηνίας d2 (όπου κάθεται ο B) → κόκκινο (required same-year same-day).
        List<Map<String, Object>> d2Hints = hints.stream()
                .filter(h -> d2.toString().equals(h.get("day"))).toList();
        assertFalse(d2Hints.isEmpty(), "υπάρχουν slots για την conflict-ημερομηνία " + d2);
        assertTrue(d2Hints.stream().allMatch(h -> Boolean.TRUE.equals(h.get("hardViolation"))),
                "όλα τα slots της conflict-ημερομηνίας κόκκινα: " + d2Hints);

        // Η καθαρή ημερομηνία d3 → κανένα κόκκινο.
        List<Map<String, Object>> d3Hints = hints.stream()
                .filter(h -> d3.toString().equals(h.get("day"))).toList();
        assertFalse(d3Hints.isEmpty(), "υπάρχουν slots για την καθαρή ημερομηνία " + d3);
        assertTrue(d3Hints.stream().noneMatch(h -> Boolean.TRUE.equals(h.get("hardViolation"))),
                "καθαρή ημερομηνία → κανένα κόκκινο: " + d3Hints);
    }

    @Test
    void preferredSlotsByCourse_authoritativeM2M() {
        // TASK 2.3: το πράσινο αντλείται από CourseTeacher M2M ∩ PREFERRED_SLOTS, ΟΧΙ teachersText.
        Course c = seedCourse("PREF", 2);
        Teacher t = seedTeacher("Δοκιμή Προτ", "PREF1");
        linkTeacher(c, t);                         // authoritative M2M link
        preferTeacher(t, DayOfWeek.WEDNESDAY, 10); // DB PREFERRED constraint

        Map<Long, List<String>> map = solverService.preferredSlotsByCourse();
        List<String> slots = map.get(c.getId());
        assertNotNull(slots, "το μάθημα με preferred-slot διδάσκοντα (M2M) εμφανίζεται");
        assertTrue(slots.contains("WEDNESDAY_10"), "authoritative preferred slot: " + slots);
    }

    // ---------- helpers ----------

    private void preferTeacher(Teacher t, DayOfWeek day, int hour) {
        savedTeacherConstraints.add(teacherConstraintRepo.save(TeacherConstraint.builder()
                .teacher(t).dayOfWeek(day.name()).hour(hour)
                .constraintType(TeacherConstraint.ConstraintType.PREFERRED).build()));
    }

    private Timetable seedExamTt(LocalDate start, LocalDate end) {
        Timetable t = timetableRepo.save(Timetable.builder()
                .name(MARK + "EXTT").academicYear("2099-00")
                .timetableType(Timetable.TimetableType.EXAM)
                .semesterType(Timetable.SemesterType.SEPTEMBER)
                .startDate(start).endDate(end)
                .status(Timetable.Status.DRAFT).createdAt(LocalDateTime.now())
                .build());
        savedTimetables.add(t);
        return t;
    }

    private TimeSlot seedExamSlot(LocalDate date, int hour) {
        TimeSlot s = timeSlotRepo.save(TimeSlot.builder()
                .dayOfWeek(date.getDayOfWeek())
                .specificDate(date)
                .startTime(LocalTime.of(hour, 0)).endTime(LocalTime.of(hour + 3, 0))
                .slotType(TimeSlot.SlotType.EXAM)
                .build());
        savedTimeSlots.add(s);
        return s;
    }

    private Course seedCourseTyped(String suffix, int year, Course.CourseType type) {
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

    private TimetableAssignment seedAssignTyped(Timetable tt, Course c, Room r, TimeSlot s, AssignmentType type) {
        TimetableAssignment a = assignmentRepo.save(TimetableAssignment.builder()
                .timetable(tt).course(c).room(r).timeSlot(s)
                .assignmentType(type).isLocked(false).manuallyAssigned(true)
                .createdAt(LocalDateTime.now())
                .build());
        savedAssignments.add(a);
        return a;
    }

    private Timetable seedTt() {
        Timetable t = timetableRepo.save(Timetable.builder()
                .name(MARK + "TT").academicYear("2099-00")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(Timetable.Status.DRAFT).createdAt(LocalDateTime.now())
                .build());
        savedTimetables.add(t);
        return t;
    }

    private Course seedCourse(String suffix, int year) {
        Course c = courseRepo.save(Course.builder()
                .code(MARK + suffix).name("Μάθημα " + suffix)
                .semester(Math.max(1, year * 2 - 1)).studyYear(year)
                .courseType(Course.CourseType.GENERAL_EDUCATION)
                .lectureHours(1).tutorialHours(0).labHours(0)
                .ects(5).expectedStudents(30)
                .semesterType(Course.SemesterType.FALL)
                .active(true).visibleInTimetable(true).deleted(false)
                .build());
        savedCourses.add(c);
        return c;
    }

    private Room seedRoom(String suffix) {
        Room r = roomRepo.save(Room.builder()
                .name("Αίθουσα " + suffix).code("TDH_" + suffix).capacity(100)
                .roomType(Room.RoomType.CLASSROOM)
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

    private TimetableAssignment seedAssign(Timetable tt, Course c, Room r, TimeSlot s) {
        TimetableAssignment a = assignmentRepo.save(TimetableAssignment.builder()
                .timetable(tt).course(c).room(r).timeSlot(s)
                .assignmentType(AssignmentType.LECTURE).isLocked(false).manuallyAssigned(true)
                .createdAt(LocalDateTime.now())
                .build());
        savedAssignments.add(a);
        return a;
    }

    @AfterEach
    void cleanup() {
        if (!savedAssignments.isEmpty()) assignmentRepo.deleteAll(savedAssignments);
        if (!savedTimetables.isEmpty()) timetableRepo.deleteAll(savedTimetables);
        if (!savedCourseTeachers.isEmpty()) courseTeacherRepo.deleteAll(savedCourseTeachers);
        if (!savedTeacherConstraints.isEmpty()) teacherConstraintRepo.deleteAll(savedTeacherConstraints);
        if (!savedCourses.isEmpty()) courseRepo.deleteAll(savedCourses);
        if (!savedRooms.isEmpty()) roomRepo.deleteAll(savedRooms);
        if (!savedTimeSlots.isEmpty()) timeSlotRepo.deleteAll(savedTimeSlots);
        if (!savedTeachers.isEmpty()) teacherRepo.deleteAll(savedTeachers);

        savedAssignments.clear(); savedTimetables.clear(); savedCourseTeachers.clear();
        savedTeacherConstraints.clear(); savedCourses.clear(); savedRooms.clear();
        savedTimeSlots.clear(); savedTeachers.clear();

        TeacherAvailabilityConstraints.BLOCKED_SLOTS = Map.of();
        TeacherAvailabilityConstraints.PREFERRED_SLOTS = Map.of();
        RoomAvailabilityConstraints.BLOCKED_SLOTS = Map.of();
        SolverWeights.resetToDefaults();
    }
}
