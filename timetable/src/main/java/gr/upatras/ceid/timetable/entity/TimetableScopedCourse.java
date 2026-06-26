package gr.upatras.ceid.timetable.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * #5 — παγωμένο σύνολο μαθημάτων που ΑΝΗΚΟΥΝ σε ένα πρόγραμμα, όπως ήταν τη
 * στιγμή της δημιουργίας του. Το completeness (validate/progress) διαβάζει
 * ΑΥΤΟ, όχι τον live κατάλογο (immutability invariant #1).
 *
 * course_id: original id ΧΩΡΙΣ FK επίτηδες (επιβιώνει hard delete μαθήματος).
 */
@Entity
@Table(name = "timetable_scoped_courses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableScopedCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timetable_id", nullable = false)
    private Timetable timetable;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

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

    @Column(name = "req_lecture_hours", nullable = false)
    private Integer reqLectureHours;

    @Column(name = "req_tutorial_hours", nullable = false)
    private Integer reqTutorialHours;

    @Column(name = "req_lab_hours", nullable = false)
    private Integer reqLabHours;

    @Column(name = "needs_exam", nullable = false)
    private Boolean needsExam;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
