package gr.upatras.ceid.timetable.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "courses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // π.χ. "CEID_22Y101", "CEID_NE4117"
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    // π.χ. "Διακριτά Μαθηματικά"
    @Column(nullable = false, length = 200)
    private String name;

    // Εξάμηνο: 1-10
    @Column(nullable = false)
    private Integer semester;

    // Έτος σπουδών: 1-5
    @Column(name = "study_year", nullable = false)
    private Integer studyYear;

    // Τύπος μαθήματος
    @Enumerated(EnumType.STRING)
    @Column(name = "course_type", nullable = false)
    private CourseType courseType;

    // Ώρες ανά εβδομάδα
    @Column(name = "lecture_hours")
    private Integer lectureHours;       // Θεωρία (Δ)

    @Column(name = "tutorial_hours")
    private Integer tutorialHours;      // Φροντιστήριο (Φ)

    @Column(name = "lab_hours")
    private Integer labHours;           // Εργαστήριο (Ε)

    // ECTS μονάδες
    private Integer ects;

    // Τομέας: ΕΘ, ΛΥ, ΥΑ
    @Column(length = 10)
    private String sector;

    // Εκτιμώμενος αριθμός φοιτητών
    @Column(name = "expected_students")
    private Integer expectedStudents;

    // Διάρκεια εξέτασης σε λεπτά
    @Column(name = "exam_duration_minutes")
    private Integer examDurationMinutes;

    // Χρειάζεται εργαστήριο Η/Υ;
    @Column(name = "needs_lab")
    private Boolean needsLab;

    // Χρειάζεται projector;
    @Column(name = "needs_projector")
    private Boolean needsProjector;

    // Εξάμηνο διδασκαλίας
    @Enumerated(EnumType.STRING)
    @Column(name = "semester_type", nullable = false)
    private SemesterType semesterType;

    // Διδάσκοντες (ονόματα ως text — η σχέση θα γίνει σε ξεχωριστό entity αργότερα)
    @Column(name = "teachers_text", length = 500)
    private String teachersText;

    // Ενεργό μάθημα; (false = δεν διδάσκεται φέτος)
    @Column(nullable = false)
    private Boolean active;

    // Εμφανίζεται στο πρόγραμμα; (false = "σε συνεννόηση")
    @Column(name = "visible_in_timetable", nullable = false)
    private Boolean visibleInTimetable;

    // #4 soft-delete: true = αποσυρμένο από τον ζωντανό κατάλογο (η γραμμή μένει
    // ώστε υπάρχοντα προγράμματα να το κρατούν ακέραιο μέσω snapshot + frozen scope).
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    // A6: Προτιμήσεις εξεταστικής από τις φόρμες διδασκόντων.
    // CSV κωδικών αιθουσών (π.χ. "Β,Δ1") και ωρών έναρξης (π.χ. "9,12").
    @Column(length = 200)
    private String preferredExamRooms;

    @Column(length = 50)
    private String preferredExamHours;

    // Σημειώσεις
    @Column(columnDefinition = "TEXT")
    private String notes;

    // Enums
    public enum CourseType {
        REQUIRED,           // Υποχρεωτικό (εξάμηνα 1-6)
        REQUIRED_ELECTIVE,  // Υποχρεωτικό κατ' επιλογήν (εξάμηνα 7+)
        GENERAL_EDUCATION,  // Γενικής Παιδείας (ΓΠ)
        EXTERNAL            // Από άλλο τμήμα / Erasmus
    }

    public enum SemesterType {
        FALL,       // Χειμερινό
        SPRING,     // Εαρινό
        BOTH        // Και τα δύο
    }
}