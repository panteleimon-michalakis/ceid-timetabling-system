package gr.upatras.ceid.timetable.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "timetable_assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Σε ποιο πρόγραμμα ανήκει αυτή η ανάθεση
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timetable_id", nullable = false)
    private Timetable timetable;

    // Ποιο μάθημα
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    // Σε ποια αίθουσα
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // Σε ποια ώρα
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "time_slot_id", nullable = false)
    private TimeSlot timeSlot;

    // Τύπος: θεωρία, φροντιστήριο, εργαστήριο, εξέταση
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false)
    private AssignmentType assignmentType;

    // Κλειδωμένο; (ο solver δεν το αλλάζει)
    @Column(name = "is_locked")
    private Boolean isLocked;

    // Τοποθετήθηκε χειροκίνητα; (drag-and-drop)
    @Column(name = "manually_assigned")
    private Boolean manuallyAssigned;

    @Column(name = "exam_duration_minutes")
    private Integer examDurationMinutes;

    // Πότε δημιουργήθηκε
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ===== Snapshot-on-write (denormalized display πεδία) =====
    // Αντιγράφονται κατά την εγγραφή/τοποθέτηση από τα live Course/Room/TimeSlot
    // (single source: stampSnapshot). Τα προγράμματα render-άρονται από αυτά →
    // μένουν ανέπαφα σε μετέπειτα αλλαγές/soft-delete των master δεδομένων
    // (Architecture invariant #1). Όλα nullable· τα παλιά rows τα γεμίζει ο
    // SnapshotBackfillRunner. Enum-name πεδία αποθηκεύονται ως String (.name())
    // ώστε το snapshot να μένει ανεξάρτητο από μελλοντικές αλλαγές των enums.

    // Course
    @Column(name = "snapshot_course_code", length = 30)
    private String snapshotCourseCode;

    @Column(name = "snapshot_course_name", length = 200)
    private String snapshotCourseName;

    @Column(name = "snapshot_semester")
    private Integer snapshotSemester;

    @Column(name = "snapshot_study_year")
    private Integer snapshotStudyYear;

    @Column(name = "snapshot_course_type", length = 30)
    private String snapshotCourseType;

    @Column(name = "snapshot_teachers_text", columnDefinition = "TEXT")
    private String snapshotTeachersText;

    // Room
    @Column(name = "snapshot_room_code", length = 20)
    private String snapshotRoomCode;

    @Column(name = "snapshot_room_name", length = 100)
    private String snapshotRoomName;

    @Column(name = "snapshot_room_capacity")
    private Integer snapshotRoomCapacity;

    @Column(name = "snapshot_room_type", length = 30)
    private String snapshotRoomType;

    // TimeSlot
    @Column(name = "snapshot_day_of_week", length = 15)
    private String snapshotDayOfWeek;

    @Column(name = "snapshot_start_time")
    private LocalTime snapshotStartTime;

    @Column(name = "snapshot_end_time")
    private LocalTime snapshotEndTime;

    @Column(name = "snapshot_slot_type", length = 20)
    private String snapshotSlotType;

    @Column(name = "snapshot_specific_date")
    private LocalDate snapshotSpecificDate;

    @Column(name = "snapshot_exam_period_label", length = 50)
    private String snapshotExamPeriodLabel;

    public enum AssignmentType {
        LECTURE,    // Θεωρία
        TUTORIAL,   // Φροντιστήριο
        LAB,        // Εργαστήριο
        EXAM        // Εξέταση
    }
}