package gr.upatras.ceid.timetable.validation;

import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.CourseTeacher;
import gr.upatras.ceid.timetable.entity.Room;
import gr.upatras.ceid.timetable.entity.Teacher;
import gr.upatras.ceid.timetable.entity.TimeSlot;
import gr.upatras.ceid.timetable.entity.Timetable;
import gr.upatras.ceid.timetable.entity.TimetableAssignment;
import gr.upatras.ceid.timetable.entity.TimetableAssignment.AssignmentType;
import gr.upatras.ceid.timetable.repository.*;
import gr.upatras.ceid.timetable.service.AssignmentSnapshotStamper;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BL-11 (β) — η ΚΥΡΙΑ απόδειξη: τα HARD validation issues ενός παγωμένου προγράμματος
 * μένουν ΑΜΕΤΑΒΛΗΤΑ όταν αλλάξουν τα live course (studyYear) / room (code/type), επειδή ο
 * analysis path (buildPlacedSolution + toView) διαβάζει πλέον snapshot-first. Χωρίς το
 * BL-11, τα live edits θα μετατόπιζαν τα violations (A != B)· με snapshot-first A == B.
 *
 * ΣΗΜ.: το teacher immutability ΔΕΝ δοκιμάζεται εδώ — τα teacher keys/names μένουν σκόπιμα
 * live (BL-8 carve-out: το snapshot_teachers_text είναι corrupted → 6.4% drift)· είναι
 * BL-8 follow-up. Επομένως ΚΑΝΕΝΑ teacher mutation δεν μπαίνει στο A==B assertion.
 *
 * Ντετερμινισμός (BL-9): seed MARK-prefixed, assert ΜΟΝΟ στο δικό μας πρόγραμμα, πλήρες
 * teardown + reset των static registries που γεμίζει το loadConstraintsFromDb.
 */
@SpringBootTest
class SnapshotFirstHardValidationTest {

    private static final String MARK = "TEST_BL11V_";

    @Autowired ValidationEngineService validationEngineService;
    @Autowired AssignmentSnapshotStamper stamper;
    @Autowired TimetableRepository timetableRepo;
    @Autowired TimetableAssignmentRepository assignmentRepo;
    @Autowired CourseRepository courseRepo;
    @Autowired RoomRepository roomRepo;
    @Autowired TimeSlotRepository timeSlotRepo;
    @Autowired TeacherRepository teacherRepo;
    @Autowired CourseTeacherRepository courseTeacherRepo;

    private final List<TimetableAssignment> savedAssignments = new ArrayList<>();
    private final List<CourseTeacher> savedCourseTeachers = new ArrayList<>();
    private final List<Course> savedCourses = new ArrayList<>();
    private final List<Room> savedRooms = new ArrayList<>();
    private final List<TimeSlot> savedTimeSlots = new ArrayList<>();
    private final List<Teacher> savedTeachers = new ArrayList<>();
    private Timetable savedTimetable;

    // ── (β1) studyYear: REQUIRED_YEAR_CONFLICT αμετάβλητο σε live studyYear edit ──
    @Test
    void hardIssues_immutableToStudyYearEdit_BL11() {
        Timetable tt = seedTimetable();
        Room r1 = seedRoom("R1", Room.RoomType.CLASSROOM);
        Room r2 = seedRoom("R2", Room.RoomType.CLASSROOM);
        TimeSlot s = seedSlot(DayOfWeek.MONDAY, 9);
        Course c1 = seedCourse("YA", Course.CourseType.REQUIRED, 2, "Διδ Πρώτος");
        Course c2 = seedCourse("YB", Course.CourseType.REQUIRED, 2, "Διδ Δεύτερος");
        linkTeacher(c1, seedTeacher("Διδ Πρώτος"));
        linkTeacher(c2, seedTeacher("Διδ Δεύτερος"));  // διαφορετικοί → όχι TEACHER_CONFLICT (καθαρό year test)
        seedStamped(tt, c1, r1, s, AssignmentType.LECTURE);
        seedStamped(tt, c2, r2, s, AssignmentType.LECTURE);  // ίδιο slot+year → REQUIRED_YEAR_CONFLICT

        Set<String> a = sig(validationEngineService.analyzeHardIssues(tt.getId()));
        assertTrue(a.stream().anyMatch(x -> x.startsWith("REQUIRED_YEAR_CONFLICT")),
                "precondition: year conflict υπάρχει: " + a);

        // MUTATE LIVE (ΟΧΙ snapshot): studyYear. (teacher immutability = BL-8 follow-up — βλ. class doc)
        c1.setStudyYear(4);
        courseRepo.save(c1);

        Set<String> b = sig(validationEngineService.analyzeHardIssues(tt.getId()));
        assertEquals(a, b, "BL-11: hard issues αμετάβλητα παρά το live studyYear edit (snapshot-frozen year)");
    }

