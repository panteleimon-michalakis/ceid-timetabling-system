package gr.upatras.ceid.timetable.service;

import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.Timetable;
import gr.upatras.ceid.timetable.entity.TimetableScopedCourse;
import gr.upatras.ceid.timetable.repository.CourseRepository;
import gr.upatras.ceid.timetable.repository.TimetableScopedCourseRepository;
import gr.upatras.ceid.timetable.util.CourseRelevance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * #5 — υλοποιεί («παγώνει») το σύνολο μαθημάτων ενός προγράμματος τη στιγμή της
 * δημιουργίας του. Single source: το καλούν και το create() (live) και ο
 * TimetableScopeBackfillRunner (υπάρχοντα). Freeze-once / idempotent ανά
 * πρόγραμμα μέσω existsByTimetableId.
 */
@Service
public class TimetableScopeService {

    private final CourseRepository courseRepo;
    private final TimetableScopedCourseRepository scopedCourseRepo;

    public TimetableScopeService(CourseRepository courseRepo,
                                 TimetableScopedCourseRepository scopedCourseRepo) {
        this.courseRepo = courseRepo;
        this.scopedCourseRepo = scopedCourseRepo;
    }

    /**
     * Υλοποιεί το scope ΜΟΝΟ αν δεν υπάρχει ήδη (freeze-once).
     * @return πλήθος γραμμών που γράφτηκαν (0 αν υπήρχε ήδη).
     */
    @Transactional
    public int materializeScopeIfAbsent(Timetable timetable) {
        if (timetable == null || timetable.getId() == null) return 0;
        if (scopedCourseRepo.existsByTimetableId(timetable.getId())) return 0;

        List<TimetableScopedCourse> rows = courseRepo.findAll().stream()
                .filter(c -> CourseRelevance.isRelevant(c, timetable))
                .map(c -> toScopedCourse(timetable, c))
                .toList();

        scopedCourseRepo.saveAll(rows);
        return rows.size();
    }

    private TimetableScopedCourse toScopedCourse(Timetable timetable, Course c) {
        return TimetableScopedCourse.builder()
                .timetable(timetable)
                .courseId(c.getId())
                .snapshotCourseCode(c.getCode())
                .snapshotCourseName(c.getName())
                .snapshotSemester(c.getSemester())
                .snapshotStudyYear(c.getStudyYear())
                .snapshotCourseType(c.getCourseType() != null ? c.getCourseType().name() : null)
                .reqLectureHours(c.getLectureHours() != null ? c.getLectureHours() : 0)
                .reqTutorialHours(c.getTutorialHours() != null ? c.getTutorialHours() : 0)
                .reqLabHours(c.getLabHours() != null ? c.getLabHours() : 0)
                .needsExam(Boolean.TRUE) // preserve προ-#5 συμπεριφορά: όλα τα relevant αναμένονται σε εξεταστική
                .createdAt(LocalDateTime.now())
                .build();
    }
}
