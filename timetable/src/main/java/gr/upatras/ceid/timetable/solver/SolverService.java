package gr.upatras.ceid.timetable.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.ScoreExplanation;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatch;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import gr.upatras.ceid.timetable.entity.*;
import gr.upatras.ceid.timetable.entity.TeacherConstraint;
import gr.upatras.ceid.timetable.entity.RoomConstraint;
import gr.upatras.ceid.timetable.repository.RoomConstraintRepository;
import gr.upatras.ceid.timetable.repository.*;
import gr.upatras.ceid.timetable.util.CourseRelevance;
import gr.upatras.ceid.timetable.util.ExamDateRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;

@Service
public class SolverService {

    private final CourseRepository courseRepo;
    private final RoomRepository roomRepo;
    private final TimeSlotRepository timeSlotRepo;
    private final TimetableRepository timetableRepo;
    private final TimetableAssignmentRepository assignmentRepo;
    private final CourseTeacherRepository courseTeacherRepo;
    private final TeacherConstraintRepository constraintRepo;
    private final RoomConstraintRepository roomConstraintRepo;
    private final SolutionPersistenceService solutionPersistence;
    private final ConstraintWeightConfigRepository cwcRepo;

    public SolverService(CourseRepository courseRepo, RoomRepository roomRepo,
                         TimeSlotRepository timeSlotRepo, TimetableRepository timetableRepo,
                         TimetableAssignmentRepository assignmentRepo,
                         CourseTeacherRepository courseTeacherRepo,
                         TeacherConstraintRepository constraintRepo,
                         RoomConstraintRepository roomConstraintRepo,
                         SolutionPersistenceService solutionPersistence,
                         ConstraintWeightConfigRepository cwcRepo) {
        this.courseRepo = courseRepo;
        this.roomRepo = roomRepo;
        this.timeSlotRepo = timeSlotRepo;
        this.timetableRepo = timetableRepo;
        this.assignmentRepo = assignmentRepo;
        this.courseTeacherRepo = courseTeacherRepo;
        this.constraintRepo = constraintRepo;
        this.roomConstraintRepo = roomConstraintRepo;
        this.solutionPersistence = solutionPersistence;
        this.cwcRepo = cwcRepo;
    }

    public Map<String, Object> solve(Long timetableId, int timeLimitSeconds) {
        long startTime = System.currentTimeMillis();

        Timetable timetable = timetableRepo.findById(timetableId).orElseThrow();

        // Σήμανση ότι τρέχει ο solver (το UI εμφανίζει κατάσταση "ΕΠΕΞΕΡΓΑΣΙΑ").
        timetable.setStatus(Timetable.Status.SOLVING);
        timetableRepo.save(timetable);

	// Φόρτωσε teacher availability constraints
	loadConstraintsFromDb();

        // Build solver model
        List<SolverTimeSlot> solverSlots = buildSolverTimeSlots(timetable);
	List<SolverRoom> solverRooms = buildSolverRooms(timetable);
        List<Lesson> lessons = buildLessons(timetable);

        if (lessons.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "NO_LESSONS");
            result.put("message", "Δεν βρέθηκαν μαθήματα για τοποθέτηση");
            return result;
        }

        // Build and run solver — το SolverConfig/SolverFactory χτίζεται από τον
        // κοινό helper solverFactoryFor(...), με termination ΜΟΝΟ εδώ (solve path).
        // Η ανάλυση (analyzeHardViolations) καλεί τον ίδιο helper ΧΩΡΙΣ termination
        // (μόνο explain, καμία επίλυση).
        SolverFactory<CeidTimetable> solverFactory =
                solverFactoryFor(timetable, Duration.ofSeconds(timeLimitSeconds));
        Solver<CeidTimetable> solver = solverFactory.buildSolver();

        CeidTimetable problem = new CeidTimetable(solverSlots, solverRooms, lessons);
        CeidTimetable solution = solver.solve(problem);

        long elapsed = System.currentTimeMillis() - startTime;

