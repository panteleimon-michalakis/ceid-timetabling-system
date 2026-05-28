package gr.upatras.ceid.timetable.repository;

import gr.upatras.ceid.timetable.entity.TimetableAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TimetableAssignmentRepository extends JpaRepository<TimetableAssignment, Long> {
    @Query("SELECT a FROM TimetableAssignment a " +
           "JOIN FETCH a.course " +
           "JOIN FETCH a.room " +
           "JOIN FETCH a.timeSlot " +
           "WHERE a.timetable.id = :timetableId")
    List<TimetableAssignment> findByTimetableId(@Param("timetableId") Long timetableId);
    List<TimetableAssignment> findByCourseId(Long courseId);
    List<TimetableAssignment> findByRoomId(Long roomId);
    List<TimetableAssignment> findByTimeSlotId(Long timeSlotId);
    List<TimetableAssignment> findByTimetableIdAndAssignmentType(Long timetableId, TimetableAssignment.AssignmentType type);
    List<TimetableAssignment> findByIsLockedTrue();
}