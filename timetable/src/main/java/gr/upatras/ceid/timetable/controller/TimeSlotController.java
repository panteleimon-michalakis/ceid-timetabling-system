package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.TimeSlot;
import gr.upatras.ceid.timetable.repository.TimeSlotRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/timeslots")
public class TimeSlotController {

    private final TimeSlotRepository timeSlotRepo;

    public TimeSlotController(TimeSlotRepository timeSlotRepo) {
        this.timeSlotRepo = timeSlotRepo;
    }

    @GetMapping
    public List<TimeSlot> getAll() {
        return timeSlotRepo.findAll();
    }

    @GetMapping("/type/{type}")
    public List<TimeSlot> getByType(@PathVariable TimeSlot.SlotType type) {
        return timeSlotRepo.findBySlotType(type);
    }
}