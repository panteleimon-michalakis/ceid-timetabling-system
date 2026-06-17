package gr.upatras.ceid.timetable.repository;

import gr.upatras.ceid.timetable.entity.CourseTeacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseTeacherRepository extends JpaRepository<CourseTeacher, Long> {

    List<CourseTeacher> findByCourseId(Long courseId);

    List<CourseTeacher> findByTeacherId(Long teacherId);

    // S1: «σε χρήση» καθηγητής = διδάσκει ≥1 μάθημα → deactivate αντί hard-delete.
    boolean existsByTeacherId(Long teacherId);

    /*
     * S2: authoritative πηγή teacherKeys για τον solver. Join-fetch ώστε να
     * φορτώνονται teacher & course ΑΞΙΟΠΙΣΤΑ ΧΩΡΙΣ εξάρτηση από OSIV/transaction
     * (το solve() δεν είναι @Transactional & open-in-view=false). Πριν το S2 το
     * lazy access πετούσε LazyInitializationException → σιωπηλά swallowed → το
     * M2M loop ήταν de-facto dead code.
     */
    @Query("select ct from CourseTeacher ct join fetch ct.teacher join fetch ct.course")
    List<CourseTeacher> findAllWithTeacherAndCourse();

    boolean existsByCourseIdAndTeacherIdAndRole(
            Long courseId,
            Long teacherId,
            CourseTeacher.Role role
    );

    /*
     * Επιστρέφει τα ονόματα των κοινών διδασκόντων ανάμεσα σε δύο μαθήματα.
     *
     * Εξαιρεί generic placeholders (Εντεταλμένος Διδάσκων, ΑΑΔΕ, ΕΔΙΠ κλπ.)
     * με name-based φίλτρο αντί hardcoded IDs — portable σε οποιαδήποτε βάση.
     */
    @Query("""
        select t.name
        from CourseTeacher ct
        join ct.teacher t
        where ct.course.id = :courseId
          and t.id in (
              select ct2.teacher.id
              from CourseTeacher ct2
              where ct2.course.id = :otherCourseId
          )
          and lower(t.name) not like '%εντεταλμ%'
          and lower(t.name) not like '%εδιπ%'
          and lower(t.name) not like '%ε.δι.π%'
          and lower(t.name) not like '%aaδε%'
          and lower(t.name) not like '%ααδε%'
          and lower(t.name) not like '%entetal%'
          and lower(t.name) not like '%staff%'
          and lower(t.name) not like '%tbd%'
        """)
    List<String> findCommonTeacherNamesBetweenCourses(
            @Param("courseId") Long courseId,
            @Param("otherCourseId") Long otherCourseId
    );
}