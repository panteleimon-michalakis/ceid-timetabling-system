package gr.upatras.ceid.timetable.repository;

import gr.upatras.ceid.timetable.entity.TeacherConstraint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TeacherConstraintRepository extends JpaRepository<TeacherConstraint, Long> {

    List<TeacherConstraint> findByTeacherId(Long teacherId);

    @Modifying
    @Query("DELETE FROM TeacherConstraint c WHERE c.teacher.id = :teacherId")
    void deleteByTeacherId(Long teacherId);

    @Query("SELECT c FROM TeacherConstraint c JOIN FETCH c.teacher")
    List<TeacherConstraint> findAllWithTeacher();
}