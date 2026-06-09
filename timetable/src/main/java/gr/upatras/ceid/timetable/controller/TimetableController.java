package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.*;
import gr.upatras.ceid.timetable.repository.*;
import gr.upatras.ceid.timetable.repository.CourseTeacherRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Map;

@RestController
@RequestMapping("/api/timetables")
public class TimetableController {

    private final TimetableRepository timetableRepo;
private final TimetableAssignmentRepository assignmentRepo;
private final CourseRepository courseRepo;
private final RoomRepository roomRepo;
private final TimeSlotRepository timeSlotRepo;
private final CourseTeacherRepository courseTeacherRepo;
private final gr.upatras.ceid.timetable.solver.SolverService solverService;
public TimetableController(TimetableRepository timetableRepo,
                           TimetableAssignmentRepository assignmentRepo,
                           CourseRepository courseRepo,
                           RoomRepository roomRepo,
                           TimeSlotRepository timeSlotRepo,
                           CourseTeacherRepository courseTeacherRepo,
                           gr.upatras.ceid.timetable.solver.SolverService solverService) {
    this.timetableRepo = timetableRepo;
    this.assignmentRepo = assignmentRepo;
    this.courseRepo = courseRepo;
    this.roomRepo = roomRepo;
    this.timeSlotRepo = timeSlotRepo;
    this.courseTeacherRepo = courseTeacherRepo;
    this.solverService = solverService;
}

