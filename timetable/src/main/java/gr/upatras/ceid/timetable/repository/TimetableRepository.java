package gr.upatras.ceid.timetable.repository;

import gr.upatras.ceid.timetable.entity.Timetable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {
    List<Timetable> findByAcademicYear(String academicYear);
    List<Timetable> findByStatus(Timetable.Status status);
    List<Timetable> findByTimetableType(Timetable.TimetableType timetableType);
    List<Timetable> findByAcademicYearAndSemesterType(String academicYear, Timetable.SemesterType semesterType);
}