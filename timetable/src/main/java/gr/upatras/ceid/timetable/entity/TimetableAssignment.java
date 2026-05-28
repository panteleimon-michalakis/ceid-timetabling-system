package gr.upatras.ceid.timetable.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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

    public enum AssignmentType {
        LECTURE,    // Θεωρία
        TUTORIAL,   // Φροντιστήριο
        LAB,        // Εργαστήριο
        EXAM        // Εξέταση
    }
}