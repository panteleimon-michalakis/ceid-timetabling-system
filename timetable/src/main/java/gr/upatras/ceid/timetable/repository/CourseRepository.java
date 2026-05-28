package gr.upatras.ceid.timetable.repository;

import gr.upatras.ceid.timetable.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByCode(String code);
    List<Course> findBySemester(Integer semester);
    List<Course> findBySemesterAndActiveTrue(Integer semester);
    List<Course> findByCourseType(Course.CourseType courseType);
    List<Course> findBySemesterType(Course.SemesterType semesterType);
    List<Course> findByActiveTrue();
    List<Course> findByVisibleInTimetableTrue();
    List<Course> findByStudyYear(Integer studyYear);
}