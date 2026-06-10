package gr.upatras.ceid.timetable.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Username για login (π.χ. "tsichlas" ή AM φοιτητή)
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    // Κωδικός (hash) — NULL αν χρησιμοποιείται LDAP
    @Column(name = "password_hash")
    private String passwordHash;

    // Email (π.χ. "tsichlas@gmail.com")
    @Column(length = 100)
    private String email;

    // Πλήρες όνομα (π.χ. "Κωνσταντίνος Τσίχλας")
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    // Ρόλος στο σύστημα
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Τομέας (για καθηγητές): ΕΘ, ΛΥ, ΥΑ
    @Column(length = 10)
    private String sector;

    // Σύνδεση με εγγραφή Teacher (μόνο για χρήστες ρόλου TEACHER).
    // Επιτρέπει στον καθηγητή να επεξεργάζεται ΜΟΝΟ τον δικό του φάκελο.
    @Column(name = "teacher_id")
    private Long teacherId;

    // Ενεργός λογαριασμός;
    @Column(nullable = false)
    private Boolean active;

    // Πότε δημιουργήθηκε ο λογαριασμός
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Ρόλοι χρηστών
    public enum Role {
        ADMIN,      // Διαχειριστής — πλήρης πρόσβαση
        TEACHER,    // Καθηγητής — δημιουργεί/επεξεργάζεται πρόγραμμα
        STUDENT     // Φοιτητής — μόνο προβολή (view-only)
    }
}