package gr.upatras.ceid.timetable.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import gr.upatras.ceid.timetable.entity.Timetable;
import gr.upatras.ceid.timetable.entity.TimetableAssignment;
import gr.upatras.ceid.timetable.repository.CourseRepository;
import gr.upatras.ceid.timetable.repository.RoomRepository;
import gr.upatras.ceid.timetable.repository.TimeSlotRepository;
import gr.upatras.ceid.timetable.repository.TimetableAssignmentRepository;
import gr.upatras.ceid.timetable.repository.TimetableRepository;
import gr.upatras.ceid.timetable.service.AssignmentSnapshotStamper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S3c (BL-1): ατομική εγγραφή της λύσης του solver.
 *
 * <p>Ξεχωριστό bean από τον {@link SolverService} ώστε η {@link #persist} να είναι
 * ENTRY point σε injected proxy — το {@code @Transactional} ΔΕΝ ενεργοποιείται σε
 * private self-invocation (το παλιό {@code saveSolution} ήταν private + same-class).
 * Έτσι το ~30s {@code solver.solve(...)} μένει ΕΚΤΟΣ tx (στον SolverService), ενώ
 * η εγγραφή ({@code deleteAll} + per-lesson {@code save} + {@code setStatus(SOLVED)})
 * τρέχει σε ΕΝΑ transaction: exception στη μέση → πλήρες rollback, καμία μερική
 * εγγραφή (παλιές αναθέσεις σβησμένες + μερικές νέες).
 *
 * <p>Snapshot-on-write: {@code snapshotStamper.stamp(a)} ακριβώς πριν από κάθε
 * {@code save} — ο 4ος (solver) write-path, συμμετρικά με place/move/auto-schedule.
 */
@Service
public class SolutionPersistenceService {

    private final CourseRepository courseRepo;
    private final RoomRepository roomRepo;
    private final TimeSlotRepository timeSlotRepo;
    private final TimetableRepository timetableRepo;
    private final TimetableAssignmentRepository assignmentRepo;
    private final AssignmentSnapshotStamper snapshotStamper;

    public SolutionPersistenceService(CourseRepository courseRepo, RoomRepository roomRepo,
                                      TimeSlotRepository timeSlotRepo, TimetableRepository timetableRepo,
                                      TimetableAssignmentRepository assignmentRepo,
                                      AssignmentSnapshotStamper snapshotStamper) {
        this.courseRepo = courseRepo;
        this.roomRepo = roomRepo;
        this.timeSlotRepo = timeSlotRepo;
        this.timetableRepo = timetableRepo;
        this.assignmentRepo = assignmentRepo;
        this.snapshotStamper = snapshotStamper;
    }

    @Transactional
    public Map<String, Object> persist(Timetable timetable, CeidTimetable solution, long elapsedMs) {
        // Delete existing auto-placed assignments
        List<TimetableAssignment> existing = assignmentRepo.findByTimetableId(timetable.getId());
        List<TimetableAssignment> toDelete = existing.stream()
        .filter(a -> !Boolean.TRUE.equals(a.getManuallyAssigned()))
        .filter(a -> !Boolean.TRUE.equals(a.getIsLocked()))
        .toList();
        assignmentRepo.deleteAll(toDelete);

        int placed = 0;
        int hardViolations = 0;
        List<String> log = new ArrayList<>();

        HardSoftScore score = solution.getScore();

        for (Lesson lesson : solution.getLessons()) {
            if (lesson.getTimeSlot() == null || lesson.getRoom() == null) {
                log.add("SKIP: " + lesson.getCourseCode() + " " + lesson.getAssignmentType() + " - no slot/room");
                continue;
            }

            TimetableAssignment assignment = TimetableAssignment.builder()
                    .timetable(timetable)
                    .course(courseRepo.findById(lesson.getCourseId()).orElse(null))
                    .room(roomRepo.findById(lesson.getRoom().getId()).orElse(null))
                    .timeSlot(timeSlotRepo.findById(lesson.getTimeSlot().getId()).orElse(null))
                    .assignmentType(TimetableAssignment.AssignmentType.valueOf(lesson.getAssignmentType()))
                    .isLocked(false)
                    .manuallyAssigned(false)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            snapshotStamper.stamp(assignment);
            assignmentRepo.save(assignment);
            placed++;
            log.add(lesson.getCourseCode() + " " + lesson.getAssignmentType()
                    + " -> " + lesson.getTimeSlot() + " " + lesson.getRoom().getCode());
        }

// Μετά την επίλυση το πρόγραμμα είναι SOLVED. Δεν αλλάζει σε PUBLISHED
// εδώ — αυτό γίνεται ρητά από τον ADMIN μέσω του publish workflow.
timetable.setStatus(Timetable.Status.SOLVED);
timetableRepo.save(timetable);

        Map<String, Object> result = new LinkedHashMap<>();
        int hardScore = score != null ? score.hardScore() : 0;

	result.put("status", hardScore < 0 ? "SOLVED_WITH_HARD_CONFLICTS" : "SOLVED");
        result.put("totalLessons", solution.getLessons().size());
        result.put("totalPlaced", placed);
        result.put("hardScore", hardScore);
        result.put("softScore", score != null ? score.softScore() : 0);
        result.put("solveTimeMs", elapsedMs);
        result.put("deletedPrevious", toDelete.size());
	result.put("timetableUpdated", true);
	result.put("timetableStatus", timetable.getStatus() != null ? timetable.getStatus().name() : null);
        result.put("log", log);

        return result;
    }
}