        // Save results — ΑΤΟΜΙΚΑ σε ξεχωριστό @Transactional bean (S3c/BL-1).
        // Το solve() (πάνω) μένει ΕΚΤΟΣ tx· το early SOLVING save (πάνω) είναι ήδη ξεχωριστό.
        return solutionPersistence.persist(timetable, solution, elapsed);
    }

    /**
     * Κοινός helper δημιουργίας SolverFactory (extract από το solve()). Ίδιο config
     * για επίλυση και ανάλυση: solution class + entity class + ο σωστός
     * ConstraintProvider (weekly ή exam). Το termination μπαίνει ΜΟΝΟ όταν δοθεί
     * {@code spentLimit} (solve path)· για explain (spentLimit == null) δεν χρειάζεται
     * — δεν τρέχει επίλυση. Με non-null spentLimit το παραγόμενο config είναι
     * ΠΑΝΟΜΟΙΟΤΥΠΟ με το προηγούμενο inline config του solve().
     */
    private SolverFactory<CeidTimetable> solverFactoryFor(Timetable tt, Duration spentLimit) {
        boolean isExam = tt.getTimetableType() == Timetable.TimetableType.EXAM;
        SolverConfig cfg = new SolverConfig()
                .withSolutionClass(CeidTimetable.class)
                .withEntityClasses(Lesson.class)
                .withConstraintProviderClass(isExam ? ExamConstraintProvider.class
                                                    : CeidConstraintProvider.class);
        if (spentLimit != null) {
            cfg = cfg.withTerminationSpentLimit(spentLimit);
        }
        return SolverFactory.create(cfg);
    }

    /**
     * Φ-SV1: μηχανή ανάλυσης score-explanation (READ-ONLY, καμία εγγραφή).
     * Φορτώνει registries/βάρη (mirror του solve), ξαναχτίζει την ΗΔΗ-τοποθετημένη
     * λύση από τα saved assignments, τρέχει {@code SolutionManager.explain} με τους
     * ΙΔΙΟΥΣ constraints του solver και επιστρέφει τις HARD παραβιάσεις. Δεν αγγίζει
     * το live validation path — προορίζεται ως μελλοντική (Φάση 2) μοναδική πηγή
     * των solver-εκφράσιμων hard errors.
     */
    public List<HardViolation> analyzeHardViolations(Long timetableId) {
        Timetable tt = timetableRepo.findById(timetableId).orElseThrow();
        loadConstraintsFromDb();                 // ΙΔΙΟ με solve(): registries + SolverWeights overlay
        List<TimetableAssignment> assignments = assignmentRepo.findByTimetableId(timetableId);
        CeidTimetable solution = buildPlacedSolution(tt, assignments);

        SolutionManager<CeidTimetable, HardSoftScore> sm =
                SolutionManager.create(solverFactoryFor(tt, null));
        ScoreExplanation<CeidTimetable, HardSoftScore> explanation = sm.explain(solution);
        return extractHardViolations(explanation);
    }

    /**
     * Ξαναχτίζει ΤΟΠΟΘΕΤΗΜΕΝΗ λύση από τα saved assignments (live Course, saved
     * slot/room). {@code Lesson.id := assignment.id} ώστε τα indicted Lessons να
     * αντιστοιχίζονται πίσω σε assignment ids. Αγνοεί assignments με null
     * course/room/timeSlot/type (αυτά είναι INVALID_ASSIGNMENT — τα πιάνει το
     * integrity layer, ΟΧΙ ο solver path).
     */
    private CeidTimetable buildPlacedSolution(Timetable tt, List<TimetableAssignment> assignments) {
        Map<Long, Set<String>> teacherKeyMap = buildTeacherKeyMap();
        Map<Long, SolverTimeSlot> slotIndex = new LinkedHashMap<>();
        Map<Long, SolverRoom> roomIndex = new LinkedHashMap<>();
        List<Lesson> lessons = new ArrayList<>();

        for (TimetableAssignment a : assignments) {
            if (a.getCourse() == null || a.getRoom() == null || a.getTimeSlot() == null
                    || a.getAssignmentType() == null) continue;
            Course c = a.getCourse();
            Lesson l = new Lesson(
                    a.getId(), c.getId(), c.getCode(), c.getName(), c.getStudyYear(),
                    c.getCourseType() != null ? c.getCourseType().name() : "ELECTIVE",
                    a.getAssignmentType().name(),
                    c.getExpectedStudents() != null ? c.getExpectedStudents() : 50,
                    c.getSemesterType() != null ? c.getSemesterType().name() : null,
                    c.getSemester() != null ? c.getSemester() : 0);
            l.setTeacherKeys(teacherKeyMap.getOrDefault(c.getId(), Set.of()));
            // A6 exam prefs (mirror του buildLessons) — αδρανή για weekly.
            l.setPreferredRoomCodes(parseCsvCodes(c.getPreferredExamRooms()));
            l.setPreferredStartHours(parseCsvHours(c.getPreferredExamHours()));

            SolverTimeSlot sts = slotIndex.computeIfAbsent(a.getTimeSlot().getId(),
                    k -> toSolverTimeSlot(a.getTimeSlot()));
            SolverRoom sr = roomIndex.computeIfAbsent(a.getRoom().getId(),
                    k -> toSolverRoom(a.getRoom()));
            l.setTimeSlot(sts);   // ΗΔΗ τοποθετημένο
            l.setRoom(sr);
            lessons.add(l);
        }
        return new CeidTimetable(new ArrayList<>(slotIndex.values()),
                                 new ArrayList<>(roomIndex.values()), lessons);
    }

    /**
     * PURE (unit-testable χωρίς DB/solver wiring): κρατά μόνο τα ConstraintMatchTotal
     * με αρνητικό hard impact και μαπάρει τα indicted Lessons σε assignment ids. Κάθε
     * ConstraintMatch → μία {@link HardViolation}. Package-private static για
     * στοχευμένο test (precedent: buildTeacherKeyMap/S2).
     *
     * Ο engine μένει CONSTRAINT-AGNOSTIC: εξάγει (α) τα indicted Lessons ως assignment ids
     * και (β) τα υπόλοιπα raw indicted facts ως {@code contextFacts} (π.χ. το group-key
     * [studyYear, day, count] των aggregate constraints), ΧΩΡΙΣ να τα ερμηνεύει. Η ερμηνεία
     * (ποιο fact = year/day/count) ζει στον translator.
     */
    static List<HardViolation> extractHardViolations(
            ScoreExplanation<CeidTimetable, HardSoftScore> explanation) {
        List<HardViolation> out = new ArrayList<>();
        for (ConstraintMatchTotal<HardSoftScore> cmt :
                explanation.getConstraintMatchTotalMap().values()) {
            if (cmt.getScore().hardScore() >= 0) continue;            // μόνο HARD παραβιάσεις
            for (ConstraintMatch<HardSoftScore> cm : cmt.getConstraintMatchSet()) {
                List<Long> ids = cm.getIndictedObjectList().stream()
                        .filter(o -> o instanceof Lesson)
                        .map(o -> ((Lesson) o).getId())
                        .distinct().toList();
                // raw non-Lesson facts στη σειρά του indictment (group-key order)
                List<Object> contextFacts = cm.getIndictedObjectList().stream()
                        .filter(o -> !(o instanceof Lesson))
                        .toList();
                out.add(new HardViolation(cmt.getConstraintRef().constraintName(),
                                          ids, cm.getScore().hardScore(), contextFacts));
            }
        }
        return out;
    }

