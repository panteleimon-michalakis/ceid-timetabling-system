package gr.upatras.ceid.timetable.solver;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * BL-11 (α) — format-consistency των snapshot-first helpers του analysis path. Για ένα
 * unedited assignment (snapshot stamped == live) οι helpers παράγουν ΤΑ ΙΔΙΑ constraint
 * inputs με τα live entities· για unstamped (snapshot null) κάνουν fallback σε live. Έτσι
 * κλειδώνεται ότι το snapshot format round-trip-άρει στα ίδια keys (αλλιώς silent drift
 * θα άλλαζε τα hard-validation αποτελέσματα ΟΛΩΝ των προγραμμάτων).
 */
@SpringBootTest
class SnapshotFirstHelpersTest {

    private static final String MARK = "TEST_BL11H_";

    @Autowired SolverService solverService;
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

    // ── (α1) stamped unedited assignment → helpers == live (round-trip) ──────────
    @Test
    void stampedAssignment_helpersRoundTripToLive() {
        Timetable tt = seedTimetable();
        Teacher t = seedTeacher("Παύλος Δοκιμαστής");
        Course c = seedCourse("RT1", 2, "Παύλος Δοκιμαστής"); // teachersText συγχρονισμένο με M2M
        linkTeacher(c, t);
        Room r = seedRoom("RTH1", Room.RoomType.LAB);
        TimeSlot s = seedSlot(DayOfWeek.TUESDAY, 11);
        TimetableAssignment a = seedStampedAssignment(tt, c, r, s);

        Map<Long, Set<String>> liveMap = solverService.buildTeacherKeyMap();

        // teacher keys: ο snapshotTeacherKeys helper round-trip-άρει σε CLEAN ονόματα στα ίδια
        // keys με το live M2M. ΣΗΜ.: σε ΠΡΑΓΜΑΤΙΚΑ δεδομένα η consistency είναι gated στο BL-8
        // (μη-idempotent cleanTeacherDisplayName μολύνει το snapshot_teachers_text με «ςςς» →
        // 6.4% drift), γι' αυτό ο helper μένει hook (ΟΧΙ wired στο buildPlacedSolution). Αυτό το
        // test είναι ο μόνιμος guard που θα ανάψει πράσινο όταν καθαριστούν τα frozen δεδομένα.
        assertEquals(liveMap.getOrDefault(c.getId(), Set.of()),
                solverService.snapshotTeacherKeys(a, liveMap),
                "snapshot teacher keys == live M2M keys (clean unedited name)");
        assertFalse(solverService.snapshotTeacherKeys(a, liveMap).isEmpty(),
                "precondition: μη-κενά keys (όχι τυχαία ίσα ως κενά)");

        // timeslot
        SolverTimeSlot sts = solverService.buildSnapshotTimeSlot(a);
        assertEquals(s.getDayOfWeek().name(), sts.getDayOfWeek());
        assertEquals(s.getStartTime().getHour(), sts.getStartHour());

        // room
        SolverRoom sr = solverService.buildSnapshotRoom(a);
        assertEquals(r.getCode(), sr.getCode());
        assertEquals(r.getRoomType().name(), sr.getRoomType());
        assertEquals(r.getCapacity(), sr.getCapacity());
    }

    // ── (α2) unstamped assignment (snapshot null) → fallback σε live ─────────────
    @Test
    void unstampedAssignment_fallsBackToLive() {
        Timetable tt = seedTimetable();
        Teacher t = seedTeacher("Μαρία Δοκιμαστού");
        Course c = seedCourse("FB1", 3, "Μαρία Δοκιμαστού");
        linkTeacher(c, t);
        Room r = seedRoom("FBH1", Room.RoomType.CLASSROOM);
        TimeSlot s = seedSlot(DayOfWeek.THURSDAY, 14);
        TimetableAssignment a = seedAssignmentNoStamp(tt, c, r, s); // snapshot πεδία = null

        Map<Long, Set<String>> liveMap = solverService.buildTeacherKeyMap();

        assertNull(a.getSnapshotRoomCode(), "precondition: unstamped (snapshot null)");
        assertEquals(liveMap.getOrDefault(c.getId(), Set.of()),
                solverService.snapshotTeacherKeys(a, liveMap), "fallback: live keys");
        SolverRoom sr = solverService.buildSnapshotRoom(a);
        assertEquals(r.getCode(), sr.getCode(), "fallback: live room code");
        assertEquals(r.getRoomType().name(), sr.getRoomType());
        SolverTimeSlot sts = solverService.buildSnapshotTimeSlot(a);
        assertEquals(s.getDayOfWeek().name(), sts.getDayOfWeek(), "fallback: live day");
        assertEquals(s.getStartTime().getHour(), sts.getStartHour());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────
    private Timetable seedTimetable() {
        savedTimetable = timetableRepo.save(Timetable.builder()
                .name(MARK + "TT").academicYear("2099-00")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(Timetable.Status.DRAFT).createdAt(LocalDateTime.now())
                .build());
        return savedTimetable;
    }

    private Course seedCourse(String suffix, int year, String teachersText) {
        Course c = courseRepo.save(Course.builder()
                .code(MARK + suffix).name("Μάθημα " + suffix)
                .semester(Math.max(1, year * 2 - 1)).studyYear(year)
                .courseType(Course.CourseType.GENERAL_EDUCATION)
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
                .name("Αίθουσα " + suffix).code(MARK + suffix).capacity(80)
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
                .name(name).shortName(MARK + name.substring(0, 3)).active(true).build());
        savedTeachers.add(t);
        return t;
    }

    private void linkTeacher(Course c, Teacher t) {
        savedCourseTeachers.add(courseTeacherRepo.save(CourseTeacher.builder()
                .course(c).teacher(t).role(CourseTeacher.Role.PRIMARY).build()));
    }

    private TimetableAssignment seedStampedAssignment(Timetable tt, Course c, Room r, TimeSlot s) {
        TimetableAssignment a = TimetableAssignment.builder()
                .timetable(tt).course(c).room(r).timeSlot(s)
                .assignmentType(AssignmentType.LECTURE)
                .isLocked(false).manuallyAssigned(true).createdAt(LocalDateTime.now())
                .build();
        stamper.stamp(a);                  // BL-11: snapshot-on-write (όπως ο live write-path)
        a = assignmentRepo.save(a);
        savedAssignments.add(a);
        return a;
    }

    private TimetableAssignment seedAssignmentNoStamp(Timetable tt, Course c, Room r, TimeSlot s) {
        TimetableAssignment a = assignmentRepo.save(TimetableAssignment.builder()
                .timetable(tt).course(c).room(r).timeSlot(s)
                .assignmentType(AssignmentType.LECTURE)
                .isLocked(false).manuallyAssigned(true).createdAt(LocalDateTime.now())
                .build());
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
    }
}
