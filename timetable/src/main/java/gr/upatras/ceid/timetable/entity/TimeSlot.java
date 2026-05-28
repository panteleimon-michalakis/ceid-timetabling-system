package gr.upatras.ceid.timetable.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "time_slots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ημέρα εβδομάδας
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    // Ώρα έναρξης (π.χ. 09:00)
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    // Ώρα λήξης (π.χ. 10:00)
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    // Τύπος: εξαμήνου ή εξεταστικής
    @Enumerated(EnumType.STRING)
    @Column(name = "slot_type", nullable = false)
    private SlotType slotType;

    // Ετικέτα περιόδου εξεταστικής (π.χ. "01-05/09", "08-12/09")
    // NULL για slots εξαμήνου
    @Column(name = "exam_period_label", length = 50)
    private String examPeriodLabel;

    // Ημερομηνία (μόνο για εξεταστική — συγκεκριμένη ημέρα)
    // NULL για slots εξαμήνου (που επαναλαμβάνονται κάθε εβδομάδα)
    @Column(name = "specific_date")
    private java.time.LocalDate specificDate;

    public enum SlotType {
        SEMESTER,   // Εξαμήνου — επαναλαμβάνεται κάθε εβδομάδα
        EXAM        // Εξεταστικής — συγκεκριμένη ημερομηνία
    }
}