package gr.upatras.ceid.timetable.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "teacher_constraints",
    uniqueConstraints = @UniqueConstraint(columnNames = {"teacher_id","day_of_week","hour"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TeacherConstraint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(name = "day_of_week", nullable = false)
    private String dayOfWeek;   // "MONDAY" … "FRIDAY"

    @Column(nullable = false)
    private int hour;           // 9–20

    @Enumerated(EnumType.STRING)
    @Column(name = "constraint_type", nullable = false)
    private ConstraintType constraintType;

    public enum ConstraintType { BLOCKED, PREFERRED }
}