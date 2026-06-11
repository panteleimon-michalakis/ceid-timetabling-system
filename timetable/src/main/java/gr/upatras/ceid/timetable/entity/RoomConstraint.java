package gr.upatras.ceid.timetable.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Δεσμευμένες ώρες αίθουσας (π.χ. χρήση από άλλο τμήμα).
 * Κανόνας τμήματος (εξεταστική #5): κάποιες αίθουσες έχουν ώρες
 * που δεν μπορούν να χρησιμοποιηθούν. Ισχύει και για εβδομαδιαία
 * προγράμματα και για εξεταστική (εβδομαδιαίο μοτίβο ανά ημέρα/ώρα).
 */
@Entity
@Table(
    name = "room_constraints",
    uniqueConstraints = @UniqueConstraint(columnNames = {"room_id","day_of_week","hour"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RoomConstraint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "day_of_week", nullable = false)
    private String dayOfWeek;   // "MONDAY" … "FRIDAY"

    @Column(nullable = false)
    private int hour;           // 9–20

    @Enumerated(EnumType.STRING)
    @Column(name = "constraint_type", nullable = false)
    private ConstraintType constraintType;

    public enum ConstraintType { BLOCKED }
}