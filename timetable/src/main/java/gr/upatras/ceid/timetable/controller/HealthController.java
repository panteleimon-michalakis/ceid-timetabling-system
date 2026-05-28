package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.repository.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final RoomRepository roomRepo;
    private final CourseRepository courseRepo;
    private final UserRepository userRepo;

    public HealthController(RoomRepository roomRepo, CourseRepository courseRepo, UserRepository userRepo) {
        this.roomRepo = roomRepo;
        this.courseRepo = courseRepo;
        this.userRepo = userRepo;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "OK",
            "application", "CEID Timetable",
            "rooms", roomRepo.count(),
            "courses", courseRepo.count(),
            "users", userRepo.count()
        );
    }
}