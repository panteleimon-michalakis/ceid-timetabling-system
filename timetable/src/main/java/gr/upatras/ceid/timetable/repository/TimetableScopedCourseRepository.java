package gr.upatras.ceid.timetable.repository;

import gr.upatras.ceid.timetable.entity.TimetableScopedCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TimetableScopedCourseRepository
        extends JpaRepository<TimetableScopedCourse, Long> {

    List<TimetableScopedCourse> findByTimetableId(Long timetableId);

    boolean existsByTimetableId(Long timetableId);
}
