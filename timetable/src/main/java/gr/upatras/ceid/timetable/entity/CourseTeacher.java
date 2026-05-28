package gr.upatras.ceid.timetable.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "course_teachers",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"course_id", "teacher_id", "role"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseTeacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Το μάθημα στο οποίο συμμετέχει ο διδάσκων.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /*
     * Ο διδάσκων που συνδέεται με το μάθημα.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    /*
     * Ρόλος διδάσκοντα στο συγκεκριμένο μάθημα.
     * Το κάνουμε nullable = false ώστε να λειτουργεί σωστά και το unique constraint.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.PRIMARY;

    public enum Role {
        PRIMARY,
        SECONDARY,
        LAB_INSTRUCTOR,
        TUTORIAL_INSTRUCTOR
    }
}