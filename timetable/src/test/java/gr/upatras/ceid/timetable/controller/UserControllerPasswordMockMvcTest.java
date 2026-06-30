package gr.upatras.ceid.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.upatras.ceid.timetable.entity.User;
import gr.upatras.ceid.timetable.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 4 — MockMvc tests για τα password endpoints του {@link UserController}
 * (security-sensitive surface που ήταν χωρίς test).
 *
 * Conventions ίδιες με {@link NonBlockingPlacementTest}: {@code @SpringBootTest} + MockMvc
 * μέσω ΟΛΟΚΛΗΡΟΥ filter chain, real JWT, ΧΩΡΙΣ {@code @Transactional}, marker-based
 * seed/cleanup ({@code TEST_T4_}: ο throwaway user γίνεται commit ώστε να τον δει το request
 * μέσα από το filter chain, και διαγράφεται στο tearDown). Καθαρά νέο test file — δεν αγγίζει
 * production code.
 *
 * Security (SecurityConfig): {@code PUT /api/users/me/password} → κάθε authenticated· κάθε
 * άλλο {@code /api/users/**} → ADMIN. Με ενεργό anonymous, unauthenticated → 403, εξ ου και
 * η ανοχή 401|403 στα μη-εξουσιοδοτημένα.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerPasswordMockMvcTest {

    private static final String MARK = "TEST_T4_";
    private static final String USERNAME = MARK + "USER";
    private static final String ORIGINAL_PW = "t4-original-pw-123";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepo;
    @Autowired BCryptPasswordEncoder encoder;

    private Long testUserId;

    @BeforeEach
    void setUp() {
        cleanup();
        User u = userRepo.save(User.builder()
                .username(USERNAME)
                .passwordHash(encoder.encode(ORIGINAL_PW))
                .email("t4@ceid.upatras.gr")
                .fullName("T4 Test User")
                .role(User.Role.STUDENT)
                .sector("ΕΒ")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());
        testUserId = u.getId();
    }

    @AfterEach
    void tearDown() { cleanup(); }

    // ── Unauthorized → 401/403 (security blocks πριν τον controller, καμία μεταβολή) ──

    @Test
    void resetPassword_noAuth_isRejectedAndUnchanged() throws Exception {
        MvcResult res = mockMvc.perform(put("/api/users/{id}/password", testUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "new-pass-123456"))))
                .andReturn();
        assertUnauthorizedOrForbiddenAndUnchanged(res);
    }

    @Test
    void changeMyPassword_noAuth_isRejectedAndUnchanged() throws Exception {
        MvcResult res = mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", ORIGINAL_PW, "newPassword", "new-pass-123456"))))
                .andReturn();
        assertUnauthorizedOrForbiddenAndUnchanged(res);
    }

    // ── Authorized happy-paths → 200 ───────────────────────────────────────────────

    @Test
    void resetPassword_asAdmin_returns200AndChangesPassword() throws Exception {
        String admin = token("admin", "admin123");
        mockMvc.perform(put("/api/users/{id}/password", testUserId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "admin-set-pw-123"))))
                .andExpect(status().isOk());

        User reloaded = userRepo.findById(testUserId).orElseThrow();
        assertTrue(encoder.matches("admin-set-pw-123", reloaded.getPasswordHash()),
                "ο ADMIN άλλαξε επιτυχώς τον κωδικό του χρήστη");
    }

    @Test
    void changeMyPassword_asOwner_returns200AndChangesPassword() throws Exception {
        String mine = token(USERNAME, ORIGINAL_PW);
        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", ORIGINAL_PW, "newPassword", "my-new-pw-123"))))
                .andExpect(status().isOk());

        User reloaded = userRepo.findById(testUserId).orElseThrow();
        assertTrue(encoder.matches("my-new-pw-123", reloaded.getPasswordHash()),
                "ο χρήστης άλλαξε επιτυχώς τον δικό του κωδικό");
    }

    // ── Authorized-but-insufficient-role → 403 ─────────────────────────────────────

    @Test
    void resetPassword_asNonAdmin_returns403AndUnchanged() throws Exception {
        String student = token(USERNAME, ORIGINAL_PW); // ρόλος STUDENT
        mockMvc.perform(put("/api/users/{id}/password", testUserId)
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "should-not-apply-123"))))
                .andExpect(status().isForbidden());

        User reloaded = userRepo.findById(testUserId).orElseThrow();
        assertTrue(encoder.matches(ORIGINAL_PW, reloaded.getPasswordHash()),
                "ο STUDENT ΔΕΝ μπόρεσε να αλλάξει κωδικό (403, καμία μεταβολή)");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private void assertUnauthorizedOrForbiddenAndUnchanged(MvcResult res) {
        int s = res.getResponse().getStatus();
        assertTrue(s == 401 || s == 403, "αναμένεται 401 ή 403, βρέθηκε " + s);
        User reloaded = userRepo.findById(testUserId).orElseThrow();
        assertTrue(encoder.matches(ORIGINAL_PW, reloaded.getPasswordHash()),
                "unauthorized → καμία μεταβολή κωδικού");
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

    private void cleanup() {
        userRepo.findByUsername(USERNAME).ifPresent(u -> userRepo.deleteById(u.getId()));
    }
}
