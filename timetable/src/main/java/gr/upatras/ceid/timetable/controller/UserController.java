package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.User;
import gr.upatras.ceid.timetable.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Διαχείριση λογαριασμών χρηστών.
 *
 * Πρόσβαση (επιβάλλεται στο SecurityConfig):
 *  - /api/users/**           → μόνο ADMIN
 *  - /api/users/me/password  → κάθε συνδεδεμένος χρήστης (αλλαγή ΔΙΚΟΥ του κωδικού)
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepo;
    private final BCryptPasswordEncoder encoder;

    public UserController(UserRepository userRepo, BCryptPasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.encoder = encoder;
    }

    // ── List ───────────────────────────────────────────────────────────────

    @GetMapping
    public List<Map<String, Object>> getAll(@RequestParam(required = false) String role) {
        List<User> users;
        if (role != null && !role.isBlank()) {
            try {
                users = userRepo.findByRole(User.Role.valueOf(role.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                users = List.of();
            }
        } else {
            users = userRepo.findAll();
        }
        return users.stream().map(this::toDto).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return userRepo.findById(id)
                .map(u -> ResponseEntity.ok((Object) toDto(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Create ─────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String username = str(body.get("username")).trim();
        String password = str(body.get("password"));
        String fullName = str(body.get("fullName")).trim();
        String roleText = str(body.get("role")).trim().toUpperCase(Locale.ROOT);

        if (username.isBlank() || password.isBlank() || fullName.isBlank() || roleText.isBlank()) {
            return badRequest("Υποχρεωτικά πεδία: username, password, fullName, role.");
        }
        if (password.length() < 6) {
            return badRequest("Ο κωδικός πρέπει να έχει τουλάχιστον 6 χαρακτήρες.");
        }
        if (userRepo.findByUsername(username).isPresent()) {
            return badRequest("Υπάρχει ήδη χρήστης με αυτό το username.");
        }

        User.Role role;
        try {
            role = User.Role.valueOf(roleText);
        } catch (IllegalArgumentException ex) {
            return badRequest("Μη έγκυρος ρόλος: " + roleText);
        }

        Long teacherId = body.get("teacherId") instanceof Number n ? n.longValue() : null;

        User user = User.builder()
                .username(username)
                .passwordHash(encoder.encode(password))
                .email(str(body.get("email")).isBlank() ? null : str(body.get("email")).trim())
                .fullName(fullName)
                .role(role)
                .sector(str(body.get("sector")).isBlank() ? null : str(body.get("sector")).trim())
                .teacherId(teacherId)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(toDto(userRepo.save(user)));
    }

    // ── Update (στοιχεία + ρόλος + active, ΟΧΙ κωδικός) ──────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return userRepo.findById(id).map(user -> {
            if (body.containsKey("fullName")) {
                String fn = str(body.get("fullName")).trim();
                if (!fn.isBlank()) user.setFullName(fn);
            }
            if (body.containsKey("email")) {
                String em = str(body.get("email")).trim();
                user.setEmail(em.isBlank() ? null : em);
            }
            if (body.containsKey("sector")) {
                String se = str(body.get("sector")).trim();
                user.setSector(se.isBlank() ? null : se);
            }
            if (body.containsKey("role")) {
                try {
                    user.setRole(User.Role.valueOf(str(body.get("role")).trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) { /* αγνόησε μη έγκυρο ρόλο */ }
            }
            if (body.containsKey("active")) {
                Object a = body.get("active");
                user.setActive(Boolean.TRUE.equals(a) || "true".equalsIgnoreCase(String.valueOf(a)));
            }
            if (body.containsKey("teacherId")) {
                user.setTeacherId(body.get("teacherId") instanceof Number n ? n.longValue() : null);
            }
            return ResponseEntity.ok((Object) toDto(userRepo.save(user)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Reset password (ADMIN ορίζει νέο κωδικό για χρήστη) ──────────────────

    @PutMapping("/{id}/password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newPassword = body.getOrDefault("password", "");
        if (newPassword.length() < 6) {
            return badRequest("Ο νέος κωδικός πρέπει να έχει τουλάχιστον 6 χαρακτήρες.");
        }
        return userRepo.findById(id).map(user -> {
            user.setPasswordHash(encoder.encode(newPassword));
            userRepo.save(user);
            return ResponseEntity.ok((Object) Map.of("message", "Ο κωδικός ενημερώθηκε."));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        User target = userRepo.findById(id).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();

        // Ασφάλεια: ο ADMIN δεν μπορεί να διαγράψει τον ΕΑΥΤΟ του
        // (αποφυγή κλειδώματος έξω από το σύστημα).
        if (auth != null && target.getUsername().equals(auth.getName())) {
            return badRequest("Δεν μπορείς να διαγράψεις τον δικό σου λογαριασμό.");
        }
        try {
            userRepo.deleteById(id);
            userRepo.flush();
        } catch (Exception ex) {
            return badRequest("Ο χρήστης έχει συνδεδεμένα δεδομένα στο σύστημα "
                + "και δεν μπορεί να διαγραφεί. Χρησιμοποίησε απενεργοποίηση.");
        }
        return ResponseEntity.noContent().build();
    }

    // ── Self change-password (κάθε συνδεδεμένος χρήστης) ─────────────────────

    @PutMapping("/me/password")
    public ResponseEntity<?> changeMyPassword(@RequestBody Map<String, String> body, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();

        String current = body.getOrDefault("currentPassword", "");
        String next = body.getOrDefault("newPassword", "");

        if (next.length() < 6) {
            return badRequest("Ο νέος κωδικός πρέπει να έχει τουλάχιστον 6 χαρακτήρες.");
        }

        return userRepo.findByUsername(auth.getName()).map(user -> {
            if (user.getPasswordHash() == null || !encoder.matches(current, user.getPasswordHash())) {
                return badRequest("Ο τρέχων κωδικός είναι λάθος.");
            }
            user.setPasswordHash(encoder.encode(next));
            userRepo.save(user);
            return ResponseEntity.ok((Object) Map.of("message", "Ο κωδικός σου ενημερώθηκε."));
        }).orElse(ResponseEntity.status(404).build());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> toDto(User u) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", u.getId());
        dto.put("username", u.getUsername());
        dto.put("fullName", u.getFullName());
        dto.put("email", u.getEmail());
        dto.put("role", u.getRole() != null ? u.getRole().name() : null);
        dto.put("sector", u.getSector());
        dto.put("teacherId", u.getTeacherId());
        dto.put("active", u.getActive());
        dto.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
        // Σημείωση: ΠΟΤΕ δεν επιστρέφουμε το passwordHash.
        return dto;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}