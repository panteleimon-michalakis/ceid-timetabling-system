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
     * Adapter TimetableAssignment -> AssignmentView (LIVE course/room/teachers = Option L).
     * Τα ονόματα διδασκόντων έρχονται από τον προ-υπολογισμένο χάρτη (M2M source).
     */
    private AssignmentView toView(TimetableAssignment a, Map<Long, List<String>> teacherNamesByCourse) {
        Course c = a.getCourse();
        TimeSlot ts = a.getTimeSlot();
        return new AssignmentView(
                a.getId(),
                c.getName(),
                c.getStudyYear() != null ? c.getStudyYear() : 0,
                a.getRoom().getCode(),
                ts.getDayOfWeek() != null ? ts.getDayOfWeek().name() : null,
                ts.getStartTime() != null ? ts.getStartTime().getHour() : null,
                teacherNamesByCourse.getOrDefault(c.getId(), List.of()),
                a.getAssignmentType().name());
    }
}
