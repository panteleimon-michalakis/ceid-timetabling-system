package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.CourseTeacher;
import gr.upatras.ceid.timetable.entity.Teacher;
import gr.upatras.ceid.timetable.repository.CourseRepository;
import gr.upatras.ceid.timetable.repository.CourseTeacherRepository;
import gr.upatras.ceid.timetable.repository.TeacherRepository;
import gr.upatras.ceid.timetable.util.TeacherDisplayText;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.text.Normalizer;
import java.util.*;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    /** Σταθερή σειρά εμφάνισης μαθημάτων: έτος → εξάμηνο → κωδικός (code unique). */
    private static final Sort COURSE_SORT = Sort.by("studyYear", "semester", "code");

    private final CourseRepository courseRepo;
    private final CourseTeacherRepository courseTeacherRepo;
    private final TeacherRepository teacherRepo;

    public CourseController(CourseRepository courseRepo,
                           CourseTeacherRepository courseTeacherRepo,
                           TeacherRepository teacherRepo) {
        this.courseRepo = courseRepo;
        this.courseTeacherRepo = courseTeacherRepo;
        this.teacherRepo = teacherRepo;
    }

    /**
     * Structured αναφορά διδάσκοντα για τα M2M sync endpoints. Το {@code role}
     * είναι προαιρετικό· null/blank → {@link CourseTeacher.Role#PRIMARY}.
     */
    public record TeacherRef(Long teacherId, String role) {}

    @GetMapping
    public List<Map<String, Object>> getAll() {
        return courseRepo.findAll(COURSE_SORT).stream()
                .map(this::courseToDto)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return courseRepo.findById(id)
                .map(course -> ResponseEntity.ok(courseToDto(course)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/semester/{semester}")
    public List<Map<String, Object>> getBySemester(@PathVariable Integer semester) {
        return courseRepo.findBySemesterAndActiveTrue(semester, COURSE_SORT).stream()
                .map(this::courseToDto)
                .toList();
    }

    @GetMapping("/year/{year}")
    public List<Map<String, Object>> getByYear(@PathVariable Integer year) {
        return courseRepo.findByStudyYear(year, COURSE_SORT).stream()
                .map(this::courseToDto)
                .toList();
    }

    @GetMapping("/type/{type}")
    public List<Map<String, Object>> getByType(@PathVariable Course.CourseType type) {
        return courseRepo.findByCourseType(type, COURSE_SORT).stream()
                .map(this::courseToDto)
                .toList();
    }

    @GetMapping("/active")
    public List<Map<String, Object>> getActive() {
        return courseRepo.findByActiveTrue(COURSE_SORT).stream()
                .map(this::courseToDto)
                .toList();
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Course course) {
        course.setTeachersText(normalizeTeachersTextForDto(course.getTeachersText()));
        return courseToDto(courseRepo.save(course));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Course updated) {
        return courseRepo.findById(id).map(course -> {
            course.setCode(updated.getCode());
            course.setName(updated.getName());
            course.setSemester(updated.getSemester());
            course.setStudyYear(updated.getStudyYear());
            course.setCourseType(updated.getCourseType());
            course.setLectureHours(updated.getLectureHours());
            course.setTutorialHours(updated.getTutorialHours());
            course.setLabHours(updated.getLabHours());
            course.setEcts(updated.getEcts());
            course.setSector(updated.getSector());
            course.setExpectedStudents(updated.getExpectedStudents());
            course.setExamDurationMinutes(updated.getExamDurationMinutes());
            course.setNeedsLab(updated.getNeedsLab());
            course.setNeedsProjector(updated.getNeedsProjector());
            course.setSemesterType(updated.getSemesterType());
            course.setTeachersText(normalizeTeachersTextForDto(updated.getTeachersText()));
            course.setActive(updated.getActive());
            course.setVisibleInTimetable(updated.getVisibleInTimetable());
            course.setPreferredExamRooms(updated.getPreferredExamRooms());
            course.setPreferredExamHours(updated.getPreferredExamHours());
            course.setNotes(updated.getNotes());

            Course saved = courseRepo.save(course);
            return ResponseEntity.ok(courseToDto(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (courseRepo.existsById(id)) {
            courseRepo.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ── Teacher M2M sync (course_teachers = source-of-truth, teachersText derived) ──

    /** Οι διδάσκοντες του μαθήματος ως {@code [{teacherId, teacherName, role}]}. */
    @GetMapping("/{id}/teachers")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getCourseTeachers(@PathVariable Long id) {
        if (!courseRepo.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(teacherRelationsToDto(courseTeacherRepo.findByCourseId(id)));
    }

    /**
     * Αντικαθιστά (replace-set) τους διδάσκοντες του μαθήματος από structured input
     * (teacherId + role) και αναπαράγει το {@code teachersText} ως παράγωγο
     * (PRIMARY-first). Το {@code course_teachers} M2M είναι η authoritative πηγή του
     * solver — εδώ απλώς αλλάζει το ΠΟΙΟΣ το γράφει (structured API αντί CSV import).
     */
    @PutMapping("/{id}/teachers")
    @Transactional
    public ResponseEntity<?> setCourseTeachers(@PathVariable Long id,
                                               @RequestBody List<TeacherRef> body) {
        Course course = courseRepo.findById(id).orElse(null);
        if (course == null) return ResponseEntity.notFound().build();

        // desired set (teacherId, role)· validate κάθε teacherId + role
        LinkedHashMap<RelationKey, Teacher> desired = new LinkedHashMap<>();
        if (body != null) {
            for (TeacherRef ref : body) {
                if (ref == null || ref.teacherId() == null) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Απαιτείται teacherId σε κάθε εγγραφή."));
                }
                CourseTeacher.Role role;
                try {
                    role = parseRole(ref.role());
                } catch (IllegalArgumentException ex) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Άγνωστος ρόλος διδάσκοντα: " + ref.role()));
                }
                Teacher teacher = teacherRepo.findById(ref.teacherId()).orElse(null);
                if (teacher == null) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Δεν βρέθηκε διδάσκων με id " + ref.teacherId()));
                }
                desired.put(new RelationKey(ref.teacherId(), role), teacher);
            }
        }

        List<CourseTeacher> existing = courseTeacherRepo.findByCourseId(id);

        // delete όσα existing δεν είναι στο desired
        for (CourseTeacher ct : existing) {
            RelationKey key = new RelationKey(ct.getTeacher().getId(), ct.getRole());
            if (!desired.containsKey(key)) {
                courseTeacherRepo.delete(ct);
            }
        }

        // insert όσα desired λείπουν (σεβασμός unique (course,teacher,role))
        for (Map.Entry<RelationKey, Teacher> e : desired.entrySet()) {
            RelationKey key = e.getKey();
            if (!courseTeacherRepo.existsByCourseIdAndTeacherIdAndRole(id, key.id(), key.role())) {
                courseTeacherRepo.save(CourseTeacher.builder()
                        .course(course).teacher(e.getValue()).role(key.role()).build());
            }
        }

        // regenerate teachersText (derived, PRIMARY-first)
        List<CourseTeacher> updated = courseTeacherRepo.findByCourseId(id);
        course.setTeachersText(TeacherDisplayText.buildTeachersText(updated));
        courseRepo.save(course);

        return ResponseEntity.ok(teacherRelationsToDto(updated));
    }

    /** Κλειδί ζεύγους (teacherId|courseId, role) για diff/dedup των M2M σχέσεων. */
    private record RelationKey(Long id, CourseTeacher.Role role) {}

    /** null/blank role → PRIMARY· άγνωστο → {@link IllegalArgumentException} (→ 400). */
    static CourseTeacher.Role parseRole(String role) {
        if (role == null || role.isBlank()) {
            return CourseTeacher.Role.PRIMARY;
        }
        return CourseTeacher.Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
    }

    private List<Map<String, Object>> teacherRelationsToDto(List<CourseTeacher> relations) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseTeacher ct : relations) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("teacherId",   ct.getTeacher().getId());
            dto.put("teacherName", ct.getTeacher().getName());
            dto.put("role",        ct.getRole() != null ? ct.getRole().name() : null);
            result.add(dto);
        }
        return result;
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
        dto.put("expectedStudents", course.getExpectedStudents());
        dto.put("examDurationMinutes", course.getExamDurationMinutes());
        dto.put("needsLab", course.getNeedsLab());
        dto.put("needsProjector", course.getNeedsProjector());
        dto.put("semesterType", course.getSemesterType() != null ? course.getSemesterType().name() : null);
        dto.put("teachersText", normalizeTeachersTextForDto(course.getTeachersText()));
        dto.put("active", course.getActive());
        dto.put("visibleInTimetable", course.getVisibleInTimetable());
        dto.put("preferredExamRooms", course.getPreferredExamRooms());
        dto.put("preferredExamHours", course.getPreferredExamHours());
        dto.put("notes", course.getNotes());

        return dto;
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

private String normalizeKnownTeacherTypos(String value) {
    if (value == null) {
        return "";
    }

    return value
            .replace("Α. Ηλίαςςς (ΕΔΙΠ)", "Α. Ηλίας (ΕΔΙΠ)")
            .replace("Α. Ηλίαςςς", "Α. Ηλίας")
            .replace("Α. Ηλία (ΕΔΙΠ)", "Α. Ηλίας (ΕΔΙΠ)")
            .replace("Α. Ηλία", "Α. Ηλίας")
            .replace("Ηλίαςςς", "Ηλίας");
}

private String cleanTeacherDisplayName(String value) {
    if (value == null) {
        return "";
    }

    String cleaned = value
            .replace("(Υ)", "")
            .replace("(Θ)", "")
            .replace("(Ε)", "")
            .replace("Ε.ΔΙ.Π.", "ΕΔΙΠ")
            .replace("Ε.ΔΙ.Π", "ΕΔΙΠ")
            .replace("Εντεταλμένος Διδασκων", "Εντεταλμένος Διδάσκων")
            .replace("Εντεταλμένων Διδάσκων", "Εντεταλμένος Διδάσκων");

    cleaned = normalizeKnownTeacherTypos(cleaned);

    return cleaned
            .replaceAll("\\s+", " ")
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
}