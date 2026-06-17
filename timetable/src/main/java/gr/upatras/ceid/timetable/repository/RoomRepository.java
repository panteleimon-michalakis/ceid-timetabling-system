package gr.upatras.ceid.timetable.repository;

import gr.upatras.ceid.timetable.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByCode(String code);
    List<Room> findByRoomType(Room.RoomType roomType);
    // S1: active-aware reads — απενεργοποιημένες αίθουσες δεν προσφέρονται
    // στον solver ούτε στις προτεινόμενες θέσεις.
    List<Room> findByActiveTrue();
    List<Room> findByAvailableForSemesterTrueAndActiveTrue();
    List<Room> findByAvailableForExamsTrueAndActiveTrue();
    List<Room> findByCapacityGreaterThanEqual(Integer capacity);
}