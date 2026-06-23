package gr.upatras.ceid.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.CourseTeacher;
import gr.upatras.ceid.timetable.entity.Teacher;
import gr.upatras.ceid.timetable.repository.CourseRepository;
import gr.upatras.ceid.timetable.repository.CourseTeacherRepository;
import gr.upatras.ceid.timetable.repository.TeacherRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Φ2a — structured Course↔Teacher M2M sync.
 *
 * Επιβεβαιώνει ότι το {@code course_teachers} M2M γίνεται source-of-truth μέσω
 * structured API (teacherId + role) και ότι το {@code teachersText} παράγεται
 * derived (PRIMARY-first) ώστε τα read-paths (DTO/snapshot/fallback) να μένουν
 * αμετάβλητα.
 *
 * Conventions ίδιες με {@link NonBlockingPlacementTest}: {@code @SpringBootTest}
 * + MockMvc, marker-based seed/cleanup ({@code TEST_CTS_}), ΧΩΡΙΣ
 * {@code @Transactional} (τα writes γίνονται commit ώστε να τα δει το επόμενο
 * request μέσα από το filter chain). Τα endpoints είναι ADMIN-gated → JWT μέσω
 * {@code POST /api/auth/login} (admin/admin123).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CourseTeacherSyncTest {

    private static final String MARK = "TEST_CTS_";
    private static final String NAME_A = MARK + "Alpha";
    private static final String NAME_B = MARK + "Beta";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CourseRepository courseRepo;
    @Autowired TeacherRepository teacherRepo;
    @Autowired CourseTeacherRepository courseTeacherRepo;

    private Long teacherAId, teacherBId, course1Id, course2Id;

    @BeforeEach
    void setUp() { cleanup(); seed(); }

    @AfterEach
    void tearDown() { cleanup(); }

    // ── (1) PUT 2 teachers (PRIMARY+SECONDARY) → 2 rows, GET τα επιστρέφει, text PRIMARY-first ──
    @Test
    @SuppressWarnings("unchecked")
    void putTwoTeachers_createsRows_getReturns_textPrimaryFirst() throws Exception {
        String token = adminToken();

        // Beta=PRIMARY, Alpha=SECONDARY → το PRIMARY-first ordering δίνει Beta ΠΡΙΝ Alpha,
        // παρότι αλφαβητικά Alpha < Beta (αποδεικνύει role-priority, όχι απλή αλφαβητική).
        putCourseTeachers(token, course1Id, List.of(
                Map.of("teacherId", teacherBId, "role", "PRIMARY"),
                Map.of("teacherId", teacherAId, "role", "SECONDARY")))
            .andExpect(status().isOk());

        assertEquals(2, courseTeacherRepo.findByCourseId(course1Id).size(), "2 rows δημιουργήθηκαν");

        MvcResult getRes = mockMvc.perform(get("/api/courses/{id}/teachers", course1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> rels = objectMapper.readValue(
                getRes.getResponse().getContentAsString(), List.class);
        assertEquals(2, rels.size(), "GET /{id}/teachers επιστρέφει 2 relations");

        String text = courseRepo.findById(course1Id).orElseThrow().getTeachersText();
        assertTrue(text.contains(NAME_A) && text.contains(NAME_B), "teachersText περιέχει και τους δύο");
        assertTrue(text.indexOf(NAME_B) < text.indexOf(NAME_A),
                "PRIMARY (Beta) ΠΡΙΝ SECONDARY (Alpha) — regenerated PRIMARY-first");
    }

    // ── (2) PUT ξανά χωρίς τον SECONDARY → 1 row, teachersText ενημερωμένο ──
    @Test
    void putRemoveSecondary_leavesOneRow_textUpdated() throws Exception {
        String token = adminToken();
        putCourseTeachers(token, course1Id, List.of(
                Map.of("teacherId", teacherBId, "role", "PRIMARY"),
                Map.of("teacherId", teacherAId, "role", "SECONDARY")))
            .andExpect(status().isOk());

        putCourseTeachers(token, course1Id, List.of(
                Map.of("teacherId", teacherBId, "role", "PRIMARY")))
            .andExpect(status().isOk());

        assertEquals(1, courseTeacherRepo.findByCourseId(course1Id).size(), "έμεινε 1 row");
        String text = courseRepo.findById(course1Id).orElseThrow().getTeachersText();
        assertEquals(NAME_B, text, "teachersText = μόνο ο PRIMARY (Beta)");
        assertFalse(text.contains(NAME_A), "ο αφαιρεμένος SECONDARY (Alpha) έφυγε από το text");
    }

    // ── (3) Idempotency: ίδιο PUT 2× → ίδιο αποτέλεσμα, καμία διπλή row ──
    @Test
    void putSameTwice_isIdempotent_noDuplicateRows() throws Exception {
        String token = adminToken();
        List<Map<String, Object>> refs = List.of(
                Map.of("teacherId", teacherBId, "role", "PRIMARY"),
                Map.of("teacherId", teacherAId, "role", "SECONDARY"));

        putCourseTeachers(token, course1Id, refs).andExpect(status().isOk());
        putCourseTeachers(token, course1Id, refs).andExpect(status().isOk());

        assertEquals(2, courseTeacherRepo.findByCourseId(course1Id).size(),
                "idempotent: ακόμα 2 rows, καμία διπλή (unique (course,teacher,role))");
    }

    // ── (4) άγνωστο teacherId → 400· άγνωστο role → 400· άγνωστο course (path) → 404 ──
    @Test
    void validation_unknownTeacher400_unknownRole400_unknownCoursePath404() throws Exception {
        String token = adminToken();

        putCourseTeachers(token, course1Id, List.of(
                Map.of("teacherId", 999_999_999L, "role", "PRIMARY")))
            .andExpect(status().isBadRequest());

        putCourseTeachers(token, course1Id, List.of(
                Map.of("teacherId", teacherAId, "role", "BOSS")))
            .andExpect(status().isBadRequest());

        putCourseTeachers(token, 999_999_999L, List.of())
            .andExpect(status().isNotFound());
    }

    // ── (5) reverse sync: τα courses που ΕΧΑΣΑΝ τον teacher ενημερώνουν το teachersText ──
    @Test
    void reverseSync_updatesTextOfCoursesThatLostTeacher() throws Exception {
        String token = adminToken();

        // Σύνδεσε τον Alpha και στα δύο μαθήματα.
        putTeacherCourses(token, teacherAId, List.of(
                Map.of("courseId", course1Id, "role", "PRIMARY"),
                Map.of("courseId", course2Id, "role", "PRIMARY")))
            .andExpect(status().isOk());

        assertEquals(NAME_A, courseRepo.findById(course1Id).orElseThrow().getTeachersText());
        assertEquals(NAME_A, courseRepo.findById(course2Id).orElseThrow().getTeachersText());

        // Αφαίρεσε το course2 → πρέπει να ενημερωθεί ΚΑΙ το teachersText του course2.
        putTeacherCourses(token, teacherAId, List.of(
                Map.of("courseId", course1Id, "role", "PRIMARY")))
            .andExpect(status().isOk());

        assertEquals(NAME_A, courseRepo.findById(course1Id).orElseThrow().getTeachersText(),
                "το course1 κρατά τον Alpha");
        assertTrue(courseRepo.findById(course2Id).orElseThrow().getTeachersText().isBlank(),
                "το course2 έχασε τον Alpha → teachersText άδειο");
        assertEquals(1, courseTeacherRepo.findByTeacherId(teacherAId).size(),
                "ο Alpha συνδέεται πλέον με 1 μάθημα");
    }

    // ── (6) owner-check: TEACHER χρήστης σε ΑΛΛΟΝ teacherId → 403 ──
    @Test
    void reverseSync_teacherEditingAnotherTeacher_forbidden403() throws Exception {
        // Ο dev λογαριασμός "teacher" (role TEACHER, χωρίς linked teacherId) ΔΕΝ
        // επιτρέπεται να επεξεργαστεί τον seeded teacherA → 403 (ίδια συμπεριφορά με
        // τα update/updateConstraints).
        String token = teacherToken();
        putTeacherCourses(token, teacherAId, List.of(
                Map.of("courseId", course1Id, "role", "PRIMARY")))
            .andExpect(status().isForbidden());
    }

    // ── request helpers ─────────────────────────────────────────────────────────────
    private ResultActions putCourseTeachers(String token, Long courseId,
                                            List<Map<String, Object>> refs) throws Exception {
        return mockMvc.perform(put("/api/courses/{id}/teachers", courseId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refs)));
    }

    private ResultActions putTeacherCourses(String token, Long teacherId,
                                            List<Map<String, Object>> refs) throws Exception {
        return mockMvc.perform(put("/api/teachers/{id}/courses", teacherId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refs)));
    }

    // ── auth helper ─────────────────────────────────────────────────────────────────
    private String adminToken() throws Exception { return loginToken("admin", "admin123"); }
    private String teacherToken() throws Exception { return loginToken("teacher", "teacher123"); }

    @SuppressWarnings("unchecked")
    private String loginToken(String username, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        return (String) body.get("token");
    }

    // ── seed / cleanup ────────────────────────────────────────────────────────────────
    private void seed() {
        Teacher a = teacherRepo.save(Teacher.builder()
                .name(NAME_A).shortName(MARK + "ALPHA")
                .teacherType(Teacher.TeacherType.PROFESSOR).department("CEID")
                .active(true).build());
        teacherAId = a.getId();

        Teacher b = teacherRepo.save(Teacher.builder()
                .name(NAME_B).shortName(MARK + "BETA")
                .teacherType(Teacher.TeacherType.PROFESSOR).department("CEID")
                .active(true).build());
        teacherBId = b.getId();

        Course c1 = courseRepo.save(Course.builder()
                .code(MARK + "C1").name("CTS Course 1").semester(1).studyYear(1)
                .courseType(Course.CourseType.REQUIRED).semesterType(Course.SemesterType.FALL)
                .active(true).visibleInTimetable(true).build());
        course1Id = c1.getId();

        Course c2 = courseRepo.save(Course.builder()
                .code(MARK + "C2").name("CTS Course 2").semester(1).studyYear(1)
                .courseType(Course.CourseType.REQUIRED).semesterType(Course.SemesterType.FALL)
                .active(true).visibleInTimetable(true).build());
        course2Id = c2.getId();
    }

    private void cleanup() {
        // Πρώτα τα M2M rows (FK), μετά courses & teachers.
        courseRepo.findAll().stream()
                .filter(c -> c.getCode() != null && c.getCode().startsWith(MARK))
                .forEach(c -> courseTeacherRepo.deleteAll(courseTeacherRepo.findByCourseId(c.getId())));
        teacherRepo.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().startsWith(MARK))
                .forEach(t -> courseTeacherRepo.deleteAll(courseTeacherRepo.findByTeacherId(t.getId())));
        courseRepo.findAll().stream()
                .filter(c -> c.getCode() != null && c.getCode().startsWith(MARK))
                .forEach(c -> courseRepo.deleteById(c.getId()));
        teacherRepo.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().startsWith(MARK))
                .forEach(t -> teacherRepo.deleteById(t.getId()));
    }
}
