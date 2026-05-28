package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.Room;
import gr.upatras.ceid.timetable.repository.RoomRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRepository roomRepo;

    public RoomController(RoomRepository roomRepo) {
        this.roomRepo = roomRepo;
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
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (roomRepo.existsById(id)) {
            roomRepo.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}