private List<SolverTimeSlot> buildSolverTimeSlots(Timetable timetable) {
    List<SolverTimeSlot> result = new ArrayList<>();

    boolean examTimetable = timetable.getTimetableType() == Timetable.TimetableType.EXAM;

    TimeSlot.SlotType requiredSlotType = examTimetable
            ? TimeSlot.SlotType.EXAM
            : TimeSlot.SlotType.SEMESTER;

    // Custom εξαιρέσεις του προγράμματος (οι επίσημες αργίες ελέγχονται ξεχωριστά).
    // Τα exam slots είναι κοινόχρηστα: παλιά slots σε αργία/εξαίρεση από άλλο
    // πρόγραμμα δεν πρέπει να μπαίνουν στο value range αυτού του solve.
    Set<LocalDate> excluded = new HashSet<>(
            timetable.getExcludedDates() != null
                    ? timetable.getExcludedDates() : List.of());

    for (TimeSlot ts : timeSlotRepo.findBySlotType(requiredSlotType)) {
        if (examTimetable) {
            if (ts.getSpecificDate() == null) {
                continue;
            }

            if (timetable.getStartDate() != null
                    && ts.getSpecificDate().isBefore(timetable.getStartDate())) {
                continue;
            }

            if (timetable.getEndDate() != null
                    && ts.getSpecificDate().isAfter(timetable.getEndDate())) {
                continue;
            }

            if (ExamDateRules.isExcludedExamDate(ts.getSpecificDate(), excluded)) {
                continue;
            }
        }

        // Single source: ίδιος mapper με την ανάλυση (buildPlacedSolution).
        result.add(toSolverTimeSlot(ts));
    }

    return result;
}

