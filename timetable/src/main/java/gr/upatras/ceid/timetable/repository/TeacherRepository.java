package gr.upatras.ceid.timetable.repository;

import gr.upatras.ceid.timetable.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findByShortName(String shortName);

    Optional<Teacher> findByName(String name);

    boolean existsByShortName(String shortName);

    // S1 soft-delete: active-aware read — απενεργοποιημένοι καθηγητές δεν
    // επιστρέφονται στη λίστα διαχείρισης (συνεπές με RoomRepository.findByActiveTrue).
    List<Teacher> findByActiveTrue();
}