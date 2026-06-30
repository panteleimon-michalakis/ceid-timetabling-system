package gr.upatras.ceid.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 4 — MockMvc tests για τον {@link AuthController} (το auth boundary της εφαρμογής).
 *
 * Conventions ίδιες με {@link NonBlockingPlacementTest}: {@code @SpringBootTest} + MockMvc
 * μέσω ΟΛΟΚΛΗΡΟΥ filter chain (JwtAuthFilter), real JWT μέσω {@code POST /api/auth/login}
 * με τον seeded admin (admin/admin123). Όλα τα tests είναι read-only (το login/me δεν
 * μεταβάλλουν δεδομένα), άρα ΧΩΡΙΣ seed/cleanup. Καθαρά νέο test file — δεν αγγίζει
 * production code.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerMockMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @SuppressWarnings("unchecked")
    void login_validCredentials_returns200WithToken() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin", "password", "admin123"))))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        assertNotNull(body.get("token"), "επιτυχές login → επιστρέφει token");
        assertFalse(String.valueOf(body.get("token")).isBlank(), "το token δεν είναι κενό");
        assertEquals("ADMIN", body.get("role"), "ο ρόλος του admin είναι ADMIN");
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin", "password", "definitely-wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unknownUser_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "TEST_T4_NO_SUCH_USER", "password", "whatever"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withoutToken_isRejected() throws Exception {
        // Χωρίς token: το /api/auth/me είναι permitAll, αλλά ο controller δεν επιστρέφει
        // δεδομένα χρήστη (401 για null principal). Robust assertion: οποιοδήποτε 4xx.
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @SuppressWarnings("unchecked")
    void me_withValidToken_returns200WithUsername() throws Exception {
        String token = adminToken();
        MvcResult res = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        assertEquals("admin", body.get("username"), "/me επιστρέφει τον συνδεδεμένο χρήστη");
    }

    // ── auth helper (ίδιο pattern με NonBlockingPlacementTest.adminToken) ─────────────
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
}