/** Mapper TimeSlot entity → SolverTimeSlot. SINGLE SOURCE: τον μοιράζονται ο builder
 *  ({@link #buildSolverTimeSlots}) και η ανάλυση ({@link #buildPlacedSolution}) ώστε
 *  τα κρίσιμα πεδία (dayOfWeek/startHour/dayKey) να παράγονται ΠΑΝΟΜΟΙΟΤΥΠΑ. Για
 *  semester slots: dayKey = όνομα ημέρας· για exam slots: dayKey = ISO ημερομηνία.
 *  Package-private static για στοχευμένο parity test. */
static SolverTimeSlot toSolverTimeSlot(TimeSlot ts) {
    int startHour = ts.getStartTime() != null ? ts.getStartTime().getHour() : 9;

    String dayKey;
    if (ts.getSpecificDate() != null) {
        dayKey = ts.getSpecificDate().toString();
    } else {
        dayKey = ts.getDayOfWeek() != null ? ts.getDayOfWeek().name() : "MONDAY";
    }

    return new SolverTimeSlot(
            ts.getId(),
            ts.getDayOfWeek() != null ? ts.getDayOfWeek().name() : "MONDAY",
            startHour,
            dayKey
    );
}

@Transactional
public void generateExamSlotsForTimetable(Timetable timetable) {
    if (timetable.getStartDate() == null || timetable.getEndDate() == null) {
        throw new IllegalArgumentException("Για πρόγραμμα εξεταστικής απαιτούνται startDate και endDate.");
    }

    if (timetable.getEndDate().isBefore(timetable.getStartDate())) {
        throw new IllegalArgumentException("Το endDate δεν μπορεί να είναι πριν από το startDate.");
    }

    // Ρεαλιστικά παράθυρα εξετάσεων του τμήματος: 9-12, 12-15, 15-18, 18-21.
    // (Ίδια με το μαζικό generate-exam-slots endpoint.)
    List<LocalTime> examStartTimes = new java.util.ArrayList<>();
    for (int h : new int[]{9, 12, 15, 18}) {
        examStartTimes.add(LocalTime.of(h, 0));
    }

    // Custom εξαιρέσεις του admin (επίσημες αργίες εξαιρούνται ξεχωριστά).
    Set<LocalDate> excluded = new HashSet<>(
            timetable.getExcludedDates() != null
                    ? timetable.getExcludedDates() : List.of());

    LocalDate currentDate = timetable.getStartDate();

    while (!currentDate.isAfter(timetable.getEndDate())) {
        if (isExamWorkingDay(currentDate)
                && !ExamDateRules.isExcludedExamDate(currentDate, excluded)) {
            for (LocalTime startTime : examStartTimes) {
                LocalDate date = currentDate;

                List<TimeSlot> existingSlots = timeSlotRepo
        .findBySlotTypeAndSpecificDateAndStartTime(
                TimeSlot.SlotType.EXAM,
                date,
                startTime
        );

    boolean exists = !existingSlots.isEmpty();

                if (!exists) {
                    TimeSlot slot = new TimeSlot();
                    slot.setSlotType(TimeSlot.SlotType.EXAM);
                    slot.setSpecificDate(date);
                    slot.setDayOfWeek(date.getDayOfWeek());
                    slot.setStartTime(startTime);
                    slot.setEndTime(startTime.plusHours(3));
                    slot.setExamPeriodLabel(timetable.getSemesterType() != null
                            ? timetable.getSemesterType().name()
                            : "EXAM");

                    timeSlotRepo.save(slot);
                }
            }
        }

        currentDate = currentDate.plusDays(1);
    }
}

private boolean isExamWorkingDay(LocalDate date) {
    DayOfWeek day = date.getDayOfWeek();

    return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
}

private List<SolverRoom> buildSolverRooms(Timetable timetable) {
    List<Room> sourceRooms;

    if (timetable.getTimetableType() == Timetable.TimetableType.EXAM) {
        sourceRooms = roomRepo.findByAvailableForExamsTrueAndActiveTrue();
    } else {
        sourceRooms = roomRepo.findByAvailableForSemesterTrueAndActiveTrue();
    }

    List<SolverRoom> result = new ArrayList<>();

    for (Room room : sourceRooms) {
        // Single source: ίδιος mapper με την ανάλυση (buildPlacedSolution).
        result.add(toSolverRoom(room));
    }

    return result;
}

