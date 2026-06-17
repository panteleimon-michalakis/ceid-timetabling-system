package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.Room;
import gr.upatras.ceid.timetable.entity.RoomConstraint;
import gr.upatras.ceid.timetable.repository.RoomConstraintRepository;
import gr.upatras.ceid.timetable.repository.RoomRepository;
import gr.upatras.ceid.timetable.repository.TimetableAssignmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRepository roomRepo;
    private final RoomConstraintRepository constraintRepo;
    private final TimetableAssignmentRepository assignmentRepo;

    public RoomController(RoomRepository roomRepo, RoomConstraintRepository constraintRepo,
                          TimetableAssignmentRepository assignmentRepo) {
        this.roomRepo = roomRepo;
        this.constraintRepo = constraintRepo;
        this.assignmentRepo = assignmentRepo;
    }

    /** Δεσμευμένες ώρες αίθουσας. */
    @GetMapping("/{id}/constraints")
    public ResponseEntity<?> getConstraints(@PathVariable Long id) {
        if (!roomRepo.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(constraintRepo.findByRoomId(id).stream()
                .map(c -> Map.of(
                        "id", c.getId(),
                        "dayOfWeek", c.getDayOfWeek(),
                        "hour", c.getHour(),
                        "constraintType", c.getConstraintType().name()))
                .toList());
    }

    /** Αντικατάσταση δεσμευμένων ωρών αίθουσας (μόνο ADMIN — SecurityConfig). */
    @PutMapping("/{id}/constraints")
    @Transactional
    public ResponseEntity<?> updateConstraints(@PathVariable Long id,
                                               @RequestBody List<Map<String, Object>> body) {
        Room room = roomRepo.findById(id).orElse(null);
        if (room == null) return ResponseEntity.notFound().build();

        constraintRepo.deleteByRoomId(id);

        List<RoomConstraint> toSave = new ArrayList<>();
        for (Map<String, Object> item : body) {
            String day = String.valueOf(item.get("dayOfWeek"));
            Object hourValue = item.get("hour");
            if (day == null || day.isBlank() || !(hourValue instanceof Number n)) continue;
            toSave.add(RoomConstraint.builder()
                    .room(room)
                    .dayOfWeek(day)
                    .hour(n.intValue())
                    .constraintType(RoomConstraint.ConstraintType.BLOCKED)
                    .build());
        }
        constraintRepo.saveAll(toSave);
        return ResponseEntity.ok(Map.of("saved", toSave.size()));
    }

    /**
     * Σταθερή σειρά: τύπος αίθουσας κατά enum ordinal
     * (AMPHITHEATER → CLASSROOM → LAB → MEETING_ROOM → EXAM_HALL), μετά όνομα.
     * Ταξινόμηση in-memory επειδή το roomType αποθηκεύεται ως EnumType.STRING
     * (η DB δεν εγγυάται ordinal σειρά).
     */
    @GetMapping
    public List<Room> getAll() {
        return roomRepo.findAll().stream()
                .sorted(Comparator
                        .comparingInt((Room r) -> r.getRoomType() == null
                                ? Integer.MAX_VALUE : r.getRoomType().ordinal())
                        .thenComparing(Room::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Room> getById(@PathVariable Long id) {
        return roomRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<Room> getByCode(@PathVariable String code) {
        return roomRepo.findByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/type/{type}")
    public List<Room> getByType(@PathVariable Room.RoomType type) {
        return roomRepo.findByRoomType(type);
    }

    @PostMapping
    public Room create(@RequestBody Room room) {
        return roomRepo.save(room);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Room> update(@PathVariable Long id, @RequestBody Room updated) {
        return roomRepo.findById(id).map(room -> {
            room.setName(updated.getName());
            room.setCode(updated.getCode());
            room.setCapacity(updated.getCapacity());
            room.setRoomType(updated.getRoomType());
            room.setHasProjector(updated.getHasProjector());
            room.setHasComputers(updated.getHasComputers());
            room.setAvailableForExams(updated.getAvailableForExams());
            room.setAvailableForSemester(updated.getAvailableForSemester());
            room.setNotes(updated.getNotes());
            return ResponseEntity.ok(roomRepo.save(room));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!roomRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        // Pre-check ΧΩΡΙΣ καμία εγγραφή: αν η αίθουσα χρησιμοποιείται σε αναθέσεις,
        // επιστρέφουμε 409 Conflict και ΔΕΝ σβήνουμε τίποτα (ούτε τα constraints).
        if (assignmentRepo.existsByRoomId(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error",
                "Η αίθουσα χρησιμοποιείται σε αναθέσεις προγραμμάτων και δεν μπορεί "
                + "να διαγραφεί. Αφαίρεσε πρώτα τις αναθέσεις (ή τα παλιά προγράμματα) "
                + "ή απενεργοποίησέ τη για εξεταστική/εξαμηνιαίο."));
        }
        // Ελεύθερη αίθουσα: constraints + room σβήνονται ατομικά μέσα στο ίδιο
        // transaction. Τυχόν exception ΔΕΝ καταπνίγεται -> καθαρό rollback
        // (καμία μερική εγγραφή, κανένα UnexpectedRollbackException).
        constraintRepo.deleteByRoomId(id);
        roomRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}