package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.Room;
import gr.upatras.ceid.timetable.entity.RoomConstraint;
import gr.upatras.ceid.timetable.repository.RoomConstraintRepository;
import gr.upatras.ceid.timetable.repository.RoomRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRepository roomRepo;
    private final RoomConstraintRepository constraintRepo;

    public RoomController(RoomRepository roomRepo, RoomConstraintRepository constraintRepo) {
        this.roomRepo = roomRepo;
        this.constraintRepo = constraintRepo;
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

    @GetMapping
    public List<Room> getAll() {
        return roomRepo.findAll();
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
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!roomRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            constraintRepo.deleteByRoomId(id);
            roomRepo.deleteById(id);
            roomRepo.flush();
            return ResponseEntity.noContent().build();
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "Η αίθουσα χρησιμοποιείται σε αναθέσεις προγραμμάτων και δεν μπορεί "
                + "να διαγραφεί. Αφαίρεσε πρώτα τις αναθέσεις (ή τα παλιά προγράμματα) "
                + "ή απενεργοποίησέ τη για εξεταστική/εξαμηνιαίο."));
        }
    }
}