/** Mapper Room entity → SolverRoom. SINGLE SOURCE (βλ. {@link #toSolverTimeSlot}):
 *  τον μοιράζονται builder ({@link #buildSolverRooms}) και ανάλυση. */
static SolverRoom toSolverRoom(Room room) {
    return new SolverRoom(
            room.getId(),
            room.getCode(),
            room.getCapacity(),
            room.getRoomType() != null ? room.getRoomType().name() : "CLASSROOM"
    );
}

    private List<Lesson> buildLessons(Timetable timetable) {
        Map<Long, Set<String>> teacherKeyMap = buildTeacherKeyMap();

        List<Course> courses = courseRepo.findByDeletedFalse().stream()
                .filter(c -> isCourseRelevant(c, timetable))
                .toList();

        // Φόρτωσε τα υπάρχοντα manual assignments του timetable
        // ώστε να μην ξαναδημιουργηθούν lessons για ώρες που έχουν ήδη τοποθετηθεί χειροκίνητα.
        List<TimetableAssignment> existingAssignments = assignmentRepo.findByTimetableId(timetable.getId());

        // Χτίσε map: courseId → assignmentType → count of manual assignments
        Map<Long, Map<String, Integer>> manualCounts = new HashMap<>();
        for (TimetableAssignment a : existingAssignments) {
            boolean isManual = Boolean.TRUE.equals(a.getManuallyAssigned())
                            || Boolean.TRUE.equals(a.getIsLocked());
            if (!isManual) continue;
            if (a.getCourse() == null || a.getAssignmentType() == null) continue;

            manualCounts
                .computeIfAbsent(a.getCourse().getId(), k -> new HashMap<>())
                .merge(a.getAssignmentType().name(), 1, Integer::sum);
        }

        List<Lesson> lessons = new ArrayList<>();
        long lessonId = 1;

        boolean isExam = timetable.getTimetableType() == Timetable.TimetableType.EXAM;

        for (Course course : courses) {
            int students = course.getExpectedStudents() != null ? course.getExpectedStudents() : 50;
            String semType = course.getSemesterType() != null ? course.getSemesterType().name() : null;
            String cType = course.getCourseType() != null ? course.getCourseType().name() : "ELECTIVE";
            int sem = course.getSemester() != null ? course.getSemester() : 0;

            Map<String, Integer> courseManual = manualCounts.getOrDefault(course.getId(), Map.of());

            if (isExam) {
                // Εξεταστική: 1 lesson ανά μάθημα — αν υπάρχει ήδη manual EXAM, παράλειψε
                int manualExams = courseManual.getOrDefault("EXAM", 0);
                if (manualExams >= 1) continue;

                Lesson l = new Lesson(lessonId++, course.getId(), course.getCode(), course.getName(),
                        course.getStudyYear(), cType, "EXAM", students, semType, sem);
                l.setTeacherKeys(teacherKeyMap.getOrDefault(course.getId(), Set.of()));
                l.setPreferredRoomCodes(parseCsvCodes(course.getPreferredExamRooms()));
                l.setPreferredStartHours(parseCsvHours(course.getPreferredExamHours()));
                lessons.add(l);
            } else {
                // Semester: αφαίρεσε τα ήδη manual-placed από τις απαιτούμενες ώρες
                int lecture  = Math.max(0, (course.getLectureHours()  != null ? course.getLectureHours()  : 0)
                                          - courseManual.getOrDefault("LECTURE",  0));
                int tutorial = Math.max(0, (course.getTutorialHours() != null ? course.getTutorialHours() : 0)
                                          - courseManual.getOrDefault("TUTORIAL", 0));
                int lab      = Math.max(0, (course.getLabHours()      != null ? course.getLabHours()      : 0)
                                          - courseManual.getOrDefault("LAB",      0));

                for (int i = 0; i < lecture; i++) {
                    Lesson l = new Lesson(lessonId++, course.getId(), course.getCode(), course.getName(),
                            course.getStudyYear(), cType, "LECTURE", students, semType, sem);
                    l.setTeacherKeys(teacherKeyMap.getOrDefault(course.getId(), Set.of()));
                    lessons.add(l);
                }
                for (int i = 0; i < tutorial; i++) {
                    Lesson l = new Lesson(lessonId++, course.getId(), course.getCode(), course.getName(),
                            course.getStudyYear(), cType, "TUTORIAL", students, semType, sem);
                    l.setTeacherKeys(teacherKeyMap.getOrDefault(course.getId(), Set.of()));
                    lessons.add(l);
                }
                for (int i = 0; i < lab; i++) {
                    Lesson l = new Lesson(lessonId++, course.getId(), course.getCode(), course.getName(),
                            course.getStudyYear(), cType, "LAB", students, semType, sem);
                    l.setTeacherKeys(teacherKeyMap.getOrDefault(course.getId(), Set.of()));
                    lessons.add(l);
                }
            }
        }

        return lessons;
    }

