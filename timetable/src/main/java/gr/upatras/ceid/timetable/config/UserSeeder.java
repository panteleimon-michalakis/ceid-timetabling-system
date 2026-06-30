package gr.upatras.ceid.timetable.config;

import gr.upatras.ceid.timetable.entity.User;
import gr.upatras.ceid.timetable.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Order(1)
@Profile("!prod") // default creds (admin/admin123 κ.λπ.) σπέρνονται σε dev+test, ΟΧΙ σε prod
public class UserSeeder implements ApplicationRunner {

    private final UserRepository userRepo;
    private final BCryptPasswordEncoder encoder;

    public UserSeeder(UserRepository userRepo, BCryptPasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.encoder  = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Δημιουργεί τους default users ΜΟΝΟ αν δεν υπάρχουν ήδη.
        // Δεν αντικαθιστά ποτέ κωδικούς ή ρόλους υπαρχόντων users.
        seed("admin",   "admin123",   "Διαχειριστής Συστήματος", User.Role.ADMIN);
        seed("teacher", "teacher123", "Δοκιμαστικός Καθηγητής",  User.Role.TEACHER);
        seed("student", "student123", "Δοκιμαστικός Φοιτητής",   User.Role.STUDENT);
        System.out.println(">>> UserSeeder: ολοκληρώθηκε.");
    }

    private void seed(String username, String password,
                      String fullName, User.Role role) {
        // Αν ο χρήστης υπάρχει ήδη, δεν κάνουμε τίποτα.
        if (userRepo.findByUsername(username).isPresent()) {
            return;
        }
        User user = User.builder()
            .username(username)
            .email(username + "@ceid.upatras.gr")
            .fullName(fullName)
            .role(role)
            .sector("ΕΒ")
            .passwordHash(encoder.encode(password))
            .active(true)
            .createdAt(LocalDateTime.now())
            .build();
        userRepo.save(user);
        System.out.println(">>> UserSeeder: δημιουργήθηκε ο χρήστης '" + username + "'");
    }
}