    // ── (β2) room code + type: ROOM_CONFLICT (message code) + LAB_ROOM_REQUIRED ──
    @Test
    void hardIssues_immutableToRoomEdits_BL11() {
        Timetable tt = seedTimetable();
        Room r = seedRoom("RR", Room.RoomType.CLASSROOM);
        TimeSlot s1 = seedSlot(DayOfWeek.TUESDAY, 9);
        TimeSlot s2 = seedSlot(DayOfWeek.TUESDAY, 11);
        Course c1 = seedCourse("RA", Course.CourseType.GENERAL_EDUCATION, 2, "Διδ Άλφα");
        Course c2 = seedCourse("RB", Course.CourseType.GENERAL_EDUCATION, 3, "Διδ Βήτα");
        Course c3 = seedCourse("RC", Course.CourseType.GENERAL_EDUCATION, 2, "Διδ Γάμα");
        linkTeacher(c1, seedTeacher("Διδ Άλφα"));
        linkTeacher(c2, seedTeacher("Διδ Βήτα"));
        linkTeacher(c3, seedTeacher("Διδ Γάμα"));
        seedStamped(tt, c1, r, s1, AssignmentType.LECTURE);
        seedStamped(tt, c2, r, s1, AssignmentType.LECTURE);  // ίδιο room+slot → ROOM_CONFLICT
        seedStamped(tt, c3, r, s2, AssignmentType.LAB);       // LAB σε CLASSROOM → LAB_ROOM_REQUIRED

        Set<String> a = sig(validationEngineService.analyzeHardIssues(tt.getId()));
        assertTrue(a.stream().anyMatch(x -> x.startsWith("ROOM_CONFLICT")),
                "precondition: room conflict υπάρχει: " + a);
        assertTrue(a.stream().anyMatch(x -> x.startsWith("LAB_ROOM_REQUIRED")),
                "precondition: lab-room violation υπάρχει: " + a);
        String oldCode = r.getCode();
        assertTrue(a.stream().anyMatch(x -> x.contains(oldCode)),
                "precondition: μήνυμα περιέχει το παλιό room code: " + a);

        // MUTATE LIVE (ΟΧΙ snapshot): room code + roomType
        r.setCode("ZZ_NEW_BL11");
        r.setRoomType(Room.RoomType.LAB);
        roomRepo.save(r);

        Set<String> b = sig(validationEngineService.analyzeHardIssues(tt.getId()));
        assertEquals(a, b, "BL-11: hard issues αμετάβλητα παρά τα live edits (room code + type)");
        assertFalse(b.stream().anyMatch(x -> x.contains("ZZ_NEW_BL11")),
                "το νέο live room code ΔΕΝ διαρρέει στα μηνύματα (snapshot frozen)");
    }

    // ── signature: συγκρίσιμη μορφή ενός issue set (code|ref|assignmentIds|message) ──
    private static Set<String> sig(List<Map<String, Object>> issues) {
        return issues.stream()
                .map(i -> i.get("code") + "|" + i.get("referenceId") + "|"
                        + i.get("assignmentIds") + "|" + i.get("message"))
                .collect(Collectors.toSet());
    }