/** A6: "Β, Δ1" -> {"Β","Δ1"} */
private static java.util.Set<String> parseCsvCodes(String csv) {
    if (csv == null || csv.isBlank()) return java.util.Set.of();
    java.util.Set<String> out = new java.util.HashSet<>();
    for (String part : csv.split("[,;]")) {
        String p = part.trim();
        if (!p.isEmpty()) out.add(p);
    }
    return out;
}

/** A6: "9, 12" -> {9, 12} — αγνοεί μη αριθμητικές τιμές. */
private static java.util.Set<Integer> parseCsvHours(String csv) {
    if (csv == null || csv.isBlank()) return java.util.Set.of();
    java.util.Set<Integer> out = new java.util.HashSet<>();
    for (String part : csv.split("[,;]")) {
        try { out.add(Integer.parseInt(part.trim())); } catch (NumberFormatException ignored) { }
    }
    return out;
}

private boolean isCourseRelevant(Course course, Timetable timetable) {
    // BL-10: ενοποιημένη schedulability predicate (active ∧ visible ∧ semester).
    // Ήταν inline εδώ· εξήχθη στο util.CourseRelevance.isSchedulable ώστε το παγωμένο
    // scope (TimetableScopeService) == ΑΚΡΙΒΩΣ ό,τι επιχειρεί να τοποθετήσει ο solver,
    // χωρίς drift. Ταυτόσημο σε αποτέλεσμα με το προηγούμενο inline body (Gate A,
    // κλειδωμένο από CourseRelevanceSchedulabilityTest — 144-combo equivalence oracle).
    return CourseRelevance.isSchedulable(course, timetable);
}

/*
 * S2: το course_teachers M2M είναι η AUTHORITATIVE πηγή teacherKeys.
 *  - Φορτώνεται με join-fetch (αξιόπιστο χωρίς OSIV/transaction).
 *  - Φιλτράρονται οι inactive διδάσκοντες (BL-5: soft-delete → αόρατος στον
 *    solver, συμμετρικά με τις inactive αίθουσες του S1).
 *  - teachers_text: DEPRECATED fallback — χρησιμοποιείται ΜΟΝΟ για μαθήματα
 *    χωρίς καμία CourseTeacher γραμμή (να μη χαθούν conflict edges μη-wired
 *    μαθημάτων). Η στήλη παραμένει για display· drop σε μελλοντικό migration
 *    αφού η Φ2 wiring εξασφαλίσει πλήρη κάλυψη M2M.
 * Package-private για στοχευμένο test (TeacherKeyMapTest, ίδιο package).
 */
Map<Long, Set<String>> buildTeacherKeyMap() {
    Map<Long, Set<String>> map = new HashMap<>();
    Set<Long> coursesWithM2M = new HashSet<>();

    for (CourseTeacher ct : courseTeacherRepo.findAllWithTeacherAndCourse()) {
        if (ct.getCourse() == null || ct.getTeacher() == null) continue;
        Long courseId = ct.getCourse().getId();
        coursesWithM2M.add(courseId);  // έχει M2M row → authoritative, ΟΧΙ fallback

        if (!Boolean.TRUE.equals(ct.getTeacher().getActive())) continue;  // BL-5

        String key = teacherKey(ct.getTeacher().getName());
        if (key != null && !key.isBlank()) {
            map.computeIfAbsent(courseId, k -> new HashSet<>()).add(key);
        }
    }

    // Fallback (deprecated): teachers_text ΜΟΝΟ για μαθήματα χωρίς M2M rows.
    for (Course course : courseRepo.findByDeletedFalse()) {
        if (coursesWithM2M.contains(course.getId())) continue;
        if (course.getTeachersText() == null || course.getTeachersText().isBlank()) continue;

        for (String part : splitTeacherText(course.getTeachersText())) {
            String key = teacherKey(part);
            if (!key.isBlank()) {
                map.computeIfAbsent(course.getId(), k -> new HashSet<>()).add(key);
            }
        }
    }

    return map;
}

