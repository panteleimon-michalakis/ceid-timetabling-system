package gr.upatras.ceid.timetable.validation;

import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.CourseTeacher;
import gr.upatras.ceid.timetable.entity.TimeSlot;
import gr.upatras.ceid.timetable.entity.TimetableAssignment;
import gr.upatras.ceid.timetable.repository.CourseTeacherRepository;
import gr.upatras.ceid.timetable.repository.TimetableAssignmentRepository;
import gr.upatras.ceid.timetable.solver.ConstraintCodeMapping;
import gr.upatras.ceid.timetable.solver.HardViolation;
import gr.upatras.ceid.timetable.solver.SolverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Παράγει τα HARD validation issues ενός προγράμματος ΕΞ ΟΛΟΚΛΗΡΟΥ από τη μηχανή
 * (Φ-SV1 engine → {@link HardViolationTranslator}). Από τη Φάση 2b-ii-β2 είναι η ΜΟΝΑΔΙΚΗ
 * πηγή των solver-εκφράσιμων hard errors του {@code validateTimetableReport} (το integrity
 * layer + completeness παραμένουν χειρόγραφα εκεί). READ-ONLY.
 */
@Service
public class ValidationEngineService {

    private static final Logger log = LoggerFactory.getLogger(ValidationEngineService.class);

    private final SolverService solverService;
    private final TimetableAssignmentRepository assignmentRepo;
    private final CourseTeacherRepository courseTeacherRepo;

    public ValidationEngineService(SolverService solverService,
                                   TimetableAssignmentRepository assignmentRepo,
                                   CourseTeacherRepository courseTeacherRepo) {
        this.solverService = solverService;
        this.assignmentRepo = assignmentRepo;
        this.courseTeacherRepo = courseTeacherRepo;
    }

    /**
     * Παράγει τα HARD validation issues ενός προγράμματος αποκλειστικά από το
     * score-explanation. Κάθε issue: {type:"ERROR", code, referenceId, assignmentIds,
     * message}. READ-ONLY.
     */
    public List<Map<String, Object>> analyzeHardIssues(Long timetableId) {
        List<HardViolation> violations = solverService.analyzeHardViolations(timetableId);

        // Fail-loud: μη-χαρτογραφημένο hard constraint (χωρίς report code) -> ορατό στα logs.
        // Ο translator συνεχίζει να το κάνει skip· εδώ απλώς δεν εξαφανίζεται σιωπηλά (π.χ.
        // μελλοντικός DB-driven περιορισμός χωρίς entry στο ConstraintCodeMapping).
        for (HardViolation v : violations) {
            if (ConstraintCodeMapping.codeFor(v.constraintName()).isEmpty()) {
                log.warn("Unmapped hard constraint (no report code) — excluded from validation: {}",
                        v.constraintName());
            }
        }

        // courseId -> display ονόματα ΕΝΕΡΓΩΝ διδασκόντων (από το authoritative M2M).
        // Join-fetched ώστε να μην εξαρτάται από OSIV/transaction (S2 pattern)· ίδιο
        // active-φίλτρο με τον engine (buildTeacherKeyMap) ώστε τα display ονόματα να
        // αντιστοιχούν στο σύνολο που λαμβάνει υπόψη η σύγκρουση.
        Map<Long, List<String>> teacherNamesByCourse = new HashMap<>();
        for (CourseTeacher ct : courseTeacherRepo.findAllWithTeacherAndCourse()) {
            if (ct.getCourse() == null || ct.getTeacher() == null) continue;
            if (!Boolean.TRUE.equals(ct.getTeacher().getActive())) continue;
            String name = ct.getTeacher().getName();
            if (name == null || name.isBlank()) continue;
            teacherNamesByCourse
                    .computeIfAbsent(ct.getCourse().getId(), k -> new ArrayList<>())
                    .add(name);
        }

        // AssignmentView lookup από τα assignments του προγράμματος (LIVE course/room/
        // teachers = Option L: ίδια δεδομένα με τον σημερινό report -> μηδέν immutability
        // regression). Assignments με ελλιπή στοιχεία τα πιάνει αλλού το integrity layer.
        Map<Long, AssignmentView> views = new HashMap<>();
        for (TimetableAssignment a : assignmentRepo.findByTimetableId(timetableId)) {
            if (a.getCourse() == null || a.getRoom() == null || a.getTimeSlot() == null
                    || a.getAssignmentType() == null) continue;
            views.put(a.getId(), toView(a, teacherNamesByCourse));
        }

        // Translate (constraint-agnostic) -> issues, + type:"ERROR".
        List<Map<String, Object>> issues = HardViolationTranslator.translate(violations, views::get);
        for (Map<String, Object> issue : issues) {
            issue.put("type", "ERROR");
        }
        return issues;
    }

    /**
     * Adapter TimetableAssignment -> AssignmentView. BL-11: SNAPSHOT-FIRST (snapshot
     * non-null κερδίζει, αλλιώς live fallback) στα name/studyYear/room/day/hour ώστε τα
     * μηνύματα των hard issues παγωμένων προγραμμάτων να μένουν αμετάβλητα σε μελλοντικά
     * edits. Τα teacher names ΕΞΑΙΡΟΥΝΤΑΙ (μένουν live M2M) — BL-8 carve-out: το
     * snapshot_teachers_text είναι corrupted (6.4% drift) μέχρι να φτιαχτεί το BL-8.
     */
    private AssignmentView toView(TimetableAssignment a, Map<Long, List<String>> teacherNamesByCourse) {
        Course c = a.getCourse();
        TimeSlot ts = a.getTimeSlot();

        String courseName = a.getSnapshotCourseName() != null ? a.getSnapshotCourseName() : c.getName();
        int studyYear = a.getSnapshotStudyYear() != null ? a.getSnapshotStudyYear()
                : (c.getStudyYear() != null ? c.getStudyYear() : 0);
        String roomCode = a.getSnapshotRoomCode() != null ? a.getSnapshotRoomCode() : a.getRoom().getCode();
        String dayName = a.getSnapshotDayOfWeek() != null ? a.getSnapshotDayOfWeek()
                : (ts.getDayOfWeek() != null ? ts.getDayOfWeek().name() : null);
        Integer startHour = a.getSnapshotStartTime() != null ? a.getSnapshotStartTime().getHour()
                : (ts.getStartTime() != null ? ts.getStartTime().getHour() : null);

        // BL-11: teacher names ΜΕΝΟΥΝ live μέχρι το BL-8 (snapshot_teachers_text corrupted →
        // 6.4% drift)· τα υπόλοιπα πεδία είναι snapshot-first (immutable).
        List<String> teacherNames = teacherNamesByCourse.getOrDefault(c.getId(), List.of());

        return new AssignmentView(
                a.getId(),
                courseName,
                studyYear,
                roomCode,
                dayName,
                startHour,
                teacherNames,
                a.getAssignmentType().name());
    }
}
