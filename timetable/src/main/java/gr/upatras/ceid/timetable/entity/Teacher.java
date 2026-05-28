package gr.upatras.ceid.timetable.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "teachers",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"short_name"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Πλήρες ονοματεπώνυμο διδάσκοντα.
     * Π.χ. "Ι. Παπαδόπουλος" ή "Παπαδόπουλος Ιωάννης".
     */
    @Column(nullable = false)
    private String name;

    /*
     * Σύντομο μοναδικό αναγνωριστικό.
     * Χρήσιμο για εσωτερική αναφορά, φίλτρα, future UI, solver.
     * Π.χ. "PAPADOPOULOS_I".
     */
    @Column(name = "short_name", unique = true)
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(name = "teacher_type")
    private TeacherType teacherType;

    private String email;

    private String department;

    private String notes;

    public enum TeacherType {
        PROFESSOR,
        ASSOCIATE_PROFESSOR,
        ASSISTANT_PROFESSOR,
        LECTURER,
        EDIP,
        ETEP,
        EXTERNAL,
        APPOINTED
    }
}