private String teacherKey(String name) {
    if (name == null || name.isBlank()) return "";

    String cleaned = cleanTeacherNameForKey(name);

    if (cleaned.isBlank()) return "";

    String normalized = java.text.Normalizer.normalize(cleaned, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();

    if (normalized.isBlank()) return "";

    if (isGenericTeacherPlaceholder(normalized)) {
        return "";
    }

    String[] parts = normalized.split(" ");

    if (parts.length == 1) return parts[0];

    return parts[parts.length - 1] + "|" + parts[0].substring(0, 1);
}

private List<String> splitTeacherText(String text) {
    List<String> result = new ArrayList<>();

    if (text == null || text.isBlank()) {
        return result;
    }

    String normalized = text.replace("\n", ",").replace(";", ",");

    StringBuilder current = new StringBuilder();
    int parenthesisDepth = 0;

    for (int i = 0; i < normalized.length(); i++) {
        char ch = normalized.charAt(i);

        if (ch == '(') {
            parenthesisDepth++;
            current.append(ch);
        } else if (ch == ')') {
            if (parenthesisDepth > 0) {
                parenthesisDepth--;
            }
            current.append(ch);
        } else if (ch == ',' && parenthesisDepth == 0) {
            String part = current.toString().trim();

            if (!part.isBlank()) {
                result.add(part);
            }

            current.setLength(0);
        } else {
            current.append(ch);
        }
    }

    String lastPart = current.toString().trim();

    if (!lastPart.isBlank()) {
        result.add(lastPart);
    }

    return result;
}

private String cleanTeacherNameForKey(String name) {
    if (name == null) return "";

    String cleaned = name.trim();

    cleaned = cleaned.replaceAll("\\([^)]*\\)", " ");

    int openParenIndex = cleaned.indexOf('(');
    if (openParenIndex >= 0) {
        cleaned = cleaned.substring(0, openParenIndex);
    }

    cleaned = cleaned.replaceAll("(?iu)\\bΕΔΙΠ\\b", " ");
    cleaned = cleaned.replaceAll("(?iu)\\bΑΑΔΕ\\b", " ");

    cleaned = cleaned.replaceAll("(?iu)\\bΕντεταλμένος\\s+Διδάσκων\\b.*", " ");
    cleaned = cleaned.replaceAll("(?iu)\\bΕντετ\\.?\\s+Διδάσκων\\b.*", " ");

    cleaned = cleaned.replaceAll("(?iu)\\bκαι\\b\\s*$", " ");
    cleaned = cleaned.replaceAll("(?iu)\\bή\\b\\s*$", " ");
    cleaned = cleaned.replaceAll("(?iu)ή/και\\s*$", " ");

    return cleaned.replaceAll("\\s+", " ").trim();
}

private boolean isGenericTeacherPlaceholder(String normalizedName) {
    if (normalizedName == null || normalizedName.isBlank()) {
        return true;
    }

    String value = normalizedName.trim();

    if (value.equals("ΑΑΔΕ")) return true;
    if (value.equals("ΕΔΙΠ")) return true;
    if (value.equals("ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ")) return true;
    if (value.equals("ΕΝΤΕΤ ΔΙΔΑΣΚΩΝ")) return true;

    return value.contains("ΔΙΔΑΣΚΩΝ")
            && (value.contains("ΕΝΤΕΤΑΛΜΕΝΟΣ") || value.contains("ΕΝΤΕΤ") || value.contains("ΑΑΔΕ"));
}

private String hardScoreName(HardSoftScore score) {
    if (score == null) {
        return "UNKNOWN";
    }

    return score.hardScore() < 0 ? "SOLVED_WITH_HARD_CONFLICTS" : "SOLVED";
}

/**
     * S4b-2b: overlay των persisted (editable) βαρών πάνω στα code defaults του
     * SolverWeights, per-solve (καλείται από το loadConstraintsFromDb() πριν χτιστεί
     * το CeidTimetable). Με seeded defaults είναι no-op (baseline αμετάβλητο)·
     * ενεργοποιείται μόνο σε admin edits.
     *
     * Policy (κλειδωμένη — βλ. recon §D):
     *  - resetToDefaults() ΠΡΩΤΑ → idempotent per-solve (αλλαγή & επαναφορά βάρους
     *    στη ΒΔ αντικατοπτρίζεται σωστά).
     *  - HARD: enabled locked & weight>=1. disable ή weight<1 → fallback στο code
     *    default — ΠΟΤΕ σιωπηλή απώλεια hard κανόνα.
     *  - SOFT: disable → βάρος 0· αλλιώς το weight.
     *  - score_level read-only (μόνο διαβάζεται για HARD-floor vs SOFT-disable).
     * Package-private για στοχευμένο test (precedent: buildTeacherKeyMap/S2, assignmentToDto/S3d).
     */
    void applyConstraintWeightOverrides() {
        SolverWeights.resetToDefaults();              // κάθε solve ξεκινά καθαρό
        for (ConstraintWeightConfig c : cwcRepo.findAll()) {
            if ("HARD".equals(c.getScoreLevel())) {
                // HARD δεν απενεργοποιείται/μηδενίζεται ποτέ — fallback στο default.
                if (c.isEnabled() && c.getWeight() >= 1) {
                    SolverWeights.applyOverride(c.getConstraintKey(), c.getWeight());
                }
            } else {
                // SOFT: disable επιτρέπεται → βάρος 0· αλλιώς το weight.
                SolverWeights.applyOverride(c.getConstraintKey(),
                        c.isEnabled() ? c.getWeight() : 0);
            }
        }
    }

private void loadConstraintsFromDb() {
        applyConstraintWeightOverrides();
        loadRoomConstraintsFromDb();
        TeacherAvailabilityRegistry.load();

        List<TeacherConstraint> dbConstraints = constraintRepo.findAllWithTeacher();
        if (dbConstraints.isEmpty()) return;

        Map<String, Set<String>> blocked = new HashMap<>(
            TeacherAvailabilityConstraints.BLOCKED_SLOTS != null
                ? TeacherAvailabilityConstraints.BLOCKED_SLOTS : new HashMap<>());
        Map<String, Set<String>> preferred = new HashMap<>(
            TeacherAvailabilityConstraints.PREFERRED_SLOTS != null
                ? TeacherAvailabilityConstraints.PREFERRED_SLOTS : new HashMap<>());

        Set<String> dbKeys = new HashSet<>();
        for (TeacherConstraint c : dbConstraints) {
            String key = teacherKey(c.getTeacher().getName());
            if (!key.isBlank()) dbKeys.add(key);
        }
        for (String key : dbKeys) {
            blocked.remove(key);
            preferred.remove(key);
        }

        for (TeacherConstraint c : dbConstraints) {
            String key = teacherKey(c.getTeacher().getName());
            if (key.isBlank()) continue;
            String slot = c.getDayOfWeek() + "_" + c.getHour();
            if (c.getConstraintType() == TeacherConstraint.ConstraintType.BLOCKED) {
                blocked.computeIfAbsent(key, k -> new HashSet<>()).add(slot);
            } else {
                preferred.computeIfAbsent(key, k -> new HashSet<>()).add(slot);
            }
        }

        TeacherAvailabilityConstraints.BLOCKED_SLOTS   = Collections.unmodifiableMap(blocked);
        TeacherAvailabilityConstraints.PREFERRED_SLOTS = Collections.unmodifiableMap(preferred);
    }

    /** Φορτώνει τις δεσμευμένες ώρες αιθουσών από τη ΒΔ στο registry του solver. */
    private void loadRoomConstraintsFromDb() {
        Map<String, Set<String>> blocked = new HashMap<>();
        for (RoomConstraint c : roomConstraintRepo.findAllWithRoom()) {
            if (c.getConstraintType() != RoomConstraint.ConstraintType.BLOCKED) continue;
            blocked.computeIfAbsent(c.getRoom().getCode(), k -> new HashSet<>())
                   .add(c.getDayOfWeek() + "_" + c.getHour());
        }
        RoomAvailabilityConstraints.BLOCKED_SLOTS = Collections.unmodifiableMap(blocked);
    }

}