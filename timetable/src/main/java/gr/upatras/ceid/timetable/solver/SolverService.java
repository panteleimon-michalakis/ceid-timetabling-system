package gr.upatras.ceid.timetable.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import gr.upatras.ceid.timetable.entity.*;
import gr.upatras.ceid.timetable.entity.TeacherConstraint;
import gr.upatras.ceid.timetable.entity.RoomConstraint;
import gr.upatras.ceid.timetable.repository.RoomConstraintRepository;
import gr.upatras.ceid.timetable.repository.*;
import org.springframework.stereotype.Service;

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

    public SolverService(CourseRepository courseRepo, RoomRepository roomRepo,
                         TimeSlotRepository timeSlotRepo, TimetableRepository timetableRepo,
                         TimetableAssignmentRepository assignmentRepo,
                         CourseTeacherRepository courseTeacherRepo,
                         TeacherConstraintRepository constraintRepo,
                         RoomConstraintRepository roomConstraintRepo) {
        this.courseRepo = courseRepo;
        this.roomRepo = roomRepo;
        this.timeSlotRepo = timeSlotRepo;
        this.timetableRepo = timetableRepo;
        this.assignmentRepo = assignmentRepo;
        this.courseTeacherRepo = courseTeacherRepo;
        this.constraintRepo = constraintRepo;
        this.roomConstraintRepo = roomConstraintRepo;
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

        // Build and run solver
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(CeidTimetable.class)
                .withEntityClasses(Lesson.class)
                .withConstraintProviderClass(
                    timetable.getTimetableType() == Timetable.TimetableType.EXAM
                        ? ExamConstraintProvider.class
                        : CeidConstraintProvider.class)
                .withTerminationSpentLimit(Duration.ofSeconds(timeLimitSeconds));

        SolverFactory<CeidTimetable> solverFactory = SolverFactory.create(solverConfig);
        Solver<CeidTimetable> solver = solverFactory.buildSolver();

        CeidTimetable problem = new CeidTimetable(solverSlots, solverRooms, lessons);
        CeidTimetable solution = solver.solve(problem);

        long elapsed = System.currentTimeMillis() - startTime;

        // Save results
        return saveSolution(timetable, solution, elapsed);
    }

private List<SolverTimeSlot> buildSolverTimeSlots(Timetable timetable) {
    List<SolverTimeSlot> result = new ArrayList<>();

    boolean examTimetable = timetable.getTimetableType() == Timetable.TimetableType.EXAM;

    TimeSlot.SlotType requiredSlotType = examTimetable
            ? TimeSlot.SlotType.EXAM
            : TimeSlot.SlotType.SEMESTER;

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
        }

        int startHour = ts.getStartTime() != null ? ts.getStartTime().getHour() : 9;

        String dayKey;
        if (ts.getSpecificDate() != null) {
            dayKey = ts.getSpecificDate().toString();
        } else {
            dayKey = ts.getDayOfWeek() != null ? ts.getDayOfWeek().name() : "MONDAY";
        }

        result.add(new SolverTimeSlot(
                ts.getId(),
                ts.getDayOfWeek() != null ? ts.getDayOfWeek().name() : "MONDAY",
                startHour,
                dayKey
        ));
    }

    return result;
}

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

    LocalDate currentDate = timetable.getStartDate();

    while (!currentDate.isAfter(timetable.getEndDate())) {
        if (isExamWorkingDay(currentDate)) {
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
        sourceRooms = roomRepo.findByAvailableForExamsTrue();
    } else {
        sourceRooms = roomRepo.findByAvailableForSemesterTrue();
    }

    List<SolverRoom> result = new ArrayList<>();

    for (Room room : sourceRooms) {
        result.add(new SolverRoom(
                room.getId(),
                room.getCode(),
                room.getCapacity(),
                room.getRoomType() != null ? room.getRoomType().name() : "CLASSROOM"
        ));
    }

    return result;
}

    private List<Lesson> buildLessons(Timetable timetable) {
        Map<Long, Set<String>> teacherKeyMap = buildTeacherKeyMap();

        List<Course> courses = courseRepo.findAll().stream()
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
    if (course == null || timetable == null) return false;

    // Ανενεργά μαθήματα αγνοούνται
    if (course.getActive() != null && !course.getActive()) return false;

    // Μαθήματα που γίνονται σε συνεννόηση αγνοούνται
    if (course.getVisibleInTimetable() != null && !course.getVisibleInTimetable()) return false;

    Timetable.SemesterType ttSem = timetable.getSemesterType();
    Course.SemesterType cSem = course.getSemesterType();

    // Αν δεν ορίζεται semesterType σε timetable ή course → συμπεριλαμβάνεται
    if (ttSem == null || cSem == null) return true;

    // BOTH courses → συμπεριλαμβάνονται σε όλα τα timetables
    if (cSem == Course.SemesterType.BOTH) return true;

    // SEPTEMBER exam → περιλαμβάνει ΟΛΑ τα ενεργά μαθήματα (FALL + SPRING + BOTH)
    if (ttSem == Timetable.SemesterType.SEPTEMBER) return true;

    // FALL timetable → FALL courses
    // SPRING timetable → SPRING courses
    return ttSem.name().equals(cSem.name());
}

private Map<Long, Set<String>> buildTeacherKeyMap() {
    Map<Long, Set<String>> map = new HashMap<>();

    for (CourseTeacher ct : courseTeacherRepo.findAll()) {
        if (ct.getCourse() == null || ct.getTeacher() == null) continue;

        try {
            String name = ct.getTeacher().getName();

            if (name != null && !name.isBlank()) {
                String key = teacherKey(name);

                if (!key.isBlank()) {
                    map.computeIfAbsent(ct.getCourse().getId(), k -> new HashSet<>()).add(key);
                }
            }
        } catch (Exception e) {
            // Skip lazy init failures
        }
    }

    for (Course course : courseRepo.findAll()) {
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

    private Map<String, Object> saveSolution(Timetable timetable, CeidTimetable solution, long elapsedMs) {
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

private String hardScoreName(HardSoftScore score) {
    if (score == null) {
        return "UNKNOWN";
    }

    return score.hardScore() < 0 ? "SOLVED_WITH_HARD_CONFLICTS" : "SOLVED";
}

private void loadConstraintsFromDb() {
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