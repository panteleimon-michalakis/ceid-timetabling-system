package gr.upatras.ceid.timetable.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // π.χ. "Αμφιθέατρο Γ"
    @Column(nullable = false, length = 100)
    private String name;

    // π.χ. "Γ", "Δ1", "ΥΚ"
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    // Χωρητικότητα σε θέσεις
    @Column(nullable = false)
    private Integer capacity;

    // Τύπος αίθουσας
    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false)
    private RoomType roomType;

    // Εξοπλισμός
    @Column(name = "has_projector")
    private Boolean hasProjector;

    @Column(name = "has_computers")
    private Boolean hasComputers;

    // Διαθέσιμη για εξεταστική;
    @Column(name = "available_for_exams")
    private Boolean availableForExams;

    // Διαθέσιμη για εξάμηνο;
    @Column(name = "available_for_semester")
    private Boolean availableForSemester;

    // Σημειώσεις (π.χ. "Μόνο 1ο έτος")
    @Column(columnDefinition = "TEXT")
    private String notes;

    // Soft-delete (S1): false = απενεργοποιημένη — δεν προσφέρεται στον solver
    // ούτε σε νέες αναθέσεις, αλλά η γραμμή & τα FK παλιών προγραμμάτων μένουν.
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    public enum RoomType {
        AMPHITHEATER,   // Αμφιθέατρο (Γ, Β)
        CLASSROOM,      // Αίθουσα (Δ1, Δ2, Ε1, Ε2)
        LAB,            // Εργαστήριο (ΥΚ)
        MEETING_ROOM,   // Αίθουσα Συνεδριάσεων
        EXAM_HALL        // ΑΦΕ — μόνο εξεταστική
    }
}