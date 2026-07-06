package gr.upatras.ceid.timetable.validation;

import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.CourseTeacher;
import gr.upatras.ceid.timetable.entity.TimeSlot;
import gr.upatras.ceid.timetable.entity.TimetableAssignment;
import gr.upatras.ceid.timetable.repository.CourseTeacherRepository;
import gr.upatras.ceid.timetable.repository.TimetableAssignmentRepository;
import gr.upatras.ceid.timetable.solver.ConstraintCodeMapping;
import gr.upatras.ceid.timetable.solver.HardViolation;
import gr.upatras.ceid.timetable.solver.SoftViolation;
import gr.upatras.ceid.timetable.solver.SolverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
     * Παράγει WARNING issues για τον SOFT περιορισμό {@code teacherPreferredSlot} ("πράσινο"
     * κελί καθηγητή): ΟΜΑΔΟΠΟΙΗΜΕΝΑ ΑΝΑ ΜΑΘΗΜΑ (με τα ονόματα διδασκόντων), μία γραμμή/μάθημα
     * με το πλήθος ωρών εκτός preferred. Shape ίδιο με τα υπόλοιπα warnings του report
     * (severity/code/message/referenceId). READ-ONLY — ΜΟΝΟ εμφάνιση, καμία αλλαγή σε solve/βάρη.
     *
     * Σημ.: για co-taught μάθημα εμφανίζονται όλοι οι ενεργοί διδάσκοντες (η attribution στον
     * συγκεκριμένο διδάσκοντα με τα preferred δεν προκύπτει από το match· για warning αρκεί).
     */
    public List<Map<String, Object>> analyzeTeacherPreferredWarnings(Long timetableId) {
        List<SoftViolation> violations = solverService.analyzeTeacherPreferredViolations(timetableId);
        if (violations.isEmpty()) return List.of();

        // assignmentId -> courseId + courseId -> displayName (snapshot-first, ίδιο με toView)
        Map<Long, Long> courseIdByAssignment = new HashMap<>();
        Map<Long, String> courseNameById = new HashMap<>();
        for (TimetableAssignment a : assignmentRepo.findByTimetableId(timetableId)) {
            if (a.getCourse() == null) continue;
            courseIdByAssignment.put(a.getId(), a.getCourse().getId());
            courseNameById.putIfAbsent(a.getCourse().getId(),
                    a.getSnapshotCourseName() != null ? a.getSnapshotCourseName() : a.getCourse().getName());
        }

        // courseId -> ονόματα ΕΝΕΡΓΩΝ διδασκόντων (ίδιο active-φίλτρο/πηγή με το analyzeHardIssues·
        // σκόπιμη μικρο-διπλασίαση ώστε να ΜΗΝ αγγιχτεί το hard path).
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

        // Ομαδοποίηση: courseId -> πλήθος ωρών εκτός preferred (LinkedHashMap = ντετερμινιστική σειρά)
        Map<Long, Integer> hoursByCourse = new LinkedHashMap<>();
        for (SoftViolation v : violations) {
            for (Long assignmentId : v.assignmentIds()) {
                Long courseId = courseIdByAssignment.get(assignmentId);
                if (courseId != null) hoursByCourse.merge(courseId, 1, Integer::sum);
            }
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : hoursByCourse.entrySet()) {
            Long courseId = e.getKey();
            int hours = e.getValue();
            String courseName = courseNameById.getOrDefault(courseId, "(άγνωστο μάθημα)");
            List<String> teachers = teacherNamesByCourse.getOrDefault(courseId, List.of());
            String who = teachers.isEmpty() ? "" : " (διδ. " + String.join(", ", teachers) + ")";

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("severity", "WARNING");
            w.put("code", "TEACHER_PREFERRED");
            w.put("message", "Το μάθημα " + courseName + who + " έχει " + hours
                    + (hours == 1 ? " ώρα" : " ώρες") + " εκτός των preferred ωρών του διδάσκοντα.");
            w.put("referenceId", courseId);
            warnings.add(w);
        }
        return warnings;
    }

    /**
     * Παράγει WARNING issues για την ΑΠΟΚΛΙΣΗ τοποθετημένης εξέτασης από τις δηλωμένες
     * προτιμήσεις αίθουσας/ώρας του μαθήματος ({@code preferredExamRooms/Hours}). Παράλληλο
     * μοτίβο με το {@link #analyzeTeacherPreferredWarnings}: ΟΜΑΔΟΠΟΙΗΣΗ ΑΝΑ ΜΑΘΗΜΑ, μία
     * γραμμή/μάθημα. ΣΥΜΠΛΗΡΩΜΑΤΙΚΟ του ★ (frontend examPreferenceMatch): το ★ εμφανίζεται iff
     * πλήρης τήρηση· το warning iff δηλωμένη προτίμηση ΚΑΙ απόκλιση σε αίθουσα ή/και ώρα.
     * Ίδιο CSV parsing με τον solver ({@link SolverService#parseCsvCodes}/{@code parseCsvHours},
     * single source of truth) + ίδια match λογική με τον {@code ExamConstraintProvider}
     * (room: {@code !preferredRoomCodes.contains(code)}, hour: {@code !preferredStartHours.contains(h)}).
     * SNAPSHOT-FIRST στα room/hour (ίδιο με {@link #toView}) → σταθερό σε παγωμένα προγράμματα.
     * READ-ONLY — ΜΟΝΟ εμφάνιση, καμία αλλαγή σε solve/βάρη.
     */
    public List<Map<String, Object>> analyzeExamPreferredWarnings(Long timetableId) {
        List<TimetableAssignment> assignments = assignmentRepo.findByTimetableId(timetableId);

        // courseId -> displayName (snapshot-first)
        Map<Long, String> courseNameById = new HashMap<>();
        for (TimetableAssignment a : assignments) {
            if (a.getCourse() == null) continue;
            courseNameById.putIfAbsent(a.getCourse().getId(),
                    a.getSnapshotCourseName() != null ? a.getSnapshotCourseName() : a.getCourse().getName());
        }

        // courseId -> ονόματα ΕΝΕΡΓΩΝ διδασκόντων (ίδια πηγή/φίλτρο με τα υπόλοιπα paths)
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

        // Ομαδοποίηση: courseId -> [roomDeviates, hourDeviates] (LinkedHashMap = ντετερμινιστική σειρά)
        Map<Long, boolean[]> devByCourse = new LinkedHashMap<>();
        for (TimetableAssignment a : assignments) {
            if (a.getAssignmentType() != TimetableAssignment.AssignmentType.EXAM) continue;
            Course c = a.getCourse();
            if (c == null) continue;

            java.util.Set<String> prefRooms = SolverService.parseCsvCodes(c.getPreferredExamRooms());
            java.util.Set<Integer> prefHours = SolverService.parseCsvHours(c.getPreferredExamHours());
            if (prefRooms.isEmpty() && prefHours.isEmpty()) continue; // καμία δηλωμένη προτίμηση

            String roomCode = a.getSnapshotRoomCode() != null ? a.getSnapshotRoomCode()
                    : (a.getRoom() != null ? a.getRoom().getCode() : null);
            Integer startHour = a.getSnapshotStartTime() != null ? a.getSnapshotStartTime().getHour()
                    : (a.getTimeSlot() != null && a.getTimeSlot().getStartTime() != null
                            ? a.getTimeSlot().getStartTime().getHour() : null);

            boolean roomDev = !prefRooms.isEmpty() && roomCode != null && !prefRooms.contains(roomCode);
            boolean hourDev = !prefHours.isEmpty() && startHour != null && !prefHours.contains(startHour);
            if (!roomDev && !hourDev) continue;

            boolean[] agg = devByCourse.computeIfAbsent(c.getId(), k -> new boolean[2]);
            agg[0] |= roomDev;
            agg[1] |= hourDev;
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Map.Entry<Long, boolean[]> e : devByCourse.entrySet()) {
            Long courseId = e.getKey();
            boolean roomDev = e.getValue()[0];
            boolean hourDev = e.getValue()[1];
            String courseName = courseNameById.getOrDefault(courseId, "(άγνωστο μάθημα)");
            List<String> teachers = teacherNamesByCourse.getOrDefault(courseId, List.of());
            String who = teachers.isEmpty() ? "" : " (διδ. " + String.join(", ", teachers) + ")";
            String what = roomDev && hourDev ? "αίθουσα και ώρα" : roomDev ? "αίθουσα" : "ώρα";

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("severity", "WARNING");
            w.put("code", "EXAM_PREFERRED");
            w.put("message", "Η εξέταση του μαθήματος " + courseName + who
                    + " τοποθετήθηκε σε " + what + " εκτός των προτιμώμενων της εξέτασης.");
            w.put("referenceId", courseId);
            warnings.add(w);
        }
        return warnings;
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
