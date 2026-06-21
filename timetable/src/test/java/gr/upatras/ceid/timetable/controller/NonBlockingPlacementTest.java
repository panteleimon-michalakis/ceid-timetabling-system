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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature #2 (non-blocking manual editing) — backend.
 *
 * Επιβεβαιώνει ότι η χειροκίνητη τοποθέτηση/μετακίνηση:
 *  - ΓΙΝΕΤΑΙ πάντα για παραβιάσεις scheduling-constraints (advisory) → 200 + μη-κενά
 *    {@code warnings}, και η ανάθεση όντως αποθηκεύεται·
 *  - εξακολουθεί να ΜΠΛΟΚΑΡΕΤΑΙ (4xx) για τους 3 structural ελέγχους (#1/#3/#4),
 *    χωρίς αποθήκευση.
 *
 * Conventions ίδιες με {@link PublicTimetableControllerTest}: {@code @SpringBootTest}
 * + MockMvc, marker-based seed/cleanup ({@code TEST_NB_}), ΧΩΡΙΣ {@code @Transactional}
 * (τα seeds γίνονται commit ώστε να τα δει το request μέσα από το filter chain).
 * Τα endpoints add/move θέλουν ADMIN/TEACHER → JWT μέσω {@code POST /api/auth/login}
 * με τον seeded admin (admin/admin123) + {@code Authorization: Bearer}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NonBlockingPlacementTest {

    private static final String MARK = "TEST_NB_";
    private static final String ROOM_CODE = MARK + "R1";
    private static final String COURSE_CLEAN = MARK + "C_CLEAN";
    private static final String COURSE_OTHER = MARK + "C_OTHER";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TimetableRepository timetableRepo;
    @Autowired TimetableAssignmentRepository assignmentRepo;
    @Autowired CourseRepository courseRepo;
    @Autowired RoomRepository roomRepo;
    @Autowired TimeSlotRepository timeSlotRepo;

    private Long roomId, slot1Id, slot2Id;
    private Long courseCleanId, courseOtherId;
    private Long ttDoubleId, ttStructId, ttMoveId, ttCleanId;
    private Long moveAssignmentId;

    @BeforeEach
    void setUp() { cleanup(); seed(); }

    @AfterEach
    void tearDown() { cleanup(); }

    // ── (1) advisory add → 200 + warnings + persisted ──────────────────────────────
    @Test
    @SuppressWarnings("unchecked")
    void advisoryAdd_roomDoubleBook_returns200WithWarningsAndPersists() throws Exception {
        String token = adminToken();
        int before = assignmentRepo.findByTimetableId(ttDoubleId).size();   // 1 (seeded)

        // Δεύτερη ανάθεση ΑΛΛΟΥ μαθήματος στο ΙΔΙΟ (room, slot) → room double-book (#10, advisory)
        MvcResult res = mockMvc.perform(post("/api/timetables/{id}/assignments", ttDoubleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseId", courseCleanId,
                                "roomId", roomId,
                                "timeSlotId", slot1Id,
                                "assignmentType", "LECTURE"))))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        List<Object> warnings = (List<Object>) body.get("warnings");
        assertNotNull(warnings, "το response περιέχει key warnings");
        assertFalse(warnings.isEmpty(), "advisory placement → μη-κενά warnings");

        assertEquals(before + 1, assignmentRepo.findByTimetableId(ttDoubleId).size(),
                "η ανάθεση αποθηκεύτηκε παρά το advisory");
    }

    // ── (2) structural block add → 4xx + NOT persisted ─────────────────────────────
    @Test
    void structuralBlock_examTypeInSemester_returns4xxAndNotPersisted() throws Exception {
        String token = adminToken();
        int before = assignmentRepo.findByTimetableId(ttStructId).size();   // 0

        // assignmentType=EXAM σε SEMESTER πρόγραμμα → #3 structural → HARD block
        mockMvc.perform(post("/api/timetables/{id}/assignments", ttStructId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseId", courseCleanId,
                                "roomId", roomId,
                                "timeSlotId", slot1Id,
                                "assignmentType", "EXAM"))))
                .andExpect(status().isBadRequest());

        assertEquals(before, assignmentRepo.findByTimetableId(ttStructId).size(),
                "structural block → καμία αποθήκευση");
    }

    // ── (3) advisory move → 200 + warnings + persisted at new location ──────────────
    @Test
    @SuppressWarnings("unchecked")
    void advisoryMove_intoOccupiedSlot_returns200WithWarningsAndPersists() throws Exception {
        String token = adminToken();

        // Μετακίνηση X (slot1) → slot2, όπου υπάρχει ήδη το Y (ίδια room) → double-book advisory
        MvcResult res = mockMvc.perform(put("/api/timetables/assignments/{aid}/move", moveAssignmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("timeSlotId", slot2Id))))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        List<Object> warnings = (List<Object>) body.get("warnings");
        assertNotNull(warnings, "το response περιέχει key warnings");
        assertFalse(warnings.isEmpty(), "advisory move → μη-κενά warnings");

        TimetableAssignment moved = assignmentRepo.findById(moveAssignmentId).orElseThrow();
        assertEquals(slot2Id, moved.getTimeSlot().getId(), "η ανάθεση μετακινήθηκε στη νέα θέση");
    }

    // ── (4) clean add → 200 + empty warnings + persisted ───────────────────────────
    @Test
    @SuppressWarnings("unchecked")
    void cleanAdd_noViolation_returns200WithEmptyWarnings() throws Exception {
        String token = adminToken();

        MvcResult res = mockMvc.perform(post("/api/timetables/{id}/assignments", ttCleanId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseId", courseCleanId,
                                "roomId", roomId,
                                "timeSlotId", slot1Id,
                                "assignmentType", "LECTURE"))))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        List<Object> warnings = (List<Object>) body.get("warnings");
        assertNotNull(warnings, "το key warnings υπάρχει πάντα (forward-compat array)");
        assertTrue(warnings.isEmpty(), "καθαρή τοποθέτηση → κενά warnings");

        assertEquals(1, assignmentRepo.findByTimetableId(ttCleanId).size(),
                "η καθαρή ανάθεση αποθηκεύτηκε");
    }

    // ── auth helper ────────────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private String adminToken() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin", "password", "admin123"))))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        return (String) body.get("token");
    }

    // ── seed / cleanup ───────────────────────────────────────────────────────────
    private void seed() {
        Room room = roomRepo.save(Room.builder()
                .name("NB Test Room").code(ROOM_CODE).capacity(200)
                .roomType(Room.RoomType.AMPHITHEATER)
                .hasProjector(false).hasComputers(false)
                .availableForExams(false).availableForSemester(true)
                .active(true).build());
        roomId = room.getId();

        TimeSlot slot1 = timeSlotRepo.save(TimeSlot.builder()
                .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
                .slotType(TimeSlot.SlotType.SEMESTER).build());
        slot1Id = slot1.getId();

        TimeSlot slot2 = timeSlotRepo.save(TimeSlot.builder()
                .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0))
                .slotType(TimeSlot.SlotType.SEMESTER).build());
        slot2Id = slot2.getId();

        // GENERAL_EDUCATION, 2ο έτος → χωρίς year-room περιορισμό· FALL για να ταιριάζει με το πρόγραμμα.
        Course courseClean = courseRepo.save(Course.builder()
                .code(COURSE_CLEAN).name("NB Clean Course")
                .semester(3).studyYear(2)
                .courseType(Course.CourseType.GENERAL_EDUCATION)
                .lectureHours(2).tutorialHours(0).labHours(0)
                .expectedStudents(10)
                .teachersText("Καρβέλης")
                .semesterType(Course.SemesterType.FALL)
                .active(true).visibleInTimetable(true)
                .build());
        courseCleanId = courseClean.getId();

        Course courseOther = courseRepo.save(Course.builder()
                .code(COURSE_OTHER).name("NB Other Course")
                .semester(5).studyYear(3)
                .courseType(Course.CourseType.GENERAL_EDUCATION)
                .lectureHours(2).tutorialHours(0).labHours(0)
                .expectedStudents(10)
                .teachersText("Παπαδόπουλος")
                .semesterType(Course.SemesterType.FALL)
                .active(true).visibleInTimetable(true)
                .build());
        courseOtherId = courseOther.getId();

        // (1) ttDouble — προϋπάρχουσα ανάθεση courseOther στο (room, slot1)
        Timetable ttDouble = saveSemesterTimetable(MARK + "DOUBLE");
        ttDoubleId = ttDouble.getId();
        saveAssignment(ttDouble, courseOther, room, slot1);

        // (2) ttStruct — κενό
        ttStructId = saveSemesterTimetable(MARK + "STRUCT").getId();

        // (3) ttMove — X=courseClean@slot1, Y=courseOther@slot2 (ίδια room)
        Timetable ttMove = saveSemesterTimetable(MARK + "MOVE");
        ttMoveId = ttMove.getId();
        moveAssignmentId = saveAssignment(ttMove, courseClean, room, slot1).getId();
        saveAssignment(ttMove, courseOther, room, slot2);

        // (4) ttClean — κενό
        ttCleanId = saveSemesterTimetable(MARK + "CLEAN").getId();
    }

    private Timetable saveSemesterTimetable(String name) {
        return timetableRepo.save(Timetable.builder()
                .name(name).academicYear("2025-26")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(Timetable.Status.DRAFT)
                .build());
    }

    private TimetableAssignment saveAssignment(Timetable tt, Course course, Room room, TimeSlot slot) {
        return assignmentRepo.save(TimetableAssignment.builder()
                .timetable(tt).course(course).room(room).timeSlot(slot)
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
        roomRepo.findByCode(ROOM_CODE).ifPresent(r -> roomRepo.deleteById(r.getId()));
        courseRepo.findAll().stream()
                .filter(c -> c.getCode() != null && c.getCode().startsWith(MARK))
                .forEach(c -> courseRepo.deleteById(c.getId()));
        if (slot1Id != null) timeSlotRepo.findById(slot1Id).ifPresent(s -> timeSlotRepo.deleteById(s.getId()));
        if (slot2Id != null) timeSlotRepo.findById(slot2Id).ifPresent(s -> timeSlotRepo.deleteById(s.getId()));
    }
}