    @GetMapping
    public List<Timetable> getAll(org.springframework.security.core.Authentication auth) {
        boolean isAdminOrTeacher = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                       || a.getAuthority().equals("ROLE_TEACHER"));
        if (isAdminOrTeacher) {
            return timetableRepo.findAll();
        }
        return timetableRepo.findByStatus(Timetable.Status.PUBLISHED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Timetable> getById(@PathVariable Long id) {
        return timetableRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/publish")
    public ResponseEntity<?> publish(@PathVariable Long id) {
        return timetableRepo.findById(id).map(t -> {
            // Εξαμηνιαίο: δεν επιτρέπεται δημοσίευση αν είναι κενό
            if (t.getTimetableType() == Timetable.TimetableType.SEMESTER) {
                boolean empty = assignmentRepo.findByTimetableId(id).isEmpty();
                if (empty) {
                    return (ResponseEntity<?>) ResponseEntity.badRequest()
                        .body(Map.of("error",
                            "Δεν μπορεί να δημοσιευτεί κενό πρόγραμμα. " +
                            "Τοποθέτησε μαθήματα πρώτα."));
                }
            }
            t.setStatus(Timetable.Status.PUBLISHED);
            t.setPublishedAt(java.time.LocalDateTime.now());
            return ResponseEntity.ok((Object) timetableRepo.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/unpublish")
    public ResponseEntity<?> unpublish(@PathVariable Long id) {
        return timetableRepo.findById(id).map(t -> {
            t.setStatus(Timetable.Status.DRAFT);
            t.setPublishedAt(null);
            return ResponseEntity.ok((Object) timetableRepo.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
    String name = body.get("name");

    if (name == null || name.isBlank()) {
        return badRequest("Το όνομα του προγράμματος είναι υποχρεωτικό.");
    }

    Timetable.TimetableType timetableType;
    Timetable.SemesterType semesterType;

    try {
        timetableType = Timetable.TimetableType.valueOf(
                body.getOrDefault("timetableType", "SEMESTER")
        );

        semesterType = Timetable.SemesterType.valueOf(
                body.getOrDefault("semesterType", "FALL")
        );
    } catch (IllegalArgumentException e) {
        return badRequest("Μη έγκυρος τύπος προγράμματος ή περίοδος εξαμήνου.");
    }

    LocalDate startDate = null;
    LocalDate endDate = null;

    String startDateText = body.get("startDate");
    String endDateText = body.get("endDate");

    boolean hasStartDate = startDateText != null && !startDateText.isBlank();
    boolean hasEndDate = endDateText != null && !endDateText.isBlank();

    if (timetableType == Timetable.TimetableType.EXAM && (!hasStartDate || !hasEndDate)) {
        return badRequest("Για πρόγραμμα εξεταστικής απαιτούνται startDate και endDate.");
    }

    if (hasStartDate || hasEndDate) {
        if (!hasStartDate || !hasEndDate) {
            return badRequest("Πρέπει να δοθούν και startDate και endDate.");
        }

        try {
            startDate = LocalDate.parse(startDateText);
            endDate = LocalDate.parse(endDateText);
        } catch (Exception e) {
            return badRequest("Οι ημερομηνίες πρέπει να έχουν μορφή YYYY-MM-DD.");
        }

        if (endDate.isBefore(startDate)) {
            return badRequest("Το endDate δεν μπορεί να είναι πριν από το startDate.");
        }
    }

    Timetable t = Timetable.builder()
            .name(name)
            .academicYear(body.getOrDefault("academicYear", "2025-26"))
            .timetableType(timetableType)
            .semesterType(semesterType)
            .startDate(startDate)
            .endDate(endDate)
            .status(Timetable.Status.DRAFT)
            .createdAt(LocalDateTime.now())
            .build();

    return ResponseEntity.ok(timetableRepo.save(t));
}

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (timetableRepo.existsById(id)) {
            assignmentRepo.deleteAll(assignmentRepo.findByTimetableId(id));
            timetableRepo.deleteById(id);
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/assignments")
    public ResponseEntity<?> getAssignments(@PathVariable Long id) {
        if (!timetableRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> result = assignmentRepo.findByTimetableId(id)
                .stream()
                .map(this::assignmentToDto)
                .toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/assignments")
    public ResponseEntity<?> addAssignment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        if (!body.containsKey("courseId") || !body.containsKey("roomId") || !body.containsKey("timeSlotId")) {
            return badRequest("Λείπουν απαραίτητα πεδία: courseId, roomId ή timeSlotId.");
        }

        var timetableOpt = timetableRepo.findById(id);
        var courseOpt = courseRepo.findById(((Number) body.get("courseId")).longValue());
        var roomOpt = roomRepo.findById(((Number) body.get("roomId")).longValue());
        var timeSlotOpt = timeSlotRepo.findById(((Number) body.get("timeSlotId")).longValue());

        if (timetableOpt.isEmpty()) {
            return badRequest("Δεν βρέθηκε το πρόγραμμα.");
        }

        if (courseOpt.isEmpty()) {
            return badRequest("Δεν βρέθηκε το μάθημα.");
        }

        if (roomOpt.isEmpty()) {
            return badRequest("Δεν βρέθηκε η αίθουσα.");
        }

        if (timeSlotOpt.isEmpty()) {
            return badRequest("Δεν βρέθηκε η χρονοθυρίδα.");
        }

        Timetable timetable = timetableOpt.get();
        Course course = courseOpt.get();
        Room room = roomOpt.get();
        TimeSlot timeSlot = timeSlotOpt.get();

        String defaultAssignmentType = isExamTimetable(timetable) ? "EXAM" : "LECTURE";

String assignmentTypeText = String.valueOf(body.getOrDefault("assignmentType", defaultAssignmentType))
        .trim()
        .toUpperCase(Locale.ROOT);

TimetableAssignment.AssignmentType assignmentType;

try {
    assignmentType = TimetableAssignment.AssignmentType.valueOf(assignmentTypeText);
} catch (IllegalArgumentException ex) {
    return badRequest("Μη έγκυρος τύπος ανάθεσης: " + assignmentTypeText);
}

        ResponseEntity<?> validationError = validateAssignment(timetable, course, room, timeSlot, assignmentType);
        if (validationError != null) {
            return validationError;
        }

Object lockedValue = body.get("isLocked");
boolean locked = Boolean.TRUE.equals(lockedValue)
        || "true".equalsIgnoreCase(String.valueOf(lockedValue));

        TimetableAssignment assignment = TimetableAssignment.builder()
                .timetable(timetable)
                .course(course)
                .room(room)
                .timeSlot(timeSlot)
                .assignmentType(assignmentType)
                .isLocked(locked)
                .manuallyAssigned(true)
                .createdAt(LocalDateTime.now())
                .build();

        TimetableAssignment saved = assignmentRepo.save(assignment);
        return ResponseEntity.ok(assignmentToDto(saved));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    public ResponseEntity<Void> removeAssignment(@PathVariable Long assignmentId) {
        return assignmentRepo.findById(assignmentId)
                .map(assignment -> {
                    assignmentRepo.delete(assignment);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/assignments")
    public ResponseEntity<Void> clearAssignments(@PathVariable Long id) {
        if (!timetableRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        List<TimetableAssignment> assignments = assignmentRepo.findByTimetableId(id);
        assignmentRepo.deleteAll(assignments);

        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // ΠΡΟΤΕΙΝΟΜΕΝΕΣ ΤΟΠΟΘΕΤΗΣΕΙΣ ΜΑΘΗΜΑΤΟΣ
    // GET /api/timetables/{id}/placement-options?courseId=1&assignmentType=LECTURE
    // =========================================================

    @GetMapping("/{id}/placement-options")
public ResponseEntity<?> getPlacementOptions(
        @PathVariable Long id,
        @RequestParam Long courseId,
        @RequestParam(required = false) String assignmentType) {

    var timetableOpt = timetableRepo.findById(id);
    var courseOpt = courseRepo.findById(courseId);

    if (timetableOpt.isEmpty()) {
        return ResponseEntity.notFound().build();
    }

    if (courseOpt.isEmpty()) {
        return badRequest("Δεν βρέθηκε το μάθημα.");
    }

    Timetable timetable = timetableOpt.get();
    Course course = courseOpt.get();

    String assignmentTypeText = assignmentType;

    if (assignmentTypeText == null || assignmentTypeText.isBlank()) {
        assignmentTypeText = isExamTimetable(timetable) ? "EXAM" : "LECTURE";
    }

    assignmentTypeText = assignmentTypeText.trim().toUpperCase(Locale.ROOT);

    TimetableAssignment.AssignmentType parsedAssignmentType;

    try {
        parsedAssignmentType = TimetableAssignment.AssignmentType.valueOf(assignmentTypeText);
    } catch (IllegalArgumentException ex) {
        return badRequest("Μη έγκυρος τύπος ανάθεσης: " + assignmentTypeText);
    }

    List<TimetableAssignment> existingAssignments = assignmentRepo.findByTimetableId(id);

    List<Room> rooms = (isExamTimetable(timetable)
            || parsedAssignmentType == TimetableAssignment.AssignmentType.EXAM)
            ? roomRepo.findByAvailableForExamsTrue()
            : roomRepo.findAll();

    TimeSlot.SlotType desiredSlotType = (isExamTimetable(timetable)
            || parsedAssignmentType == TimetableAssignment.AssignmentType.EXAM)
            ? TimeSlot.SlotType.EXAM
            : TimeSlot.SlotType.SEMESTER;

    List<TimeSlot> timeSlots = timeSlotRepo.findAll()
            .stream()
            .filter(timeSlot -> {
                if (desiredSlotType == TimeSlot.SlotType.EXAM) {
                    return timeSlot.getSlotType() == TimeSlot.SlotType.EXAM;
                }

                return timeSlot.getSlotType() == null
                        || timeSlot.getSlotType() == TimeSlot.SlotType.SEMESTER;
            })
            .filter(timeSlot -> {
                if (desiredSlotType != TimeSlot.SlotType.EXAM) {
                    return true;
                }

                if (timeSlot.getSpecificDate() == null) {
                    return false;
                }

                if (timetable.getStartDate() != null
                        && timeSlot.getSpecificDate().isBefore(timetable.getStartDate())) {
                    return false;
                }

                if (timetable.getEndDate() != null
                        && timeSlot.getSpecificDate().isAfter(timetable.getEndDate())) {
                    return false;
                }

                return true;
            })
            .sorted(Comparator
                    .comparing(TimeSlot::getSpecificDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingInt(slot -> dayOrder(slot.getDayOfWeek()))
                    .thenComparing(TimeSlot::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

    List<Map<String, Object>> options = new ArrayList<>();

    for (TimeSlot timeSlot : timeSlots) {
        for (Room room : rooms) {

            ResponseEntity<?> validationError = validateAssignment(
                    timetable,
                    course,
                    room,
                    timeSlot,
                    parsedAssignmentType
            );

            boolean allowed = validationError == null;

            Map<String, Object> option = new LinkedHashMap<>();
            option.put("allowed", allowed);
            option.put("score", allowed
                    ? calculatePlacementScore(course, room, timeSlot, parsedAssignmentType, existingAssignments)
                    : 0);
            option.put("room", roomToDto(room));
            option.put("timeSlot", timeSlotToDto(timeSlot));

            if (allowed) {
                option.put("status", "ALLOWED");
                option.put("reasons", buildAllowedPlacementReasons(
        timetable,
        course,
        room,
        timeSlot,
        parsedAssignmentType,
        existingAssignments
));
            } else {
                option.put("status", "BLOCKED");
                option.put("reasons", List.of(getValidationErrorText(validationError)));
            }

            options.add(option);
        }
    }

    options.sort((a, b) -> {
        boolean aAllowed = Boolean.TRUE.equals(a.get("allowed"));
        boolean bAllowed = Boolean.TRUE.equals(b.get("allowed"));

        if (aAllowed != bAllowed) {
            return aAllowed ? -1 : 1;
        }

        int aScore = (Integer) a.get("score");
        int bScore = (Integer) b.get("score");

        return Integer.compare(bScore, aScore);
    });

    long allowedCount = options.stream()
            .filter(option -> Boolean.TRUE.equals(option.get("allowed")))
            .count();

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("timetableId", timetable.getId());
    result.put("course", courseToDto(course));
    result.put("assignmentType", parsedAssignmentType.name());
    result.put("totalOptions", options.size());
    result.put("allowedOptions", allowedCount);
    result.put("blockedOptions", options.size() - allowedCount);
    result.put("options", options);

    return ResponseEntity.ok(result);
}

    private int calculatePlacementScore(
            Course course,
            Room room,
            TimeSlot timeSlot,
            TimetableAssignment.AssignmentType assignmentType,
            List<TimetableAssignment> existingAssignments) {

        int score = 50;

        // Χωρητικότητα αίθουσας
        if (course.getExpectedStudents() > 0 && room.getCapacity() > 0) {
            if (room.getCapacity() >= course.getExpectedStudents()) {
                score += 15;
            } else {
                score -= 20;
            }
        }

        // Τύπος αίθουσας
        if (assignmentType == TimetableAssignment.AssignmentType.LAB
                && room.getRoomType() == Room.RoomType.LAB) {
            score += 25;
        }

        if ((assignmentType == TimetableAssignment.AssignmentType.LECTURE
                || assignmentType == TimetableAssignment.AssignmentType.TUTORIAL)
                && room.getRoomType() == Room.RoomType.AMPHITHEATER) {
            score += 10;
        }

        // Προτίμηση σε πιο φυσιολογικές ώρες
        if (timeSlot.getStartTime() != null) {
            int hour = timeSlot.getStartTime().getHour();

            if (hour >= 9 && hour <= 15) {
                score += 5;
            }

            if (hour >= 18) {
                score -= 10;
            }
        }

        // Φόρτος ίδιου έτους την ίδια μέρα
        int sameYearDayLoad = countSameStudyYearAssignmentsOnSameDay(
                existingAssignments,
                course,
                timeSlot
        );

        if (sameYearDayLoad >= 6) {
            score -= 25;
        } else if (sameYearDayLoad >= 4) {
            score -= 10;
        } else {
            score += 5;
        }

        // Προτίμηση σε κοντινές ώρες για να μη δημιουργούνται μεγάλα κενά
        int nearestDistance = nearestSameYearHourDistance(
                existingAssignments,
                course,
                timeSlot
        );

        if (nearestDistance == 1) {
            score += 20;
        } else if (nearestDistance == 2) {
            score += 10;
        } else if (nearestDistance > 2 && nearestDistance < 99) {
            score -= 10;
        }

        // Lunch break για τα 3 πρώτα έτη
        if (wouldRemoveLunchBreak(existingAssignments, course, timeSlot)) {
            score -= 30;
        }

        return Math.max(0, Math.min(100, score));
    }

    private List<String> buildAllowedPlacementReasons(
        Timetable timetable,
        Course course,
        Room room,
        TimeSlot timeSlot,
        TimetableAssignment.AssignmentType assignmentType,
        List<TimetableAssignment> existingAssignments) {

        List<String> reasons = new ArrayList<>();

        reasons.add("Η τοποθέτηση περνάει τους βασικούς κανόνες validation.");

        if (course.getExpectedStudents() > 0 && room.getCapacity() > 0) {
            if (room.getCapacity() >= course.getExpectedStudents()) {
                reasons.add("Η χωρητικότητα της αίθουσας είναι επαρκής για τους αναμενόμενους φοιτητές.");
            } else {
                reasons.add("Η αίθουσα έχει μικρότερη χωρητικότητα από τους αναμενόμενους φοιτητές.");
            }
        }

        if (assignmentType == TimetableAssignment.AssignmentType.LAB) {
            reasons.add("Το εργαστήριο τοποθετείται σε εργαστηριακή αίθουσα.");
        }

        boolean examTimetable = isExamTimetable(timetable)
        || assignmentType == TimetableAssignment.AssignmentType.EXAM;

if (examTimetable) {
    int sameYearExamCount = countSameStudyYearExamAssignmentsOnSameDate(
            existingAssignments,
            course,
            timeSlot
    );

    if (sameYearExamCount == 0) {
        reasons.add("Δεν υπάρχει άλλη εξέταση μαθήματος του ίδιου έτους την ίδια ημερομηνία.");
    } else if (sameYearExamCount == 1) {
        reasons.add("Υπάρχει ήδη 1 εξέταση μαθήματος του ίδιου έτους την ίδια ημερομηνία.");
    } else {
        reasons.add("Υπάρχουν ήδη " + sameYearExamCount
                + " εξετάσεις μαθημάτων του ίδιου έτους την ίδια ημερομηνία.");
    }
} else {
    int sameYearDayLoad = countSameStudyYearAssignmentsOnSameDay(existingAssignments, course, timeSlot);

    if (sameYearDayLoad == 0) {
        reasons.add("Δεν υπάρχει άλλο μάθημα του ίδιου έτους την ίδια μέρα.");
    } else if (sameYearDayLoad == 1) {
        reasons.add("Υπάρχει ήδη 1 ώρα μαθήματος του ίδιου έτους την ίδια μέρα.");
    } else {
        reasons.add("Υπάρχουν ήδη " + sameYearDayLoad
                + " ώρες μαθημάτων του ίδιου έτους την ίδια μέρα.");
    }

    int nearestDistance = nearestSameYearHourDistance(existingAssignments, course, timeSlot);

    if (nearestDistance == 1) {
        reasons.add("Η ώρα είναι κοντά σε άλλο μάθημα του ίδιου έτους, άρα μειώνονται τα κενά.");
    } else if (nearestDistance == 2) {
        reasons.add("Η ώρα έχει αποδεκτή απόσταση από άλλο μάθημα του ίδιου έτους.");
    } else if (nearestDistance > 2 && nearestDistance < 99) {
        reasons.add("Η ώρα απέχει αρκετά από άλλο μάθημα του ίδιου έτους, πιθανό να δημιουργήσει κενό.");
    }
}

        return reasons;
    }

    private String getValidationErrorText(ResponseEntity<?> validationError) {
        if (validationError == null) {
            return "Δεν υπάρχει σφάλμα validation.";
        }

        Object body = validationError.getBody();

        if (body instanceof Map<?, ?> map) {
            Object error = map.get("error");

            if (error != null) {
                return String.valueOf(error);
            }
        }

        return "Η τοποθέτηση δεν επιτρέπεται από τους κανόνες validation.";
    }

    private int countSameStudyYearAssignmentsOnSameDay(
            List<TimetableAssignment> assignments,
            Course course,
            TimeSlot candidateTimeSlot) {

        if (candidateTimeSlot.getDayOfWeek() == null) {
            return 0;
        }

        int count = 0;

        for (TimetableAssignment assignment : assignments) {
            if (assignment.getCourse() == null || assignment.getTimeSlot() == null) {
                continue;
            }

            if (assignment.getTimeSlot().getDayOfWeek() == null) {
                continue;
            }

            boolean sameStudyYear = assignment.getCourse().getStudyYear() == course.getStudyYear();
            boolean sameDay = assignment.getTimeSlot().getDayOfWeek().equals(candidateTimeSlot.getDayOfWeek());

            if (sameStudyYear && sameDay) {
                count++;
            }
        }

        return count;
    }

private int countSameStudyYearExamAssignmentsOnSameDate(
        List<TimetableAssignment> assignments,
        Course course,
        TimeSlot candidateTimeSlot) {

    if (candidateTimeSlot == null) {
        return 0;
    }

    int count = 0;

    for (TimetableAssignment assignment : assignments) {
        if (assignment.getCourse() == null
                || assignment.getTimeSlot() == null
                || assignment.getAssignmentType() == null) {
            continue;
        }

        if (assignment.getAssignmentType() != TimetableAssignment.AssignmentType.EXAM) {
            continue;
        }

        boolean sameStudyYear = assignment.getCourse().getStudyYear() == course.getStudyYear();
        boolean sameDate = sameCalendarDay(assignment.getTimeSlot(), candidateTimeSlot);

        if (sameStudyYear && sameDate) {
            count++;
        }
    }

    return count;
}

private boolean sameCalendarDay(TimeSlot existingSlot, TimeSlot candidateSlot) {
    if (existingSlot == null || candidateSlot == null) {
        return false;
    }

    if (existingSlot.getSpecificDate() != null && candidateSlot.getSpecificDate() != null) {
        return existingSlot.getSpecificDate().equals(candidateSlot.getSpecificDate());
    }

    if (existingSlot.getDayOfWeek() != null && candidateSlot.getDayOfWeek() != null) {
        return existingSlot.getDayOfWeek().equals(candidateSlot.getDayOfWeek());
    }

    return false;
}

    private int nearestSameYearHourDistance(
            List<TimetableAssignment> assignments,
            Course course,
            TimeSlot candidateTimeSlot) {

        if (candidateTimeSlot.getDayOfWeek() == null || candidateTimeSlot.getStartTime() == null) {
            return 99;
        }

        int candidateHour = candidateTimeSlot.getStartTime().getHour();
        int nearest = 99;

        for (TimetableAssignment assignment : assignments) {
            if (assignment.getCourse() == null || assignment.getTimeSlot() == null) {
                continue;
            }

            TimeSlot existingSlot = assignment.getTimeSlot();

            if (existingSlot.getDayOfWeek() == null || existingSlot.getStartTime() == null) {
                continue;
            }

            boolean sameStudyYear = assignment.getCourse().getStudyYear() == course.getStudyYear();
            boolean sameDay = existingSlot.getDayOfWeek().equals(candidateTimeSlot.getDayOfWeek());

            if (sameStudyYear && sameDay) {
                int distance = Math.abs(existingSlot.getStartTime().getHour() - candidateHour);
                nearest = Math.min(nearest, distance);
            }
        }

        return nearest;
    }

    private boolean wouldRemoveLunchBreak(
            List<TimetableAssignment> assignments,
            Course course,
            TimeSlot candidateTimeSlot) {

        return wouldRemoveLunchBreak(assignments, course, candidateTimeSlot, null);
    }

    private boolean wouldRemoveLunchBreak(
            List<TimetableAssignment> assignments,
            Course course,
            TimeSlot candidateTimeSlot,
            Long ignoredAssignmentId) {

        if (course.getStudyYear() < 1 || course.getStudyYear() > 3) {
            return false;
        }

        if (candidateTimeSlot.getDayOfWeek() == null || candidateTimeSlot.getStartTime() == null) {
            return false;
        }

        if (!isLunchHour(candidateTimeSlot.getStartTime())) {
            return false;
        }

        boolean occupied12 = false;
        boolean occupied13 = false;
        boolean occupied14 = false;

        int candidateHour = candidateTimeSlot.getStartTime().getHour();

        if (candidateHour == 12) occupied12 = true;
        if (candidateHour == 13) occupied13 = true;
        if (candidateHour == 14) occupied14 = true;

        for (TimetableAssignment assignment : assignments) {
            if (ignoredAssignmentId != null && assignment.getId().equals(ignoredAssignmentId)) {
                continue;
            }

            if (assignment.getCourse() == null || assignment.getTimeSlot() == null) {
                continue;
            }

            TimeSlot existingSlot = assignment.getTimeSlot();

            if (existingSlot.getDayOfWeek() == null || existingSlot.getStartTime() == null) {
                continue;
            }

            boolean sameStudyYear = assignment.getCourse().getStudyYear() == course.getStudyYear();
            boolean sameDay = existingSlot.getDayOfWeek().equals(candidateTimeSlot.getDayOfWeek());

            if (!sameStudyYear || !sameDay) {
                continue;
            }

            int hour = existingSlot.getStartTime().getHour();

            if (hour == 12) occupied12 = true;
            if (hour == 13) occupied13 = true;
            if (hour == 14) occupied14 = true;
        }

        return occupied12 && occupied13 && occupied14;
    }

    private boolean isLunchHour(LocalTime startTime) {
        if (startTime == null) {
            return false;
        }

        int hour = startTime.getHour();
        return hour == 12 || hour == 13 || hour == 14;
    }

    private int countSameStudyYearLecturesOnSameDay(
            List<TimetableAssignment> assignments,
            Course course,
            TimeSlot candidateTimeSlot,
            Long ignoredAssignmentId) {

        if (candidateTimeSlot == null || candidateTimeSlot.getDayOfWeek() == null) {
            return 0;
        }

        int count = 0;

        for (TimetableAssignment assignment : assignments) {
            if (ignoredAssignmentId != null && assignment.getId().equals(ignoredAssignmentId)) {
                continue;
            }

            if (assignment.getCourse() == null
                    || assignment.getTimeSlot() == null
                    || assignment.getAssignmentType() == null) {
                continue;
            }

            TimeSlot existingSlot = assignment.getTimeSlot();

            if (existingSlot.getDayOfWeek() == null) {
                continue;
            }

            boolean sameStudyYear = assignment.getCourse().getStudyYear() == course.getStudyYear();
            boolean sameDay = existingSlot.getDayOfWeek().equals(candidateTimeSlot.getDayOfWeek());
            boolean isLecture = assignment.getAssignmentType() == TimetableAssignment.AssignmentType.LECTURE;

            if (sameStudyYear && sameDay && isLecture) {
                count++;
            }
        }

        return count;
    }


    @GetMapping("/{id}/progress")
    public ResponseEntity<?> getProgress(@PathVariable Long id) {
    var timetableOpt = timetableRepo.findById(id);

    if (timetableOpt.isEmpty()) {
        return ResponseEntity.notFound().build();
    }

    Timetable timetable = timetableOpt.get();
    List<TimetableAssignment> assignments = assignmentRepo.findByTimetableId(id);

    List<Course> relevantCourses = courseRepo.findAll()
            .stream()
            .filter(course -> isCourseRelevantForTimetable(course, timetable))
            .toList();

    // Για πρόγραμμα εξεταστικής:
    // Δεν μετράμε ώρες θεωρίας/φροντιστηρίου/εργαστηρίου.
    // Ένα μάθημα θεωρείται ολοκληρωμένο όταν έχει τουλάχιστον μία ανάθεση τύπου EXAM.
    if (isExamTimetable(timetable)) {
        Set<Long> coursesWithExam = assignments.stream()
                .filter(assignment -> assignment.getCourse() != null)
                .filter(assignment -> assignment.getAssignmentType() == TimetableAssignment.AssignmentType.EXAM)
                .map(assignment -> assignment.getCourse().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int totalCourses = relevantCourses.size();
        int completedCourses = 0;

        List<Map<String, Object>> missingCourses = new ArrayList<>();

        for (Course course : relevantCourses) {
            boolean hasExam = course.getId() != null && coursesWithExam.contains(course.getId());

            if (hasExam) {
                completedCourses++;
            } else {
                Map<String, Object> missing = new LinkedHashMap<>();
                missing.put("courseId", course.getId());
                missing.put("code", course.getCode());
                missing.put("name", course.getName());
                missing.put("semester", course.getSemester());
                missing.put("studyYear", course.getStudyYear());
                missing.put("courseType", course.getCourseType() != null ? course.getCourseType().name() : null);
                missing.put("missingReason", "Δεν έχει τοποθετηθεί εξέταση για το μάθημα.");

                missingCourses.add(missing);
            }
        }

        int percentage = totalCourses == 0
                ? 0
                : (int) Math.round((completedCourses * 100.0) / totalCourses);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timetableId", timetable.getId());
        result.put("timetableName", timetable.getName());
        result.put("timetableType", timetable.getTimetableType() != null ? timetable.getTimetableType().name() : null);
        result.put("semesterType", timetable.getSemesterType() != null ? timetable.getSemesterType().name() : null);
        result.put("totalCourses", totalCourses);
        result.put("completedCourses", completedCourses);
        result.put("totalRequiredExams", totalCourses);
        result.put("placedExams", coursesWithExam.size());
        result.put("percentage", percentage);
        result.put("missingCourses", missingCourses);

        return ResponseEntity.ok(result);
    }

    // Για πρόγραμμα εξαμήνου:
    // Μετράμε τις απαιτούμενες και τοποθετημένες ώρες θεωρίας/φροντιστηρίου/εργαστηρίου.
    int totalRequiredHours = 0;
    int totalPlacedHours = assignments.size();
    int completedCourses = 0;

    List<Map<String, Object>> missingCourses = new ArrayList<>();

    for (Course course : relevantCourses) {
        int requiredLecture = course.getLectureHours();
        int requiredTutorial = course.getTutorialHours();
        int requiredLab = course.getLabHours();

        int placedLecture = countPlacedHoursForCourseAndType(
                assignments, course, TimetableAssignment.AssignmentType.LECTURE
        );
        int placedTutorial = countPlacedHoursForCourseAndType(
                assignments, course, TimetableAssignment.AssignmentType.TUTORIAL
        );
        int placedLab = countPlacedHoursForCourseAndType(
                assignments, course, TimetableAssignment.AssignmentType.LAB
        );

        int requiredTotal = requiredLecture + requiredTutorial + requiredLab;
        int placedTotal = placedLecture + placedTutorial + placedLab;

        totalRequiredHours += requiredTotal;

        boolean lectureOk = placedLecture >= requiredLecture;
        boolean tutorialOk = placedTutorial >= requiredTutorial;
        boolean labOk = placedLab >= requiredLab;

        if (lectureOk && tutorialOk && labOk) {
            completedCourses++;
        } else {
            Map<String, Object> missing = new LinkedHashMap<>();
            missing.put("courseId", course.getId());
            missing.put("code", course.getCode());
            missing.put("name", course.getName());
            missing.put("semester", course.getSemester());
            missing.put("studyYear", course.getStudyYear());
            missing.put("courseType", course.getCourseType() != null ? course.getCourseType().name() : null);
            missing.put("requiredLecture", requiredLecture);
            missing.put("placedLecture", placedLecture);
            missing.put("requiredTutorial", requiredTutorial);
            missing.put("placedTutorial", placedTutorial);
            missing.put("requiredLab", requiredLab);
            missing.put("placedLab", placedLab);
            missing.put("requiredTotal", requiredTotal);
            missing.put("placedTotal", placedTotal);
            missing.put("missingHours", Math.max(0, requiredTotal - placedTotal));

            missingCourses.add(missing);
        }
    }

    int percentage = totalRequiredHours == 0
            ? 0
            : (int) Math.round((Math.min(totalPlacedHours, totalRequiredHours) * 100.0) / totalRequiredHours);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("timetableId", timetable.getId());
    result.put("timetableName", timetable.getName());
    result.put("timetableType", timetable.getTimetableType() != null ? timetable.getTimetableType().name() : null);
    result.put("semesterType", timetable.getSemesterType() != null ? timetable.getSemesterType().name() : null);
    result.put("totalCourses", relevantCourses.size());
    result.put("completedCourses", completedCourses);
    result.put("totalRequiredHours", totalRequiredHours);
    result.put("placedHours", totalPlacedHours);
    result.put("percentage", percentage);
    result.put("missingCourses", missingCourses);

    return ResponseEntity.ok(result);
}


    @GetMapping("/{id}/validation")
    public ResponseEntity<?> validateTimetableReport(@PathVariable Long id) {
        var timetableOpt = timetableRepo.findById(id);

        if (timetableOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Timetable timetable = timetableOpt.get();
        List<TimetableAssignment> assignments = assignmentRepo.findByTimetableId(id);

        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();

        boolean examTimetable = isExamTimetable(timetable);

        // 1. Έλεγχος κάθε υπάρχουσας ανάθεσης
        for (TimetableAssignment assignment : assignments) {
            Course course = assignment.getCourse();
            Room room = assignment.getRoom();
            TimeSlot timeSlot = assignment.getTimeSlot();
            TimetableAssignment.AssignmentType assignmentType = assignment.getAssignmentType();

            if (course == null || room == null || timeSlot == null || assignmentType == null) {
                errors.add(validationIssue(
                        "ERROR",
                        "INVALID_ASSIGNMENT",
                        "Υπάρχει ανάθεση με ελλιπή δεδομένα.",
                        assignment.getId()
                ));
                continue;
            }

            // Χειμερινό / εαρινό mismatch
            if (timetable.getSemesterType() != null && course.getSemesterType() != null) {
                if (!timetable.getSemesterType().name().equals("SEPTEMBER")
        && !course.getSemesterType().name().equals("BOTH")
        && !timetable.getSemesterType().name().equals(course.getSemesterType().name())) {
                    errors.add(validationIssue(
                            "ERROR",
                            "SEMESTER_MISMATCH",
                            "Το μάθημα " + course.getName() + " δεν ανήκει στο εξάμηνο του προγράμματος.",
                            assignment.getId()
                    ));
                }
            }

            // Εργαστήριο σε μη εργαστηριακή αίθουσα
            if (assignmentType == TimetableAssignment.AssignmentType.LAB
                    && room.getRoomType() != Room.RoomType.LAB) {
                errors.add(validationIssue(
                        "ERROR",
                        "LAB_ROOM_REQUIRED",
                        "Το εργαστήριο " + course.getName() + " πρέπει να βρίσκεται σε εργαστηριακή αίθουσα.",
                        assignment.getId()
                ));
            }

            // 1ο έτος μόνο στο Γ για θεωρία/φροντιστήριο
            if ((assignmentType == TimetableAssignment.AssignmentType.LECTURE
                    || assignmentType == TimetableAssignment.AssignmentType.TUTORIAL)
                    && course.getStudyYear() == 1
                    && !"Γ".equals(room.getCode())) {
                errors.add(validationIssue(
                        "ERROR",
                        "FIRST_YEAR_ROOM",
                        "Το μάθημα 1ου έτους " + course.getName() + " πρέπει να μπει στο Αμφιθέατρο Γ.",
                        assignment.getId()
                ));
            }

            // Υποχρεωτικά μόνο Β ή Γ για θεωρία/φροντιστήριο
            if ((assignmentType == TimetableAssignment.AssignmentType.LECTURE
                    || assignmentType == TimetableAssignment.AssignmentType.TUTORIAL)
                    && course.getCourseType() == Course.CourseType.REQUIRED) {

                boolean allowedRequiredRoom = "Β".equals(room.getCode()) || "Γ".equals(room.getCode());

                if (!allowedRequiredRoom) {
                    errors.add(validationIssue(
                            "ERROR",
                            "REQUIRED_ROOM",
                            "Το υποχρεωτικό μάθημα " + course.getName() + " πρέπει να βρίσκεται σε αίθουσα Β ή Γ.",
                            assignment.getId()
                    ));
                }
            }
        }

        // 2. Έλεγχος συγκρούσεων αίθουσας και υποχρεωτικών ίδιου έτους
        for (int i = 0; i < assignments.size(); i++) {
            TimetableAssignment a = assignments.get(i);

            for (int j = i + 1; j < assignments.size(); j++) {
                TimetableAssignment b = assignments.get(j);

                if (a.getRoom() != null && b.getRoom() != null
                        && a.getTimeSlot() != null && b.getTimeSlot() != null
                        && a.getCourse() != null && b.getCourse() != null
                        && a.getRoom().getId().equals(b.getRoom().getId())
                        && a.getTimeSlot().getId().equals(b.getTimeSlot().getId())) {

                    errors.add(validationIssue(
                            "ERROR",
                            "ROOM_CONFLICT",
                            "Η αίθουσα " + a.getRoom().getCode()
                                    + " έχει δύο μαθήματα την ίδια ώρα: "
                                    + a.getCourse().getName() + " και " + b.getCourse().getName() + ".",
                            a.getId()
                    ));
                }


                if (a.getCourse() != null && b.getCourse() != null
                        && a.getTimeSlot() != null && b.getTimeSlot() != null
                        && a.getTimeSlot().getId().equals(b.getTimeSlot().getId())) {

                    if (a.getCourse().getId() != null
                            && b.getCourse().getId() != null
                            && a.getCourse().getId().equals(b.getCourse().getId())) {
                        errors.add(validationIssue(
                                "ERROR",
                                "SAME_COURSE_SAME_SLOT",
                                "Το ίδιο μάθημα έχει τοποθετηθεί δύο φορές στην ίδια ώρα: "
                                        + a.getCourse().getName() + ".",
                                a.getId()
                        ));
                    }

                    List<String> commonTeacherNames = findCommonTeacherNamesSmart(a.getCourse(), b.getCourse());

                    if (!commonTeacherNames.isEmpty()) {
                        String teacherText = String.join(", ", commonTeacherNames);

                        errors.add(validationIssue(
                                "ERROR",
                                "TEACHER_CONFLICT",
                                "Σύγκρουση διδάσκοντα: " + teacherText
                                        + " έχει δύο μαθήματα την ίδια ώρα: "
                                        + a.getCourse().getName() + " και "
                                        + b.getCourse().getName() + ".",
                                a.getId()
                        ));
                    }
                }

                if (a.getCourse() != null && b.getCourse() != null
        && a.getTimeSlot() != null && b.getTimeSlot() != null
        && a.getCourse().getCourseType() == Course.CourseType.REQUIRED
        && b.getCourse().getCourseType() == Course.CourseType.REQUIRED
        && a.getCourse().getStudyYear() == b.getCourse().getStudyYear()
        && !a.getCourse().getId().equals(b.getCourse().getId())) {

    boolean sameSlot = a.getTimeSlot().getId().equals(b.getTimeSlot().getId());

    boolean bothExamAssignments =
            a.getAssignmentType() == TimetableAssignment.AssignmentType.EXAM
                    && b.getAssignmentType() == TimetableAssignment.AssignmentType.EXAM;

    boolean sameExamDate = examTimetable
            && bothExamAssignments
            && sameCalendarDay(a.getTimeSlot(), b.getTimeSlot());

    if (examTimetable && sameExamDate) {
        errors.add(validationIssue(
                "ERROR",
                "REQUIRED_YEAR_EXAM_SAME_DATE",
                "Σύγκρουση υποχρεωτικών εξετάσεων ίδιου έτους την ίδια ημερομηνία: "
                        + a.getCourse().getName() + " και " + b.getCourse().getName() + ".",
                a.getId()
        ));
    } else if (!examTimetable && sameSlot) {
        errors.add(validationIssue(
                "ERROR",
                "REQUIRED_YEAR_CONFLICT",
                "Σύγκρουση υποχρεωτικών μαθημάτων ίδιου έτους την ίδια ώρα: "
                        + a.getCourse().getName() + " και " + b.getCourse().getName() + ".",
                a.getId()
        ));
    		    }
		}
            }
        }

if (!examTimetable) {
        // 2β. Έλεγχος ορίου 6 ωρών θεωρίας ανά ημέρα και έτος
        Map<Integer, Map<DayOfWeek, Integer>> lectureHoursPerYearDay = new LinkedHashMap<>();

        for (TimetableAssignment assignment : assignments) {
            if (assignment.getCourse() == null
                    || assignment.getTimeSlot() == null
                    || assignment.getAssignmentType() == null) {
                continue;
            }

            if (assignment.getAssignmentType() != TimetableAssignment.AssignmentType.LECTURE) {
                continue;
            }

	if (assignment.getCourse().getCourseType() != Course.CourseType.REQUIRED) {
    	continue;
	}

            if (assignment.getTimeSlot().getDayOfWeek() == null) {
                continue;
            }

            int studyYear = assignment.getCourse().getStudyYear();
            DayOfWeek day = assignment.getTimeSlot().getDayOfWeek();

            lectureHoursPerYearDay
                    .computeIfAbsent(studyYear, key -> new LinkedHashMap<>())
                    .put(day, lectureHoursPerYearDay
                            .computeIfAbsent(studyYear, key -> new LinkedHashMap<>())
                            .getOrDefault(day, 0) + 1);
        }

        for (Map.Entry<Integer, Map<DayOfWeek, Integer>> yearEntry : lectureHoursPerYearDay.entrySet()) {
            int studyYear = yearEntry.getKey();

            for (Map.Entry<DayOfWeek, Integer> dayEntry : yearEntry.getValue().entrySet()) {
                DayOfWeek day = dayEntry.getKey();
                int lectureHours = dayEntry.getValue();

                if (lectureHours > 6) {
                    errors.add(validationIssue(
                            "ERROR",
                            "DAILY_LECTURE_LIMIT",
                            "Το " + studyYear + "ο έτος έχει " + lectureHours
                                    + " ώρες θεωρίας την ημέρα " + greekDay(day.name())
                                    + ". Το μέγιστο επιτρεπτό είναι 6.",
                            null
                    ));
                }
            }
        }

        // 2γ. Έλεγχος ελεύθερης ώρας φαγητού 12:00-15:00 για τα 3 πρώτα έτη
        for (int year = 1; year <= 3; year++) {
            for (DayOfWeek day : List.of(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY)) {

                boolean hasFreeLunchHour = hasFreeLunchHour(assignments, year, day);

                if (!hasFreeLunchHour) {
                    errors.add(validationIssue(
                            "ERROR",
                            "LUNCH_BREAK_REQUIRED",
                            "Το " + year + "ο έτος δεν έχει ελεύθερη ώρα για φαγητό μεταξύ 12:00-15:00 την ημέρα "
                                    + greekDay(day.name()) + ".",
                            null
                    ));
                }
            }
        }
}

        // 3. Έλεγχος πληρότητας προγράμματος
List<Course> relevantCourses = courseRepo.findAll()
        .stream()
        .filter(course -> isCourseRelevantForTimetable(course, timetable))
        .toList();

if (examTimetable) {
    Set<Long> coursesWithExam = assignments.stream()
            .filter(assignment -> assignment.getCourse() != null)
            .filter(assignment -> assignment.getAssignmentType() == TimetableAssignment.AssignmentType.EXAM)
            .map(assignment -> assignment.getCourse().getId())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    for (Course course : relevantCourses) {
        if (course.getId() == null || !coursesWithExam.contains(course.getId())) {
            warnings.add(validationIssue(
                    "WARNING",
                    "MISSING_EXAM",
                    "Το μάθημα " + course.getName() + " δεν έχει τοποθετημένη εξέταση.",
                    course.getId()
            ));
        }
    }
} else {
    for (Course course : relevantCourses) {
        checkCourseHours(assignments, course, TimetableAssignment.AssignmentType.LECTURE, course.getLectureHours(), warnings, errors);
        checkCourseHours(assignments, course, TimetableAssignment.AssignmentType.TUTORIAL, course.getTutorialHours(), warnings, errors);
        checkCourseHours(assignments, course, TimetableAssignment.AssignmentType.LAB, course.getLabHours(), warnings, errors);
    }
}

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", errors.isEmpty());
        result.put("errorCount", errors.size());
        result.put("warningCount", warnings.size());
        result.put("errors", errors);
        result.put("warnings", warnings);

        return ResponseEntity.ok(result);
    }

private boolean isExamTimetable(Timetable timetable) {
    return timetable != null
            && timetable.getTimetableType() != null
            && "EXAM".equals(timetable.getTimetableType().name());
}

    private boolean isCourseRelevantForTimetable(Course course, Timetable timetable) {
    if (course == null || timetable == null) return false;
    if (course.getSemesterType() == null || timetable.getSemesterType() == null) return true;
    // BOTH courses ανήκουν παντού
    if (course.getSemesterType().name().equals("BOTH")) return true;
    // SEPTEMBER timetable περιλαμβάνει όλα τα μαθήματα
    if (timetable.getSemesterType().name().equals("SEPTEMBER")) return true;
    return course.getSemesterType().name().equals(timetable.getSemesterType().name());
    }

    private int countPlacedHoursForCourseAndType(
            List<TimetableAssignment> assignments,
            Course course,
            TimetableAssignment.AssignmentType assignmentType) {

        int count = 0;

        for (TimetableAssignment assignment : assignments) {
            if (assignment.getCourse() == null || assignment.getAssignmentType() == null) {
                continue;
            }

            boolean sameCourse = assignment.getCourse().getId().equals(course.getId());
            boolean sameType = assignment.getAssignmentType() == assignmentType;

            if (sameCourse && sameType) {
                count++;
            }
        }

        return count;
    }

    private void checkCourseHours(
            List<TimetableAssignment> assignments,
            Course course,
            TimetableAssignment.AssignmentType assignmentType,
            int requiredHours,
            List<Map<String, Object>> warnings,
            List<Map<String, Object>> errors) {

        int placedHours = countPlacedHoursForCourseAndType(assignments, course, assignmentType);

        if (requiredHours == 0 && placedHours > 0) {
            errors.add(validationIssue(
                    "ERROR",
                    "UNNECESSARY_HOURS",
                    "Το μάθημα " + course.getName()
                            + " έχει τοποθετημένες ώρες " + assignmentTypeLabel(assignmentType)
                            + ", ενώ δεν προβλέπονται.",
                    course.getId()
            ));
            return;
        }

        if (placedHours > requiredHours) {
            errors.add(validationIssue(
                    "ERROR",
                    "TOO_MANY_HOURS",
                    "Το μάθημα " + course.getName()
                            + " έχει περισσότερες ώρες " + assignmentTypeLabel(assignmentType)
                            + " από όσες προβλέπονται: " + placedHours + "/" + requiredHours + ".",
                    course.getId()
            ));
            return;
        }

        if (placedHours < requiredHours) {
            warnings.add(validationIssue(
                    "WARNING",
                    "MISSING_HOURS",
                    "Το μάθημα " + course.getName()
                            + " έχει έλλειψη ωρών " + assignmentTypeLabel(assignmentType)
                            + ": " + placedHours + "/" + requiredHours + ".",
                    course.getId()
            ));
        }
    }

    private Map<String, Object> validationIssue(
            String severity,
            String code,
            String message,
            Long referenceId) {

        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("severity", severity);
        issue.put("code", code);
        issue.put("message", message);
        issue.put("referenceId", referenceId);

        return issue;
    }

@Transactional
@PutMapping("/assignments/{assignmentId}/move")
    public ResponseEntity<?> moveAssignment(
            @PathVariable Long assignmentId,
            @RequestBody Map<String, Object> body) {

        var assignmentOpt = assignmentRepo.findById(assignmentId);

        if (assignmentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TimetableAssignment assignment = assignmentOpt.get();

        Room targetRoom = assignment.getRoom();
        TimeSlot targetTimeSlot = assignment.getTimeSlot();

        if (body.containsKey("timeSlotId")) {
            var timeSlotOpt = timeSlotRepo.findById(((Number) body.get("timeSlotId")).longValue());
            if (timeSlotOpt.isEmpty()) {
                return badRequest("\u0394\u03b5\u03bd \u03b2\u03c1\u03ad\u03b8\u03b7\u03ba\u03b5 \u03b7 \u03bd\u03ad\u03b1 \u03c7\u03c1\u03bf\u03bd\u03bf\u03b8\u03c5\u03c1\u03af\u03b4\u03b1.");
            }
            targetTimeSlot = timeSlotOpt.get();
        }

        if (body.containsKey("roomId")) {
            var roomOpt = roomRepo.findById(((Number) body.get("roomId")).longValue());
            if (roomOpt.isEmpty()) {
                return badRequest("\u0394\u03b5\u03bd \u03b2\u03c1\u03ad\u03b8\u03b7\u03ba\u03b5 \u03b7 \u03bd\u03ad\u03b1 \u03b1\u03af\u03b8\u03bf\u03c5\u03c3\u03b1.");
            }
            targetRoom = roomOpt.get();
        }

        ResponseEntity<?> validationError = validateAssignment(
                assignment.getTimetable(),
                assignment.getCourse(),
                targetRoom,
                targetTimeSlot,
                assignment.getAssignmentType(),
                assignment.getId()
        );

        if (validationError != null) {
            return validationError;
        }

        assignment.setRoom(targetRoom);
        assignment.setTimeSlot(targetTimeSlot);
        assignment.setManuallyAssigned(true);

        assignmentRepo.save(assignment);

        var refreshed = assignmentRepo.findById(assignment.getId());

        if (refreshed.isEmpty()) {
            return badRequest("\u03a3\u03c6\u03ac\u03bb\u03bc\u03b1 \u03ba\u03b1\u03c4\u03ac \u03c4\u03b7\u03bd \u03b1\u03c0\u03bf\u03b8\u03ae\u03ba\u03b5\u03c5\u03c3\u03b7.");
        }

        return ResponseEntity.ok(assignmentToDto(refreshed.get()));
    }

    // =========================================================
    // VALIDATION ΓΙΑ ΠΡΟΣΘΗΚΗ / ΜΕΤΑΚΙΝΗΣΗ ΜΙΑΣ ΑΝΑΘΕΣΗΣ
    // =========================================================

    private ResponseEntity<?> validateAssignment(
            Timetable timetable,
            Course course,
            Room room,
            TimeSlot timeSlot,
            TimetableAssignment.AssignmentType assignmentType) {
        return validateAssignment(timetable, course, room, timeSlot, assignmentType, null);
    }

    private ResponseEntity<?> validateAssignment(
        Timetable timetable,
        Course course,
        Room room,
        TimeSlot timeSlot,
        TimetableAssignment.AssignmentType assignmentType,
        Long ignoredAssignmentId) {

    if (timetable == null || course == null || room == null || timeSlot == null || assignmentType == null) {
        return badRequest("Η ανάθεση έχει ελλιπή δεδομένα.");
    }

    boolean examTimetable = isExamTimetable(timetable);
    boolean examAssignment = assignmentType == TimetableAssignment.AssignmentType.EXAM;

    if (examTimetable && !examAssignment) {
        return badRequest("Σε πρόγραμμα εξεταστικής επιτρέπονται μόνο αναθέσεις τύπου EXAM.");
    }

    if (!examTimetable && examAssignment) {
        return badRequest("Ανάθεση τύπου EXAM επιτρέπεται μόνο σε πρόγραμμα εξεταστικής.");
    }

    if (examTimetable) {
        if (timeSlot.getSlotType() != TimeSlot.SlotType.EXAM) {
            return badRequest("Η εξέταση πρέπει να τοποθετηθεί σε χρονοθυρίδα τύπου EXAM.");
        }

        if (timeSlot.getSpecificDate() == null) {
            return badRequest("Η χρονοθυρίδα εξέτασης πρέπει να έχει συγκεκριμένη ημερομηνία.");
        }

        if (timetable.getStartDate() != null
                && timeSlot.getSpecificDate().isBefore(timetable.getStartDate())) {
            return badRequest("Η ημερομηνία της εξέτασης είναι πριν από την έναρξη της εξεταστικής.");
        }

        if (timetable.getEndDate() != null
                && timeSlot.getSpecificDate().isAfter(timetable.getEndDate())) {
            return badRequest("Η ημερομηνία της εξέτασης είναι μετά από τη λήξη της εξεταστικής.");
        }
    } else {
        if (timeSlot.getSlotType() == TimeSlot.SlotType.EXAM) {
            return badRequest("Χρονοθυρίδα τύπου EXAM δεν μπορεί να χρησιμοποιηθεί σε πρόγραμμα εξαμήνου.");
        }
    }

    if (!examAssignment) {
        if (assignmentType == TimetableAssignment.AssignmentType.LECTURE
                && autoSafeHours(course.getLectureHours()) <= 0) {
            return badRequest("Το μάθημα δεν έχει ώρες θεωρίας.");
        }

        if (assignmentType == TimetableAssignment.AssignmentType.TUTORIAL
                && autoSafeHours(course.getTutorialHours()) <= 0) {
            return badRequest("Το μάθημα δεν έχει ώρες φροντιστηρίου.");
        }

        if (assignmentType == TimetableAssignment.AssignmentType.LAB
                && autoSafeHours(course.getLabHours()) <= 0) {
            return badRequest("Το μάθημα δεν έχει εργαστηριακές ώρες.");
        }
    }

    List<TimetableAssignment> existingAssignments = assignmentRepo.findByTimetableId(timetable.getId());

if (examTimetable
        && examAssignment
        && course.getCourseType() == Course.CourseType.REQUIRED) {

    for (TimetableAssignment existing : existingAssignments) {
        if (ignoredAssignmentId != null && existing.getId().equals(ignoredAssignmentId)) {
            continue;
        }

        if (existing.getCourse() == null
                || existing.getTimeSlot() == null
                || existing.getAssignmentType() == null) {
            continue;
        }

        if (existing.getAssignmentType() != TimetableAssignment.AssignmentType.EXAM) {
            continue;
        }

        Course existingCourse = existing.getCourse();

        boolean otherRequired = existingCourse.getCourseType() == Course.CourseType.REQUIRED;
        boolean differentCourse = !existingCourse.getId().equals(course.getId());
        boolean sameStudyYear = existingCourse.getStudyYear() == course.getStudyYear();
        boolean sameDate = sameCalendarDay(existing.getTimeSlot(), timeSlot);

        if (otherRequired && differentCourse && sameStudyYear && sameDate) {
            return badRequest("Δεν επιτρέπεται η τοποθέτηση δύο υποχρεωτικών εξετάσεων του ίδιου έτους την ίδια ημερομηνία. "
                    + "Υπάρχει ήδη η εξέταση του μαθήματος "
                    + existingCourse.getName()
                    + " την ίδια ημερομηνία.");
        }
    }
}

    int alreadyPlacedHours = 0;

    for (TimetableAssignment existing : existingAssignments) {
        if (ignoredAssignmentId != null && Objects.equals(existing.getId(), ignoredAssignmentId)) {
            continue;
        }

        if (existing.getCourse() == null || existing.getAssignmentType() == null) {
            continue;
        }

        boolean sameCourse = Objects.equals(existing.getCourse().getId(), course.getId());
        boolean sameAssignmentType = existing.getAssignmentType() == assignmentType;

        if (sameCourse && sameAssignmentType) {
            alreadyPlacedHours++;
        }
    }

    int requiredHours = getRequiredHoursForAssignmentType(course, assignmentType);

    if (alreadyPlacedHours >= requiredHours) {
        if (examAssignment) {
            return badRequest("Το μάθημα " + course.getName() + " έχει ήδη τοποθετημένη εξέταση.");
        }

        return badRequest("Δεν μπορείς να προσθέσεις άλλη ώρα "
                + assignmentTypeLabel(assignmentType)
                + " για το μάθημα " + course.getName()
                + ". Έχουν ήδη τοποθετηθεί " + alreadyPlacedHours
                + "/" + requiredHours + " ώρες.");
    }

    if (assignmentType == TimetableAssignment.AssignmentType.LAB
            && room.getRoomType() != Room.RoomType.LAB) {
        return badRequest("Τα εργαστήρια πρέπει να τοποθετούνται σε εργαστηριακή αίθουσα.");
    }

    if (timetable.getSemesterType() != null && course.getSemesterType() != null) {
        if (!timetable.getSemesterType().name().equals("SEPTEMBER")
                && !course.getSemesterType().name().equals("BOTH")
                && !timetable.getSemesterType().name().equals(course.getSemesterType().name())) {

            String timetableSemester = timetable.getSemesterType() == Timetable.SemesterType.FALL
                    ? "χειμερινό"
                    : "εαρινό";

            String courseSemester = course.getSemesterType() == Course.SemesterType.FALL
                    ? "χειμερινό"
                    : "εαρινό";

            return badRequest("Το πρόγραμμα είναι " + timetableSemester
                    + ", αλλά το μάθημα είναι " + courseSemester + ".");
        }
    }

    for (TimetableAssignment existing : existingAssignments) {
        if (ignoredAssignmentId != null && Objects.equals(existing.getId(), ignoredAssignmentId)) {
            continue;
        }

        boolean sameRoom = existing.getRoom() != null
                && Objects.equals(existing.getRoom().getId(), room.getId());

        boolean sameSlot = existing.getTimeSlot() != null
                && Objects.equals(existing.getTimeSlot().getId(), timeSlot.getId());

        if (sameRoom && sameSlot) {
            String existingCourseName = existing.getCourse() != null
                    ? existing.getCourse().getName()
                    : "άγνωστο μάθημα";

            return badRequest("Η αίθουσα " + room.getCode()
                    + " είναι ήδη κατειλημμένη σε αυτή την ώρα από το μάθημα "
                    + existingCourseName + ".");
        }

        if (sameSlot
                && existing.getCourse() != null
                && existing.getCourse().getId() != null
                && course.getId() != null
                && Objects.equals(existing.getCourse().getId(), course.getId())) {
            return badRequest("Το ίδιο μάθημα υπάρχει ήδη στην ίδια χρονοθυρίδα.");
        }

        if (sameSlot && existing.getCourse() != null) {
            List<String> commonTeacherNames = findCommonTeacherNamesSmart(course, existing.getCourse());

            if (!commonTeacherNames.isEmpty()) {
                String teacherText = String.join(", ", commonTeacherNames);

                return badRequest("Σύγκρουση διδάσκοντα: " + teacherText
                        + " έχει ήδη μάθημα την ίδια ώρα στο μάθημα "
                        + existing.getCourse().getCode() + " - "
                        + existing.getCourse().getName() + ".");
            }
        }
    }

    if (!examTimetable) {
        if (assignmentType == TimetableAssignment.AssignmentType.LECTURE
                || assignmentType == TimetableAssignment.AssignmentType.TUTORIAL) {

            if (course.getStudyYear() == 1 && !"Γ".equals(room.getCode())) {
                return badRequest("Τα μαθήματα 1ου έτους πρέπει να μπαίνουν μόνο στο Αμφιθέατρο Γ.");
            }

            if (course.getCourseType() == Course.CourseType.REQUIRED) {
                boolean allowedRequiredRoom = "Β".equals(room.getCode()) || "Γ".equals(room.getCode());

                if (!allowedRequiredRoom) {
                    return badRequest("Τα υποχρεωτικά μαθήματα θεωρίας/φροντιστηρίου πρέπει να μπαίνουν μόνο στις αίθουσες Β ή Γ.");
                }
            }
        }

        if (course.getCourseType() == Course.CourseType.REQUIRED) {
            for (TimetableAssignment existing : existingAssignments) {
                if (ignoredAssignmentId != null && Objects.equals(existing.getId(), ignoredAssignmentId)) {
                    continue;
                }

                if (existing.getCourse() == null || existing.getTimeSlot() == null) {
                    continue;
                }

                boolean sameSlot = Objects.equals(existing.getTimeSlot().getId(), timeSlot.getId());
                boolean sameStudyYear = existing.getCourse().getStudyYear() == course.getStudyYear();
                boolean existingIsRequired = existing.getCourse().getCourseType() == Course.CourseType.REQUIRED;

                if (sameSlot && sameStudyYear && existingIsRequired) {
                    return badRequest("Υπάρχει ήδη υποχρεωτικό μάθημα του ίδιου έτους σε αυτή την ώρα: "
                            + existing.getCourse().getName() + ".");
                }
            }
        }

        if (assignmentType == TimetableAssignment.AssignmentType.LECTURE) {
            int lectureHoursSameDay = countSameStudyYearLecturesOnSameDay(
                    existingAssignments,
                    course,
                    timeSlot,
                    ignoredAssignmentId
            );

            if (lectureHoursSameDay >= 6) {
                String dayText = timeSlot.getDayOfWeek() != null
                        ? greekDay(timeSlot.getDayOfWeek().name())
                        : "άγνωστη ημέρα";

                return badRequest("Το " + course.getStudyYear()
                        + "ο έτος έχει ήδη 6 ώρες θεωρίας την ημέρα "
                        + dayText
                        + ". Δεν επιτρέπεται νέα ώρα θεωρίας την ίδια ημέρα.");
            }
        }

        if (wouldRemoveLunchBreak(existingAssignments, course, timeSlot, ignoredAssignmentId)) {
            String dayText = timeSlot.getDayOfWeek() != null
                    ? greekDay(timeSlot.getDayOfWeek().name())
                    : "άγνωστη ημέρα";

            return badRequest("Η τοποθέτηση αυτή αφαιρεί την τελευταία ελεύθερη ώρα φαγητού για το "
                    + course.getStudyYear() + "ο έτος μεταξύ 12:00-15:00 την ημέρα "
                    + dayText + ".");
        }
    }

    return null;
}

    // =========================================================
    // SOLVER (Timefold CPSolver)
    // =========================================================

    @PostMapping("/{id}/solve")
    public ResponseEntity<?> solve(@PathVariable Long id,
                                   @RequestParam(defaultValue = "30") int timeLimit) {
        if (!timetableRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            Map<String, Object> result = solverService.solve(id, timeLimit);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================
    // AUTO-SCHEDULE (safe greedy algorithm, limited per run)
    // =========================================================

    @Transactional
    @PostMapping("/{id}/auto-schedule")
    public ResponseEntity<?> autoSchedule(@PathVariable Long id) {
        var timetableOpt = timetableRepo.findById(id);
        if (timetableOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Timetable timetable = timetableOpt.get();

        List<Course> allCourses = courseRepo.findAll().stream()
                .filter(course -> isCourseRelevantForTimetable(course, timetable))
                .filter(course -> autoTotalCourseHours(course) > 0)
                .sorted(courseSchedulingPriority())
                .toList();

        List<Room> rooms = roomRepo.findAll().stream()
                .sorted(Comparator.comparing((Room room) -> normalizeSortKey(room.getCode())))
                .toList();

        List<TimeSlot> slots = timeSlotRepo.findAll().stream()
                .filter(slot -> slot.getSlotType() == null || slot.getSlotType() == TimeSlot.SlotType.SEMESTER)
                .sorted(Comparator
                        .comparingInt((TimeSlot slot) -> dayOrder(slot.getDayOfWeek()))
                        .thenComparing(TimeSlot::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<TimetableAssignment> currentAssignments = new ArrayList<>(assignmentRepo.findByTimetableId(id));
        Map<Long, Set<String>> courseTeacherKeyMap = buildCourseTeacherKeyMap(allCourses);

        int totalPlaced = 0;
        int totalFailed = 0;
        int totalSkipped = 0;

        List<String> log = new ArrayList<>();
        List<Map<String, Object>> placedEntries = new ArrayList<>();
        List<Map<String, Object>> failedEntries = new ArrayList<>();
        Map<String, Integer> globalFailureReasonCounts = new LinkedHashMap<>();

        TimetableAssignment.AssignmentType[] typeOrder = {
                TimetableAssignment.AssignmentType.LECTURE,
                TimetableAssignment.AssignmentType.TUTORIAL,
                TimetableAssignment.AssignmentType.LAB
        };

        for (Course course : allCourses) {
            for (TimetableAssignment.AssignmentType assignmentType : typeOrder) {
                int required = getRequiredHoursForAssignmentType(course, assignmentType);
                if (required <= 0) {
                    continue;
                }

                int alreadyPlaced = countPlacedHoursForCourseAndType(currentAssignments, course, assignmentType);
                int remaining = required - alreadyPlaced;

                if (remaining <= 0) {
                    totalSkipped += required;
                    continue;
                }

                for (int hourIndex = 0; hourIndex < remaining; hourIndex++) {
                    TimeSlot bestSlot = null;
                    Room bestRoom = null;
                    int bestScore = -1;

                    Map<String, Integer> failureReasonCounts = new LinkedHashMap<>();

                    for (TimeSlot slot : slots) {
                        for (Room room : rooms) {
                            List<String> blockingReasons = getBlockingReasonsInMemoryFast(
                                    currentAssignments,
                                    timetable,
                                    course,
                                    room,
                                    slot,
                                    assignmentType,
                                    courseTeacherKeyMap
                            );

                            if (!blockingReasons.isEmpty()) {
                                for (String reason : blockingReasons) {
                                    failureReasonCounts.merge(reason, 1, Integer::sum);
                                }
                                continue;
                            }

                            int score = calculatePlacementScore(course, room, slot, assignmentType, currentAssignments);

                            if (score > bestScore) {
                                bestScore = score;
                                bestSlot = slot;
                                bestRoom = room;
                            }
                        }
                    }

                    if (bestSlot != null && bestRoom != null) {
                        TimetableAssignment assignment = TimetableAssignment.builder()
                                .timetable(timetable)
                                .course(course)
                                .room(bestRoom)
                                .timeSlot(bestSlot)
                                .assignmentType(assignmentType)
                                .isLocked(false)
                                .manuallyAssigned(false)
                                .createdAt(LocalDateTime.now())
                                .build();

                        TimetableAssignment saved = assignmentRepo.save(assignment);
                        currentAssignments.add(saved);
                        totalPlaced++;

                        String successLine = course.getCode() + " " + assignmentTypeLabel(assignmentType)
                                + " -> " + greekDay(bestSlot.getDayOfWeek().name())
                                + " " + bestSlot.getStartTime() + " " + bestRoom.getCode()
                                + " (score: " + bestScore + ")";

                        log.add(successLine);

                        Map<String, Object> placedEntry = new LinkedHashMap<>();
                        placedEntry.put("courseCode", course.getCode());
                        placedEntry.put("courseName", course.getName());
                        placedEntry.put("assignmentType", assignmentTypeLabel(assignmentType));
                        placedEntry.put("day", greekDay(bestSlot.getDayOfWeek().name()));
                        placedEntry.put("startTime", bestSlot.getStartTime().toString());
                        placedEntry.put("roomCode", bestRoom.getCode());
                        placedEntry.put("score", bestScore);
                        placedEntries.add(placedEntry);
                    } else {
                        totalFailed++;

                        List<String> topReasons = summarizeFailureReasons(failureReasonCounts);
                        String mainReason = topReasons.isEmpty()
                                ? "Δεν βρέθηκε έγκυρη θέση"
                                : topReasons.get(0);

                        for (Map.Entry<String, Integer> entry : failureReasonCounts.entrySet()) {
                            globalFailureReasonCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
                        }

                        String failLine = "FAIL: " + course.getCode() + " " + assignmentTypeLabel(assignmentType)
                                + " - " + mainReason;

                        log.add(failLine);

                        Map<String, Object> failedEntry = new LinkedHashMap<>();
                        failedEntry.put("courseCode", course.getCode());
                        failedEntry.put("courseName", course.getName());
                        failedEntry.put("assignmentType", assignmentTypeLabel(assignmentType));
                        failedEntry.put("remainingHourIndex", hourIndex + 1);
                        failedEntry.put("mainReason", mainReason);
                        failedEntry.put("topReasons", topReasons);
                        failedEntries.add(failedEntry);
                    }
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timetableId", timetable.getId());
        result.put("totalPlaced", totalPlaced);
        result.put("totalFailed", totalFailed);
        result.put("totalSkipped", totalSkipped);
        result.put("totalCourses", allCourses.size());
        result.put("log", log);
        result.put("placedEntries", placedEntries);
        result.put("failedEntries", failedEntries);
        result.put("failureSummary", summarizeFailureReasons(globalFailureReasonCounts));
        result.put("topFailureReasons", summarizeFailureReasons(globalFailureReasonCounts));
        result.put("topFailureMessages", summarizeFailureReasons(globalFailureReasonCounts));

        return ResponseEntity.ok(result);
    }

    private Comparator<Course> courseSchedulingPriority() {
        return Comparator
                .comparing((Course course) -> course.getCourseType() != Course.CourseType.REQUIRED ? 1 : 0)
                .thenComparing(course -> course.getStudyYear() != null ? course.getStudyYear() : 99)
                .thenComparing((Course course) -> -safeCourseHours(course))
                .thenComparing(course -> normalizeSortKey(course.getName()))
                .thenComparing(course -> normalizeSortKey(course.getCode()));
    }

    private int safeCourseHours(Course course) {
        int lecture = course.getLectureHours() != null ? course.getLectureHours() : 0;
        int tutorial = course.getTutorialHours() != null ? course.getTutorialHours() : 0;
        int lab = course.getLabHours() != null ? course.getLabHours() : 0;
        return lecture + tutorial + lab;
    }

    private int autoTotalCourseHours(Course course) {
        return autoSafeHours(course.getLectureHours())
                + autoSafeHours(course.getTutorialHours())
                + autoSafeHours(course.getLabHours());
    }

    private int autoSafeHours(Integer hours) {
        return hours == null ? 0 : hours;
    }

    private List<String> summarizeFailureReasons(Map<String, Integer> reasonCounts) {
        return reasonCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(entry -> entry.getKey() + " (" + entry.getValue() + " εμπόδια)")
                .toList();
    }

    private List<String> getBlockingReasonsInMemoryFast(
            List<TimetableAssignment> assignments,
            Timetable timetable,
            Course course,
            Room room,
            TimeSlot timeSlot,
            TimetableAssignment.AssignmentType assignmentType,
            Map<Long, Set<String>> courseTeacherKeyMap) {

        List<String> reasons = new ArrayList<>();

        int lectureHours = autoSafeHours(course.getLectureHours());
        int tutorialHours = autoSafeHours(course.getTutorialHours());
        int labHours = autoSafeHours(course.getLabHours());

        if (assignmentType == TimetableAssignment.AssignmentType.LECTURE && lectureHours <= 0) {
            reasons.add("Το μάθημα δεν απαιτεί Θεωρία");
            return reasons;
        }
        if (assignmentType == TimetableAssignment.AssignmentType.TUTORIAL && tutorialHours <= 0) {
            reasons.add("Το μάθημα δεν απαιτεί Φροντιστήριο");
            return reasons;
        }
        if (assignmentType == TimetableAssignment.AssignmentType.LAB && labHours <= 0) {
            reasons.add("Το μάθημα δεν απαιτεί Εργαστήριο");
            return reasons;
        }

        int alreadyPlaced = countPlacedHoursForCourseAndType(assignments, course, assignmentType);
        int required = getRequiredHoursForAssignmentType(course, assignmentType);
        if (alreadyPlaced >= required) {
            reasons.add("Οι απαιτούμενες ώρες αυτού του τύπου έχουν ήδη καλυφθεί");
            return reasons;
        }

        if (assignmentType == TimetableAssignment.AssignmentType.LAB && room.getRoomType() != Room.RoomType.LAB) {
            reasons.add("Το Εργαστήριο απαιτεί αίθουσα τύπου LAB");
        }

        if (timetable.getSemesterType() != null && course.getSemesterType() != null
                && !timetable.getSemesterType().name().equals(course.getSemesterType().name())) {
            reasons.add("Το μάθημα ανήκει σε άλλο εξάμηνο");
        }

        for (TimetableAssignment existing : assignments) {
            if (existing.getRoom() != null && existing.getTimeSlot() != null
                    && existing.getRoom().getId().equals(room.getId())
                    && existing.getTimeSlot().getId().equals(timeSlot.getId())) {
                reasons.add("Η αίθουσα είναι ήδη κατειλημμένη");
                break;
            }
        }

        for (TimetableAssignment existing : assignments) {
            if (existing.getCourse() != null && existing.getTimeSlot() != null
                    && existing.getCourse().getId().equals(course.getId())
                    && existing.getTimeSlot().getId().equals(timeSlot.getId())) {
                reasons.add("Το ίδιο μάθημα υπάρχει ήδη στην ίδια χρονοθυρίδα");
                break;
            }
        }

        if ((assignmentType == TimetableAssignment.AssignmentType.LECTURE
                || assignmentType == TimetableAssignment.AssignmentType.TUTORIAL)
                && course.getStudyYear() == 1
                && !"Γ".equals(room.getCode())) {
            reasons.add("Τα μαθήματα 1ου έτους τοποθετούνται μόνο στη Γ");
        }

        if ((assignmentType == TimetableAssignment.AssignmentType.LECTURE
                || assignmentType == TimetableAssignment.AssignmentType.TUTORIAL)
                && course.getCourseType() == Course.CourseType.REQUIRED
                && !"Β".equals(room.getCode())
                && !"Γ".equals(room.getCode())) {
            reasons.add("Τα υποχρεωτικά μαθήματα τοποθετούνται μόνο σε Β ή Γ");
        }

        if (course.getCourseType() == Course.CourseType.REQUIRED) {
            for (TimetableAssignment existing : assignments) {
                if (existing.getCourse() != null && existing.getTimeSlot() != null
                        && existing.getTimeSlot().getId().equals(timeSlot.getId())
                        && existing.getCourse().getStudyYear() == course.getStudyYear()
                        && existing.getCourse().getCourseType() == Course.CourseType.REQUIRED
                        && !existing.getCourse().getId().equals(course.getId())) {
                    reasons.add("Υπάρχει σύγκρουση με υποχρεωτικό μάθημα ίδιου έτους");
                    break;
                }
            }
        }

        if (assignmentType == TimetableAssignment.AssignmentType.LECTURE) {
            int lecturesSameDay = 0;
            for (TimetableAssignment existing : assignments) {
                if (existing.getCourse() != null && existing.getTimeSlot() != null
                        && existing.getAssignmentType() == TimetableAssignment.AssignmentType.LECTURE
                        && existing.getCourse().getStudyYear() == course.getStudyYear()
                        && existing.getTimeSlot().getDayOfWeek() != null
                        && existing.getTimeSlot().getDayOfWeek().equals(timeSlot.getDayOfWeek())) {
                    lecturesSameDay++;
                }
            }
            if (lecturesSameDay >= 6) {
                reasons.add("Υπερβαίνεται το όριο 6 ωρών Θεωρίας την ίδια μέρα");
            }
        }

        if (wouldRemoveLunchBreak(assignments, course, timeSlot, null)) {
            reasons.add("Παραβιάζεται το υποχρεωτικό κενό 12:00-15:00");
        }

        for (TimetableAssignment existing : assignments) {
            if (existing.getCourse() != null && existing.getTimeSlot() != null
                    && existing.getTimeSlot().getId().equals(timeSlot.getId())
                    && !existing.getCourse().getId().equals(course.getId())
                    && hasTeacherConflict(courseTeacherKeyMap, existing.getCourse().getId(), course.getId())) {
                reasons.add("Σύγκρουση διδάσκοντα");
                break;
            }
        }

        return reasons;
    }

private Map<Long, Set<String>> buildCourseTeacherKeyMap(List<Course> courses) {
        Map<Long, Set<String>> map = new HashMap<>();

        // First: build from teachersText (always available, no lazy issues)
        for (Course course : courses) {
            if (course == null || course.getId() == null) {
                continue;
            }
            map.computeIfAbsent(course.getId(), key -> new LinkedHashSet<>())
                    .addAll(extractTeacherDisplayByKey(course.getTeachersText()).keySet());
        }

        // Second: build from DB teacher names using a JPQL query (no lazy proxy)
        try {
            for (CourseTeacher relation : courseTeacherRepo.findAll()) {
                if (relation.getCourse() == null || relation.getCourse().getId() == null) {
                    continue;
                }
                if (relation.getTeacher() == null || relation.getTeacher().getId() == null) {
                    continue;
                }

                // Use teacherId to find name from a safe source
                Long teacherId = relation.getTeacher().getId();
                String teacherName = null;
                try {
                    teacherName = relation.getTeacher().getName();
                } catch (Exception e) {
                    // Lazy init failed - skip this relation, teachersText already covers it
                    continue;
                }

                if (teacherName == null || teacherName.isBlank()) {
                    continue;
                }

                String key = teacherKeyFromDisplayName(teacherName);
                if (key.isBlank()) {
                    continue;
                }

                map.computeIfAbsent(relation.getCourse().getId(), ignored -> new LinkedHashSet<>())
                        .add(key);
            }
        } catch (Exception e) {
            // If all DB relations fail, teachersText keys are still in the map
        }

        return map;
    }

    private boolean hasTeacherConflict(Map<Long, Set<String>> courseTeacherKeyMap, Long courseIdA, Long courseIdB) {
        if (courseIdA == null || courseIdB == null) {
            return false;
        }

        Set<String> teachersA = courseTeacherKeyMap.getOrDefault(courseIdA, Collections.emptySet());
        Set<String> teachersB = courseTeacherKeyMap.getOrDefault(courseIdB, Collections.emptySet());

        for (String teacherKey : teachersA) {
            if (teachersB.contains(teacherKey)) {
                return true;
            }
        }

        return false;
    }

    private List<String> findCommonTeacherNamesSmart(Course firstCourse, Course secondCourse) {
        LinkedHashSet<String> result = new LinkedHashSet<>();

        if (firstCourse == null || secondCourse == null) {
            return List.of();
        }

        if (firstCourse.getId() != null && secondCourse.getId() != null) {
            List<String> dbNames = courseTeacherRepo.findCommonTeacherNamesBetweenCourses(
                    firstCourse.getId(),
                    secondCourse.getId()
            );
            if (dbNames != null) {
                for (String name : dbNames) {
                    String cleaned = cleanTeacherDisplayName(name);
                    if (!cleaned.isBlank() && !isGenericPlaceholder(cleaned)) {
    			result.add(cleaned);
		    }
                }
            }
        }

        Map<String, String> firstTeachers = extractTeacherDisplayByKey(firstCourse.getTeachersText());
        Map<String, String> secondTeachers = extractTeacherDisplayByKey(secondCourse.getTeachersText());

        for (Map.Entry<String, String> entry : firstTeachers.entrySet()) {
    if (secondTeachers.containsKey(entry.getKey())
            && !isGenericPlaceholder(entry.getValue())) {
        result.add(chooseBetterTeacherDisplay(entry.getValue(), secondTeachers.get(entry.getKey())));
    }
}

        return sortTeacherDisplayNames(result);
    }

private Map<String, String> extractTeacherDisplayByKey(String teachersText) {
    Map<String, String> result = new LinkedHashMap<>();

    for (String display : splitAndAttachTeacherRoles(teachersText)) {
        String key = teacherKeyFromDisplayName(display);
        if (!key.isBlank()) {
            result.merge(key, display, this::chooseBetterTeacherDisplay);
        }
    }

    return result;
}

private List<String> splitAndAttachTeacherRoles(String teachersText) {
    if (teachersText == null || teachersText.isBlank()) {
        return List.of();
    }

    String cleaned = teachersText
            .replace("\n", ",")
            .replace("\r", ",")
            .replace(";", ",")
            .replace(" και ", ",")
            .replace(" & ", ",");

    String[] parts = cleaned.split(",");
    List<String> result = new ArrayList<>();

    for (String part : parts) {
        String display = cleanTeacherDisplayName(part);
        if (display.isBlank()) {
            continue;
        }

        if (isStandaloneTeacherRole(display)) {
            if (!result.isEmpty()) {
                int last = result.size() - 1;
                result.set(last, appendRoleToTeacher(result.get(last), normalizeStandaloneTeacherRole(display)));
            } else {
                result.add(display);
            }
            continue;
        }

        result.add(display);
    }

    return result;
}

private boolean isStandaloneTeacherRole(String value) {
    String key = normalizeRoleKey(value);

    return key.equals("ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ")
            || key.equals("Η ΚΑΙ ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ")
            || key.equals("ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ Η ΑΑΔΕ")
            || key.equals("ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ ΑΑΔΕ")
            || key.equals("ΕΝΤΕΤΑΛΜΕΝΩΝ ΔΙΔΑΣΚΩΝ Η ΑΑΔΕ")
            || key.equals("ΑΑΔΕ")
            || key.equals("ΕΔΙΠ")
            || key.equals("Ε ΔΙ Π");
}

private String normalizeStandaloneTeacherRole(String value) {
    String key = normalizeRoleKey(value);

    if (key.equals("ΕΔΙΠ") || key.equals("Ε ΔΙ Π")) {
        return "ΕΔΙΠ";
    }

    if (key.equals("Η ΚΑΙ ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ")) {
        return "ή/και Εντεταλμένος Διδάσκων";
    }

    if (key.equals("ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ Η ΑΑΔΕ")
            || key.equals("ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ ΑΑΔΕ")
            || key.equals("ΕΝΤΕΤΑΛΜΕΝΩΝ ΔΙΔΑΣΚΩΝ Η ΑΑΔΕ")) {
        return "Εντεταλμένος Διδάσκων ή ΑΑΔΕ";
    }

    if (key.equals("ΑΑΔΕ")) {
        return "ΑΑΔΕ";
    }

    return "Εντεταλμένος Διδάσκων";
}

private String appendRoleToTeacher(String teacherDisplay, String roleDisplay) {
    if (teacherDisplay == null || teacherDisplay.isBlank()) {
        return roleDisplay == null ? "" : roleDisplay;
    }
    if (roleDisplay == null || roleDisplay.isBlank()) {
        return teacherDisplay;
    }

    int open = teacherDisplay.lastIndexOf('(');
    int close = teacherDisplay.endsWith(")") ? teacherDisplay.length() - 1 : -1;

    if (open >= 0 && close > open) {
        String base = teacherDisplay.substring(0, open).trim();
        String existing = teacherDisplay.substring(open + 1, close).trim();

        LinkedHashSet<String> roles = new LinkedHashSet<>();
        for (String part : existing.split("\\s*,\\s*")) {
            if (!part.isBlank()) {
                roles.add(part.trim());
            }
        }
        roles.add(roleDisplay);

        return base + " (" + String.join(", ", roles) + ")";
    }

    return teacherDisplay + " (" + roleDisplay + ")";
}

private String normalizeRoleKey(String value) {
    if (value == null) {
        return "";
    }

    return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
}

    private String chooseBetterTeacherDisplay(String first, String second) {
        if (first == null || first.isBlank()) {
            return second == null ? "" : second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return second.length() > first.length() ? second : first;
    }

private boolean isGenericPlaceholder(String name) {
    if (name == null || name.isBlank()) return true;
    String upper = name.toUpperCase()
            .replace("Ά","Α").replace("Έ","Ε").replace("Ή","Η")
            .replace("Ί","Ι").replace("Ό","Ο").replace("Ύ","Υ")
            .replace("Ώ","Ω");
    return upper.contains("ΕΝΤΕΤΑΛΜΕΝΟΣ")
        || upper.contains("ΕΝΤΕΤ.")
        || upper.contains("ΑΑΔΕ")
        || upper.contains("ΕΔΙΠ")
        || upper.contains("ENTETAL")
	|| upper.contains("(ΑΑΔΕ)")
	|| upper.contains("( ΑΑΔΕ)")
	|| upper.equals("ΑΑΔΕ");
}

private String cleanTeacherDisplayName(String value) {
    if (value == null) {
        return "";
    }

    return value
            .replace("(Υ)", "")
            .replace("(Θ)", "")
            .replace("(Ε)", "")
            .replace("Ε.ΔΙ.Π.", "ΕΔΙΠ")
            .replace("Ε.ΔΙ.Π", "ΕΔΙΠ")
            .replace("Εντεταλμένος Διδασκων", "Εντεταλμένος Διδάσκων")
            .replace("Εντεταλμένων Διδάσκων", "Εντεταλμένος Διδάσκων")
            .replace("Α. Ηλία (ΕΔΙΠ)", "Α. Ηλίας (ΕΔΙΠ)")
            .replace("Α. Ηλία", "Α. Ηλίας")
            .replace("  ", " ")
            .trim();
}

    private String teacherKeyFromDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isBlank()) {
            return "";
        }

        String[] parts = normalized.split(" ");
        if (parts.length == 1) {
            return parts[0];
        }

        String surname = parts[parts.length - 1];
        String firstToken = parts[0];
        String firstInitial = firstToken.substring(0, 1);

        return surname + "|" + firstInitial;
    }

    private String normalizeSortKey(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .trim();
    }

private String normalizeTeacherRoleKey(String value) {
    if (value == null) {
        return "";
    }

    return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
}

private boolean isGenericTeacherPlaceholder(String value) {
    String normalized = normalizeTeacherRoleKey(value);
    String compact = normalized.replace(" ", "");

    return normalized.contains("ΕΝΤΕΤΑΛΜΕΝΟΣ ΔΙΔΑΣΚΩΝ")
            || compact.equals("ΕΔΙΠ")
            || compact.equals("ΑΑΔΕ");
}

private List<String> sortTeacherDisplayNames(Collection<String> names) {
    return names.stream()
            .filter(Objects::nonNull)
            .map(this::cleanTeacherDisplayName)
            .filter(name -> !name.isBlank())
            .distinct()
            .sorted(
                    Comparator
                            .comparing((String name) -> isGenericTeacherPlaceholder(name) ? 1 : 0)
                            .thenComparing(this::normalizeSortKey)
            )
            .toList();
}

    private String normalizeTeachersTextForDto(String teachersText) {
        List<String> sortedNames = sortTeacherDisplayNames(
                extractTeacherDisplayByKey(teachersText).values()
        );

        return String.join(", ", sortedNames);
    }

    private int dayOrder(DayOfWeek dayOfWeek) {
        if (dayOfWeek == null) return 99;
        if (dayOfWeek == DayOfWeek.MONDAY) return 1;
        if (dayOfWeek == DayOfWeek.TUESDAY) return 2;
        if (dayOfWeek == DayOfWeek.WEDNESDAY) return 3;
        if (dayOfWeek == DayOfWeek.THURSDAY) return 4;
        if (dayOfWeek == DayOfWeek.FRIDAY) return 5;
        if (dayOfWeek == DayOfWeek.SATURDAY) return 6;
        if (dayOfWeek == DayOfWeek.SUNDAY) return 7;
        return 99;
    }

    // =========================================================
    // DTO HELPERS
    // =========================================================

    private Map<String, Object> assignmentToDto(TimetableAssignment assignment) {
    Map<String, Object> dto = new LinkedHashMap<>();

    dto.put("id", assignment.getId());
    dto.put("assignmentType", assignment.getAssignmentType() != null ? assignment.getAssignmentType().name() : null);
    dto.put("manuallyAssigned", assignment.getManuallyAssigned());
    dto.put("isLocked", assignment.getIsLocked());
    dto.put("course", courseToDto(assignment.getCourse()));
    dto.put("room", roomToDto(assignment.getRoom()));
    dto.put("timeSlot", timeSlotToDto(assignment.getTimeSlot()));

    return dto;
}

    private Map<String, Object> courseToDto(Course course) {
        if (course == null) return null;

        Map<String, Object> dto = new LinkedHashMap<>();

        dto.put("id", course.getId());
        dto.put("code", course.getCode());
        dto.put("name", course.getName());
        dto.put("semester", course.getSemester());
        dto.put("studyYear", course.getStudyYear());
        dto.put("courseType", course.getCourseType() != null ? course.getCourseType().name() : null);
        dto.put("lectureHours", course.getLectureHours());
        dto.put("tutorialHours", course.getTutorialHours());
        dto.put("labHours", course.getLabHours());
        dto.put("ects", course.getEcts());
        dto.put("sector", course.getSector());
        dto.put("semesterType", course.getSemesterType() != null ? course.getSemesterType().name() : null);
        dto.put("expectedStudents", course.getExpectedStudents());
        dto.put("teachersText", normalizeTeachersTextForDto(course.getTeachersText()));

        return dto;
    }

    private Map<String, Object> roomToDto(Room room) {
        if (room == null) return null;

        Map<String, Object> dto = new LinkedHashMap<>();

        dto.put("id", room.getId());
        dto.put("code", room.getCode());
        dto.put("name", room.getName());
        dto.put("capacity", room.getCapacity());
        dto.put("roomType", room.getRoomType() != null ? room.getRoomType().name() : null);

        return dto;
    }

    private Map<String, Object> timeSlotToDto(TimeSlot timeSlot) {
    if (timeSlot == null) return null;

    Map<String, Object> dto = new LinkedHashMap<>();

    dto.put("id", timeSlot.getId());
    dto.put("dayOfWeek", timeSlot.getDayOfWeek() != null ? timeSlot.getDayOfWeek().name() : null);
    dto.put("startTime", timeSlot.getStartTime() != null ? timeSlot.getStartTime().toString() : null);
    dto.put("endTime", timeSlot.getEndTime() != null ? timeSlot.getEndTime().toString() : null);
    dto.put("slotType", timeSlot.getSlotType() != null ? timeSlot.getSlotType().name() : null);
    dto.put("specificDate", timeSlot.getSpecificDate() != null ? timeSlot.getSpecificDate().toString() : null);
    dto.put("examPeriodLabel", timeSlot.getExamPeriodLabel());

    return dto;
}

    // =========================================================
    // UTILS
    // =========================================================

private int getRequiredHoursForAssignmentType(
        Course course,
        TimetableAssignment.AssignmentType assignmentType) {

    if (assignmentType == TimetableAssignment.AssignmentType.LECTURE) {
        return autoSafeHours(course.getLectureHours());
    }

    if (assignmentType == TimetableAssignment.AssignmentType.TUTORIAL) {
        return autoSafeHours(course.getTutorialHours());
    }

    if (assignmentType == TimetableAssignment.AssignmentType.LAB) {
    	return autoSafeHours(course.getLabHours());
    }

    if (assignmentType == TimetableAssignment.AssignmentType.EXAM) {
    	return 1;
    }

    return 0;
}

    private String assignmentTypeLabel(TimetableAssignment.AssignmentType assignmentType) {
    if (assignmentType == TimetableAssignment.AssignmentType.LECTURE) {
        return "Θεωρίας";
    }

    if (assignmentType == TimetableAssignment.AssignmentType.TUTORIAL) {
        return "Φροντιστηρίου";
    }

    if (assignmentType == TimetableAssignment.AssignmentType.LAB) {
        return "Εργαστηρίου";
    }

    if (assignmentType == TimetableAssignment.AssignmentType.EXAM) {
        return "Εξέτασης";
    }

    return assignmentType.name();
}

    private int countPlacedHours(
            List<TimetableAssignment> assignments,
            Course course,
            TimetableAssignment.AssignmentType assignmentType) {

        int count = 0;

        for (TimetableAssignment assignment : assignments) {
            if (assignment.getCourse() == null || assignment.getAssignmentType() == null) continue;

            boolean sameCourse = assignment.getCourse().getId().equals(course.getId());
            boolean sameType = assignment.getAssignmentType() == assignmentType;

            if (sameCourse && sameType) {
                count++;
            }
        }

        return count;
    }

    private Map<String, Object> courseProgressDto(Course course, String type, int placed, int required) {
        Map<String, Object> dto = new LinkedHashMap<>();

        dto.put("courseId", course.getId());
        dto.put("code", course.getCode());
        dto.put("name", course.getName());
        dto.put("type", type);
        dto.put("placed", placed);
        dto.put("required", required);

        return dto;
    }

    private boolean hasFreeLunchHour(List<TimetableAssignment> assignments, int studyYear, DayOfWeek day) {
        Set<LocalTime> occupiedLunchStarts = new HashSet<>();

        for (TimetableAssignment a : assignments) {
            if (a.getCourse() == null || a.getTimeSlot() == null) continue;
            if (a.getCourse().getStudyYear() != studyYear) continue;
            if (a.getTimeSlot().getDayOfWeek() != day) continue;

            LocalTime start = a.getTimeSlot().getStartTime();

            if (start != null &&
                    !start.isBefore(LocalTime.of(12, 0)) &&
                    start.isBefore(LocalTime.of(15, 0))) {
                occupiedLunchStarts.add(start);
            }
        }

        return !occupiedLunchStarts.contains(LocalTime.of(12, 0))
                || !occupiedLunchStarts.contains(LocalTime.of(13, 0))
                || !occupiedLunchStarts.contains(LocalTime.of(14, 0));
    }

    private String formatSlot(TimeSlot slot) {
        if (slot == null) return "άγνωστη ώρα";

        String day = slot.getDayOfWeek() != null ? greekDay(slot.getDayOfWeek().name()) : "άγνωστη ημέρα";
        String start = slot.getStartTime() != null ? slot.getStartTime().toString() : "?";
        String end = slot.getEndTime() != null ? slot.getEndTime().toString() : "?";

        return day + " " + start + "-" + end;
    }

    private String greekDay(String day) {
        if ("MONDAY".equals(day)) return "Δευτέρα";
        if ("TUESDAY".equals(day)) return "Τρίτη";
        if ("WEDNESDAY".equals(day)) return "Τετάρτη";
        if ("THURSDAY".equals(day)) return "Πέμπτη";
        if ("FRIDAY".equals(day)) return "Παρασκευή";
        if ("SATURDAY".equals(day)) return "Σάββατο";
        if ("SUNDAY".equals(day)) return "Κυριακή";

        return day;
    }

    private ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    // =========================================================
    // EXAM TIME SLOTS GENERATOR
    // =========================================================

    @Transactional
@PostMapping("/generate-exam-slots")
public ResponseEntity<?> generateExamSlots(@RequestBody Map<String, String> body) {
    String startDateStr = body.get("startDate");
    String endDateStr = body.get("endDate");
    String label = body.getOrDefault("label", "Εξεταστική");

    if (startDateStr == null || startDateStr.isBlank()
            || endDateStr == null || endDateStr.isBlank()) {
        return ResponseEntity.badRequest().body(Map.of("error", "startDate and endDate required"));
    }

    LocalDate startDate;
    LocalDate endDate;

    try {
        startDate = LocalDate.parse(startDateStr);
        endDate = LocalDate.parse(endDateStr);
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Οι ημερομηνίες πρέπει να έχουν μορφή YYYY-MM-DD."));
    }

    if (endDate.isBefore(startDate)) {
        return ResponseEntity.badRequest().body(Map.of("error", "Το endDate δεν μπορεί να είναι πριν από το startDate."));
    }

    int[] examHours = {9, 12, 15, 18};

    List<Map<String, Object>> slots = new ArrayList<>();
    int createdCount = 0;
    int reusedCount = 0;
    int duplicateExistingCount = 0;

    LocalDate current = startDate;

    while (!current.isAfter(endDate)) {
        DayOfWeek dow = current.getDayOfWeek();

        if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
            for (int hour : examHours) {
                LocalTime startTime = LocalTime.of(hour, 0);
                LocalTime endTime = LocalTime.of(hour + 3, 0);

                List<TimeSlot> existingMatches =
                        timeSlotRepo.findBySlotTypeAndSpecificDateAndStartTime(
                                TimeSlot.SlotType.EXAM,
                                current,
                                startTime
                        );

                TimeSlot ts;
                String slotStatus;

                if (!existingMatches.isEmpty()) {
                    ts = existingMatches.get(0);

                    ts.setDayOfWeek(dow);
                    ts.setEndTime(endTime);
                    ts.setSlotType(TimeSlot.SlotType.EXAM);
                    ts.setSpecificDate(current);
                    ts.setExamPeriodLabel(label);

                    ts = timeSlotRepo.save(ts);

                    reusedCount++;
                    duplicateExistingCount += Math.max(0, existingMatches.size() - 1);
                    slotStatus = "EXISTING";
                } else {
                    ts = TimeSlot.builder()
                            .dayOfWeek(dow)
                            .startTime(startTime)
                            .endTime(endTime)
                            .slotType(TimeSlot.SlotType.EXAM)
                            .specificDate(current)
                            .examPeriodLabel(label)
                            .build();

                    ts = timeSlotRepo.save(ts);

                    createdCount++;
                    slotStatus = "CREATED";
                }

                Map<String, Object> info = new LinkedHashMap<>();
                info.put("id", ts.getId());
                info.put("date", current.toString());
                info.put("dayOfWeek", dow.name());
                info.put("startTime", startTime.toString());
                info.put("endTime", endTime.toString());
                info.put("label", label);
                info.put("status", slotStatus);

                slots.add(info);
            }
        }

        current = current.plusDays(1);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("totalSlots", slots.size());
    result.put("createdCount", createdCount);
    result.put("reusedCount", reusedCount);
    result.put("duplicateExistingCount", duplicateExistingCount);
    result.put("startDate", startDateStr);
    result.put("endDate", endDateStr);
    result.put("label", label);
    result.put("slots", slots);

    return ResponseEntity.ok(result);
}

@GetMapping("/{id}/generate-exam-slots")
public ResponseEntity<?> generateExamSlotsForTimetable(@PathVariable Long id) {
    Timetable timetable = timetableRepo.findById(id).orElse(null);
    if (timetable == null) return ResponseEntity.notFound().build();
    if (timetable.getStartDate() == null || timetable.getEndDate() == null) {
        return ResponseEntity.badRequest().body(
            Map.of("error", "Το πρόγραμμα δεν έχει ημερομηνίες έναρξης/λήξης."));
    }
    try {
        solverService.generateExamSlotsForTimetable(timetable);
        return ResponseEntity.ok(Map.of("message", "Exam slots δημιουργήθηκαν επιτυχώς"));
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}

}