    // ── seed helpers ────────────────────────────────────────────────────────────
    private Timetable seedTimetable() {
        savedTimetable = timetableRepo.save(Timetable.builder()
                .name(MARK + "TT").academicYear("2099-00")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(Timetable.Status.DRAFT).createdAt(LocalDateTime.now())
                .build());
        return savedTimetable;
    }

    private Course seedCourse(String suffix, Course.CourseType type, int year, String teachersText) {
        Course c = courseRepo.save(Course.builder()
                .code(MARK + suffix).name("Μάθημα " + suffix)
                .semester(Math.max(1, year * 2 - 1)).studyYear(year)
                .courseType(type)
                .lectureHours(1).tutorialHours(0).labHours(0)
                .ects(5).expectedStudents(30)
                .semesterType(Course.SemesterType.FALL)
                .teachersText(teachersText)
                .active(true).visibleInTimetable(true).deleted(false)
                .build());
        savedCourses.add(c);
        return c;
    }

    private Room seedRoom(String suffix, Room.RoomType type) {
        Room r = roomRepo.save(Room.builder()
                .name("Αίθουσα " + suffix).code(MARK + suffix).capacity(100)
                .roomType(type).hasProjector(false).hasComputers(false)
                .availableForExams(false).availableForSemester(true).active(true)
                .build());
        savedRooms.add(r);
        return r;
    }

    private TimeSlot seedSlot(DayOfWeek day, int hour) {
        TimeSlot s = timeSlotRepo.save(TimeSlot.builder()
                .dayOfWeek(day).startTime(LocalTime.of(hour, 0)).endTime(LocalTime.of(hour + 1, 0))
                .slotType(TimeSlot.SlotType.SEMESTER).build());
        savedTimeSlots.add(s);
        return s;
    }

    private Teacher seedTeacher(String name) {
        Teacher t = teacherRepo.save(Teacher.builder()
                .name(name).shortName(MARK + name.hashCode()).active(true).build());
        savedTeachers.add(t);
        return t;
    }

    private CourseTeacher linkTeacher(Course c, Teacher t) {
        CourseTeacher ct = courseTeacherRepo.save(CourseTeacher.builder()
                .course(c).teacher(t).role(CourseTeacher.Role.PRIMARY).build());
        savedCourseTeachers.add(ct);
        return ct;
    }

    private TimetableAssignment seedStamped(Timetable tt, Course c, Room r, TimeSlot s, AssignmentType type) {
        TimetableAssignment a = TimetableAssignment.builder()
                .timetable(tt).course(c).room(r).timeSlot(s)
                .assignmentType(type)
                .isLocked(false).manuallyAssigned(true).createdAt(LocalDateTime.now())
                .build();
        stamper.stamp(a);                  // freeze: snapshot-on-write (όπως ο live write-path)
        a = assignmentRepo.save(a);
        savedAssignments.add(a);
        return a;
    }

    @AfterEach
    void cleanup() {
        if (!savedAssignments.isEmpty()) assignmentRepo.deleteAll(savedAssignments);
        if (savedTimetable != null) timetableRepo.deleteById(savedTimetable.getId());
        if (!savedCourseTeachers.isEmpty()) courseTeacherRepo.deleteAll(savedCourseTeachers);
        if (!savedCourses.isEmpty()) courseRepo.deleteAll(savedCourses);
        if (!savedRooms.isEmpty()) roomRepo.deleteAll(savedRooms);
        if (!savedTimeSlots.isEmpty()) timeSlotRepo.deleteAll(savedTimeSlots);
        if (!savedTeachers.isEmpty()) teacherRepo.deleteAll(savedTeachers);
        savedAssignments.clear();
        savedCourseTeachers.clear();
        savedCourses.clear();
        savedRooms.clear();
        savedTimeSlots.clear();
        savedTeachers.clear();
        savedTimetable = null;

        TeacherAvailabilityConstraints.BLOCKED_SLOTS = Map.of();
        TeacherAvailabilityConstraints.PREFERRED_SLOTS = Map.of();
        RoomAvailabilityConstraints.BLOCKED_SLOTS = Map.of();
        SolverWeights.resetToDefaults();
    }
}
