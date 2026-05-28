package gr.upatras.ceid.timetable.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "timetables")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Timetable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Όνομα προγράμματος (π.χ. "Χειμερινό 2025-26")
    @Column(nullable = false, length = 200)
    private String name;

    // Ακαδημαϊκό έτος (π.χ. "2025-26")
    @Column(name = "academic_year", nullable = false, length = 10)
    private String academicYear;

    // Τύπος: πρόγραμμα εξαμήνου ή εξεταστικής
    @Enumerated(EnumType.STRING)
    @Column(name = "timetable_type", nullable = false)
    private TimetableType timetableType;

    // Περίοδος εξαμήνου
    @Enumerated(EnumType.STRING)
    @Column(name = "semester_type", nullable = false)
    private SemesterType semesterType;

    // Ημερομηνίες (κυρίως για εξεταστική)
    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // Κατάσταση προγράμματος
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    // Στατιστικά solver
    @Column(name = "solver_score")
    private Double solverScore;

    @Column(name = "solver_conflicts")
    private Integer solverConflicts;

    @Column(name = "solver_time_seconds")
    private Integer solverTimeSeconds;

    // Ποιος το δημιούργησε
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    // Πότε δημοσιεύτηκε
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // Πότε δημιουργήθηκε
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Σημειώσεις
    @Column(columnDefinition = "TEXT")
    private String notes;

    // Enums
    public enum TimetableType {
        SEMESTER,       // Ωρολόγιο εξαμήνου
        EXAM            // Πρόγραμμα εξεταστικής
    }

    public enum SemesterType {
        FALL,           // Χειμερινό
        SPRING,         // Εαρινό
        SEPTEMBER       // Εξεταστική Σεπτεμβρίου
    }

    public enum Status {
        DRAFT,          // Πρόχειρο — υπό επεξεργασία
        SOLVING,        // Τρέχει ο solver
        SOLVED,         // Ο solver τελείωσε
        PUBLISHED       // Δημοσιευμένο — ορατό σε φοιτητές
    }
}