package gr.upatras.ceid.timetable.repository;

import gr.upatras.ceid.timetable.entity.RoomConstraint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RoomConstraintRepository extends JpaRepository<RoomConstraint, Long> {

    List<RoomConstraint> findByRoomId(Long roomId);

    @Modifying
    @Query("DELETE FROM RoomConstraint c WHERE c.room.id = :roomId")
    void deleteByRoomId(Long roomId);

    @Query("SELECT c FROM RoomConstraint c JOIN FETCH c.room")
    List<RoomConstraint> findAllWithRoom();
}