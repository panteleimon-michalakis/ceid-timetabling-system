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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 3 (placement-option hint ↔ drop parity): το {@code allowed} κάθε option
 * βασίζεται στο ΙΔΙΟ hard gate με το drop ({@code validateStructural}). Advisory
 * scheduling issues → allowed=true + warning!=null (status WARNING), ΟΧΙ block.
 * Δομικό fail → allowed=false (status BLOCKED). Καθαρό → allowed + warning==null.
 *
 * Integration (@SpringBootTest): καλεί απευθείας {@code getPlacementOptions}.
 * Marker seed/cleanup, ΧΩΡΙΣ @Transactional (τα seeds πρέπει να είναι visible στο
 * read μέσα από το ίδιο tx του handler).
 */
@SpringBootTest
class PlacementHintDropParityTest {

    private static final String MARK = "TEST_PHDP_";
    private static final String ROOM_CODE = MARK + "R1";

    @Autowired TimetableController timetableController;
    @Autowired TimetableRepository timetableRepo;
    @Autowired TimetableAssignmentRepository assignmentRepo;
    @Autowired CourseRepository courseRepo;
    @Autowired RoomRepository roomRepo;
    @Autowired TimeSlotRepository timeSlotRepo;

    private Long roomId, semSlotId, semSlot2Id, examSlotId;
    private Long courseCleanId;
    private Long ttId;

    @BeforeEach void before() { cleanup(); seed(); }
    @AfterEach  void after()  { cleanup(); }

    // structural OK + advisory (room double-book) → allowed=true + warning
    @Test
    void advisorySlot_isAllowedWithWarning() {
        Map<String, Object> option = findOption("LECTURE", roomId, semSlotId);
        assertNotNull(option, "υπάρχει option για (room, occupied semSlot)");
        assertEquals(Boolean.TRUE, option.get("allowed"), "δομικά επιτρεπτό → allowed");
        assertNotNull(option.get("warning"), "advisory σύγκρουση → warning!=null");
        assertEquals("WARNING", option.get("status"));
    }

    // structural OK + καθαρό → allowed=true + warning==null
    @Test
    void cleanSlot_isAllowedNoWarning() {
        Map<String, Object> option = findOption("LECTURE", roomId, semSlot2Id);
        assertNotNull(option, "υπάρχει option για (room, κενό semSlot)");
        assertEquals(Boolean.TRUE, option.get("allowed"));
        assertNull(option.get("warning"), "καθαρό slot → warning==null");
        assertEquals("ALLOWED", option.get("status"));
    }

    // structural fail (EXAM type σε SEMESTER πρόγραμμα) → allowed=false, BLOCKED
    @Test
    void structuralFail_isBlocked() {
        Map<String, Object> option = findOption("EXAM", roomId, examSlotId);
        assertNotNull(option, "υπάρχει option για (exam room, exam slot)");
        assertEquals(Boolean.FALSE, option.get("allowed"), "δομικό fail → !allowed");
        assertEquals("BLOCKED", option.get("status"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findOption(String type, Long wantRoomId, Long wantSlotId) {
        ResponseEntity<?> resp = timetableController.getPlacementOptions(ttId, courseCleanId, type);
        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertNotNull(body);
        List<Map<String, Object>> options = (List<Map<String, Object>>) body.get("options");
        for (Map<String, Object> o : options) {
            Map<String, Object> room = (Map<String, Object>) o.get("room");
            Map<String, Object> slot = (Map<String, Object>) o.get("timeSlot");
            if (room != null && slot != null
                    && wantRoomId.equals(toLong(room.get("id")))
                    && wantSlotId.equals(toLong(slot.get("id")))) {
                return o;
            }
        }
        return null;
    }

    private Long toLong(Object o) { return o == null ? null : ((Number) o).longValue(); }

    private void seed() {
        Room room = roomRepo.save(Room.builder()
                .name("PHDP Room").code(ROOM_CODE).capacity(200)
                .roomType(Room.RoomType.AMPHITHEATER)
                .hasProjector(false).hasComputers(false)
                .availableForExams(true).availableForSemester(true)
                .active(true).build());
        roomId = room.getId();

        TimeSlot semSlot = timeSlotRepo.save(TimeSlot.builder()
                .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
                .slotType(TimeSlot.SlotType.SEMESTER).build());
        semSlotId = semSlot.getId();

        TimeSlot semSlot2 = timeSlotRepo.save(TimeSlot.builder()
                .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0))
                .slotType(TimeSlot.SlotType.SEMESTER).build());
        semSlot2Id = semSlot2.getId();

        TimeSlot examSlot = timeSlotRepo.save(TimeSlot.builder()
                .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0))
                .slotType(TimeSlot.SlotType.EXAM).specificDate(LocalDate.of(2026, 1, 15)).build());
        examSlotId = examSlot.getId();

        Course courseClean = courseRepo.save(Course.builder()
                .code(MARK + "C_CLEAN").name("PHDP Clean")
                .semester(3).studyYear(2)
                .courseType(Course.CourseType.GENERAL_EDUCATION)
                .lectureHours(2).tutorialHours(0).labHours(0)
                .expectedStudents(10).teachersText("Καρβέλης")
                .semesterType(Course.SemesterType.FALL)
                .active(true).visibleInTimetable(true).build());
        courseCleanId = courseClean.getId();

        Course courseOther = courseRepo.save(Course.builder()
                .code(MARK + "C_OTHER").name("PHDP Other")
                .semester(5).studyYear(3)
                .courseType(Course.CourseType.GENERAL_EDUCATION)
                .lectureHours(2).tutorialHours(0).labHours(0)
                .expectedStudents(10).teachersText("Παπαδόπουλος")
                .semesterType(Course.SemesterType.FALL)
                .active(true).visibleInTimetable(true).build());

        Timetable tt = timetableRepo.save(Timetable.builder()
                .name(MARK + "TT").academicYear("2025-26")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(Timetable.Status.DRAFT).build());
        ttId = tt.getId();

        // occupy (room, semSlot) με courseOther → room double-book advisory για courseClean
        assignmentRepo.save(TimetableAssignment.builder()
                .timetable(tt).course(courseOther).room(room).timeSlot(semSlot)
                .assignmentType(TimetableAssignment.AssignmentType.LECTURE)
                .isLocked(false).manuallyAssigned(true)
                .createdAt(LocalDateTime.now()).build());
    }

    private void cleanup() {
        timetableRepo.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().startsWith(MARK))
                .forEach(t -> {
                    assignmentRepo.deleteAll(assignmentRepo.findByTimetableId(t.getId()));
                    timetableRepo.deleteById(t.getId());
                });
        courseRepo.findAll().stream()
                .filter(c -> c.getCode() != null && c.getCode().startsWith(MARK))
                .forEach(c -> courseRepo.deleteById(c.getId()));
        roomRepo.findByCode(ROOM_CODE).ifPresent(r -> roomRepo.deleteById(r.getId()));
        for (Long sid : new Long[]{semSlotId, semSlot2Id, examSlotId}) {
            if (sid != null) timeSlotRepo.findById(sid).ifPresent(s -> timeSlotRepo.deleteById(s.getId()));
        }
    }
}
