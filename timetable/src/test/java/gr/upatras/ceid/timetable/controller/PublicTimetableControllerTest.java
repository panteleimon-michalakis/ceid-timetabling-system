package gr.upatras.ceid.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.upatras.ceid.timetable.entity.*;
import gr.upatras.ceid.timetable.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Φάση 1 του Public StudentView: account-less, read-only πρόσβαση ΜΟΝΟ σε
 * PUBLISHED προγράμματα μέσω {@code /api/public/**} (permitAll μόνο για GET).
 *
 * Τα requests γίνονται ΧΩΡΙΣ authentication (κανένα Authorization header) ώστε να
 * επιβεβαιωθεί ότι ο νέος permitAll matcher του {@code SecurityConfig} λειτουργεί
 * και ότι το PUBLISHED gating φράζει τα DRAFT (404, χωρίς διαρροή ύπαρξης).
 *
 * Σκόπιμα ΧΩΡΙΣ @Transactional: τα seeds γίνονται commit ώστε να τα δει το request
 * μέσα από το πλήρες filter chain· καθαρισμός με markers στο tearDown.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicTimetableControllerTest {

    private static final String MARK = "TEST_PUBVIEW_";
    private static final String COURSE_CODE = MARK + "C1";
    private static final String ROOM_CODE = MARK + "R1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TimetableRepository timetableRepo;
    @Autowired TimetableAssignmentRepository assignmentRepo;
    @Autowired CourseRepository courseRepo;
    @Autowired RoomRepository roomRepo;
    @Autowired TimeSlotRepository timeSlotRepo;

    private Long publishedId;
    private Long draftId;
    private Long courseId;
    private Long roomId;
    private Long slotId;

    @BeforeEach
    void setUp() { cleanup(); seed(); }

    @AfterEach
    void tearDown() { cleanup(); }

    // ── (1) λίστα: no token → 200, περιέχει το published, ΟΧΙ το draft ──────────────
    @Test
    @SuppressWarnings("unchecked")
    void listPublished_noAuth_returnsPublishedNotDraft() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/public/timetables"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> body =
                objectMapper.readValue(res.getResponse().getContentAsString(), List.class);

        List<Object> ids = body.stream().map(m -> m.get("id")).toList();
        assertTrue(ids.contains(publishedId.intValue()),
                "η δημόσια λίστα περιέχει το PUBLISHED πρόγραμμα");
        assertFalse(ids.contains(draftId.intValue()),
                "η δημόσια λίστα ΔΕΝ διαρρέει το DRAFT πρόγραμμα");
    }

    // ── (2) draft assignments: no token → 404 (δεν διαρρέει ύπαρξη/περιεχόμενο) ──────
    @Test
    void draftAssignments_noAuth_returns404() throws Exception {
        mockMvc.perform(get("/api/public/timetables/{id}/assignments", draftId))
                .andExpect(status().isNotFound());
    }

    // ── (3) published assignments: no token → 200, μη-κενή λίστα ─────────────────────
    @Test
    @SuppressWarnings("unchecked")
    void publishedAssignments_noAuth_returns200NonEmpty() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/public/timetables/{id}/assignments", publishedId))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> body =
                objectMapper.readValue(res.getResponse().getContentAsString(), List.class);
        assertFalse(body.isEmpty(), "το δημοσιευμένο πρόγραμμα επιστρέφει ≥1 ανάθεση");
    }

    // ── helpers ────────────────────────────────────────────────────────────────────
    private void seed() {
        Course course = courseRepo.save(Course.builder()
                .code(COURSE_CODE).name("Public View Test Course")
                .semester(3).studyYear(2)
                .courseType(Course.CourseType.GENERAL_EDUCATION)
                .lectureHours(2).tutorialHours(0).labHours(0)
                .expectedStudents(10)
                .teachersText("Καρβέλης")
                .semesterType(Course.SemesterType.FALL)
                .active(true).visibleInTimetable(true)
                .build());
        courseId = course.getId();

        Room room = roomRepo.save(Room.builder()
                .name("Public View Test Room").code(ROOM_CODE).capacity(200)
                .roomType(Room.RoomType.AMPHITHEATER)
                .hasProjector(false).hasComputers(false)
                .availableForExams(false).availableForSemester(true)
                .active(true).build());
        roomId = room.getId();

        TimeSlot slot = timeSlotRepo.save(TimeSlot.builder()
                .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
                .slotType(TimeSlot.SlotType.SEMESTER).build());
        slotId = slot.getId();

        Timetable published = timetableRepo.save(Timetable.builder()
                .name(MARK + "PUBLISHED").academicYear("2025-26")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(Timetable.Status.PUBLISHED)
                .publishedAt(LocalDateTime.now())
                .build());
        publishedId = published.getId();

        // ≥1 ανάθεση στο published (απευθείας save — δεν περνά από room-κανόνες)
        assignmentRepo.save(TimetableAssignment.builder()
                .timetable(published).course(course).room(room).timeSlot(slot)
                .assignmentType(TimetableAssignment.AssignmentType.LECTURE)
                .isLocked(false).manuallyAssigned(true)
                .createdAt(LocalDateTime.now()).build());

        draftId = timetableRepo.save(Timetable.builder()
                .name(MARK + "DRAFT").academicYear("2025-26")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(Timetable.Status.DRAFT)
                .build()).getId();
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
