package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.config.JwtUtil;
import gr.upatras.ceid.timetable.entity.User;
import gr.upatras.ceid.timetable.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder;

    public AuthController(UserRepository userRepo, JwtUtil jwtUtil,
                          BCryptPasswordEncoder encoder) {
        this.userRepo  = userRepo;
        this.jwtUtil   = jwtUtil;
        this.encoder   = encoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");

        return userRepo.findByUsername(username)
            .filter(u -> Boolean.TRUE.equals(u.getActive()))
            .filter(u -> encoder.matches(password, u.getPasswordHash()))
            .map(u -> ResponseEntity.ok((Object) Map.of(
                "token",    jwtUtil.generate(u.getUsername(), u.getRole().name()),
                "username", u.getUsername(),
                "fullName", u.getFullName(),
                "role",     u.getRole().name()
            )))
            .orElse(ResponseEntity.status(401)
                .body(Map.of("error", "Λάθος username ή κωδικός.")));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(org.springframework.security.core.Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        return userRepo.findByUsername(auth.getName())
            .map(u -> ResponseEntity.ok((Object) Map.of(
                "username", u.getUsername(),
                "fullName", u.getFullName(),
                "role",     u.getRole().name()
            )))
            .orElse(ResponseEntity.status(404).build());
    }
}