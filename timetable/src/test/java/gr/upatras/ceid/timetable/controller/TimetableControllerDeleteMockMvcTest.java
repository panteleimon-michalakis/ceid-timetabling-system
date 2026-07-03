package gr.upatras.ceid.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.upatras.ceid.timetable.entity.Timetable;
import gr.upatras.ceid.timetable.repository.TimetableRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E-3 — αναπαραγωγή/επιβεβαίωση για {@link TimetableController#delete}.
 *
 * Η αρχική θεωρία ("403 σε μη-DRAFT πρόγραμμα, σιωπηλή αποτυχία UI") ΔΕΝ επιβεβαιώθηκε:
 * ο controller δεν έχει κανέναν έλεγχο status, διαγράφει unconditionally. Το πραγματικό
 * 403 είναι role-gate (SecurityConfig: DELETE /api/timetables/* → μόνο ADMIN). Conventions
 * ίδιες με {@link UserControllerPasswordMockMvcTest}: πλήρες filter chain, real JWT, ΧΩΡΙΣ
 * {@code @Transactional}, marker-based seed/cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TimetableControllerDeleteMockMvcTest {

    private static final String MARK = "TEST_E3_";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TimetableRepository timetableRepo;

    @AfterEach
    void cleanup() {
        timetableRepo.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().startsWith(MARK))
                .forEach(t -> timetableRepo.deleteById(t.getId()));
    }

    @Test
    void delete_asAdmin_onPublishedTimetable_returns204AndRemoves() throws Exception {
        Long id = seedTimetable(MARK + "PUBLISHED", Timetable.Status.PUBLISHED);

        mockMvc.perform(delete("/api/timetables/{id}", id)
                        .header("Authorization", "Bearer " + token("admin", "admin123")))
                .andExpect(status().isNoContent());

        assertFalse(timetableRepo.existsById(id), "ADMIN διαγράφει καθαρά ένα PUBLISHED πρόγραμμα — καμία status-block");
    }

    @Test
    void delete_asTeacher_returns403AndTimetableSurvives() throws Exception {
        Long id = seedTimetable(MARK + "TEACHER_ATTEMPT", Timetable.Status.DRAFT);

        mockMvc.perform(delete("/api/timetables/{id}", id)
                        .header("Authorization", "Bearer " + token("teacher", "teacher123")))
                .andExpect(status().isForbidden());

        assertTrue(timetableRepo.existsById(id), "TEACHER δεν έχει δικαίωμα διαγραφής (role-gate by design) — το πρόγραμμα επιβιώνει");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private Long seedTimetable(String name, Timetable.Status status) {
        Timetable t = timetableRepo.save(Timetable.builder()
                .name(name)
                .academicYear("2025-26")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build());
        return t.getId();
    }

    @SuppressWarnings("unchecked")
    private String token(String username, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        return (String) body.get("token");
    }
}
