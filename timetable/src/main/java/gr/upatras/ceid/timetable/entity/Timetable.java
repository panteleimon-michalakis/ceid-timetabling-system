package gr.upatras.ceid.timetable.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // Εξαιρούμενες ημερομηνίες που ορίζει ο admin (custom) — δεν παράγονται exam
    // slots σε αυτές. Οι επίσημες ελληνικές αργίες εξαιρούνται ξεχωριστά, μέσω
    // GreekHolidays (δεν αποθηκεύονται εδώ).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "timetable_excluded_dates",
            joinColumns = @JoinColumn(name = "timetable_id"))
    @Column(name = "excluded_date")
    @Builder.Default
    private List<LocalDate> excludedDates = new ArrayList<>();

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