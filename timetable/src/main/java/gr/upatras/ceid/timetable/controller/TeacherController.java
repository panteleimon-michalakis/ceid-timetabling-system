package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.CourseTeacher;
import gr.upatras.ceid.timetable.entity.Teacher;
import gr.upatras.ceid.timetable.entity.TeacherConstraint;
import gr.upatras.ceid.timetable.repository.CourseRepository;
import gr.upatras.ceid.timetable.repository.CourseTeacherRepository;
import gr.upatras.ceid.timetable.repository.TeacherConstraintRepository;
import gr.upatras.ceid.timetable.repository.TeacherRepository;
import gr.upatras.ceid.timetable.repository.UserRepository;
import gr.upatras.ceid.timetable.entity.User;
import org.springframework.security.core.Authentication;
import gr.upatras.ceid.timetable.service.TeacherImportService;
import gr.upatras.ceid.timetable.solver.TeacherAvailabilityConstraints;
import gr.upatras.ceid.timetable.solver.TeacherAvailabilityRegistry;
import gr.upatras.ceid.timetable.util.TeacherDisplayText;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.text.Normalizer;
import java.util.*;

@RestController
@RequestMapping("/api/teachers")
public class TeacherController {

    private final TeacherRepository teacherRepo;
    private final CourseTeacherRepository courseTeacherRepo;
    private final CourseRepository courseRepo;
    private final TeacherConstraintRepository constraintRepo;
    private final TeacherImportService teacherImportService;
    private final UserRepository userRepo;

    public TeacherController(
            TeacherRepository teacherRepo,
            CourseTeacherRepository courseTeacherRepo,
            CourseRepository courseRepo,
            TeacherConstraintRepository constraintRepo,
            TeacherImportService teacherImportService,
            UserRepository userRepo
    ) {
        this.teacherRepo = teacherRepo;
        this.courseTeacherRepo = courseTeacherRepo;
        this.courseRepo = courseRepo;
        this.constraintRepo = constraintRepo;
        this.teacherImportService = teacherImportService;
        this.userRepo = userRepo;
    }

    /**
     * Structured αναφορά μαθήματος για το reverse M2M sync. Το {@code role}
     * είναι προαιρετικό· null/blank → {@link CourseTeacher.Role#PRIMARY}.
     */
    public record CourseRef(Long courseId, String role) {}

    /**
     * Επιστρέφει true αν ο τρέχων χρήστης ΔΕΝ επιτρέπεται να επεξεργαστεί
     * τον καθηγητή με το δοθέν id. ADMIN: πάντα επιτρέπεται. TEACHER:
     * μόνο αν το teacherId του λογαριασμού του ταιριάζει.
     */
    private boolean isForbiddenToEditTeacher(Authentication auth, Long teacherId) {
        if (auth == null) return true;
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return false;
        // Μη-admin: πρέπει να είναι ο ιδιοκτήτης του φακέλου.
        User current = userRepo.findByUsername(auth.getName()).orElse(null);
        if (current == null || current.getTeacherId() == null) return true;
        return !current.getTeacherId().equals(teacherId);
    }

    // ── Basic CRUD ──────────────────────────────────────────────────────────

    /**
     * Σταθερή σειρά: βαθμίδα κατά enum ordinal (PROFESSOR → ... → APPOINTED,
     * δηλ. καθηγητές πρώτα, εντεταλμένοι τελευταίοι), μετά όνομα, μετά id.
     * Ταξινόμηση in-memory επειδή το teacherType αποθηκεύεται ως EnumType.STRING
     * (η DB δεν εγγυάται ordinal σειρά).
     */
    @GetMapping
    public List<Teacher> getAll() {
        return teacherRepo.findAll().stream()
                .sorted(Comparator
                        .comparingInt((Teacher t) -> t.getTeacherType() == null
                                ? Integer.MAX_VALUE : t.getTeacherType().ordinal())
                        .thenComparing(Teacher::getName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(Teacher::getId))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Teacher> getById(@PathVariable Long id) {
        return teacherRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Teacher create(@RequestBody Teacher teacher) {
        // Jackson (no-args + setters): αν το JSON δεν φέρει active, default true.
        if (teacher.getActive() == null) teacher.setActive(true);
        return teacherRepo.save(teacher);
    }

    /**
     * Ενεργοποίηση/απενεργοποίηση καθηγητή (soft-delete toggle) — ADMIN-only
     * (ειδικός matcher PUT /api/teachers/{id}/active στο SecurityConfig, ΠΡΙΝ
     * τον γενικό κανόνα που επιτρέπει PUT σε TEACHER). Body: { "active": bool }.
     */
    @PutMapping("/{id}/active")
    @Transactional
    public ResponseEntity<?> setActive(@PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        Teacher teacher = teacherRepo.findById(id).orElse(null);
        if (teacher == null) return ResponseEntity.notFound().build();
        if (!(body.get("active") instanceof Boolean active)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "Απαιτείται boolean πεδίο 'active'."));
        }
        teacher.setActive(active);
        return ResponseEntity.ok(teacherRepo.save(teacher));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Teacher> update(@PathVariable Long id, @RequestBody Teacher updated,
                                          Authentication auth) {
        if (isForbiddenToEditTeacher(auth, id)) {
            return ResponseEntity.status(403).build();
        }
        return teacherRepo.findById(id).map(t -> {
            t.setName(updated.getName());
            t.setShortName(updated.getShortName());
            t.setTeacherType(updated.getTeacherType());
            t.setEmail(updated.getEmail());
            t.setDepartment(updated.getDepartment());
            t.setNotes(updated.getNotes());
            return ResponseEntity.ok(teacherRepo.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Teacher teacher = teacherRepo.findById(id).orElse(null);
        if (teacher == null) return ResponseEntity.notFound().build();
        // Soft-delete (S1): αν ο καθηγητής διδάσκει ≥1 μάθημα (CourseTeacher
        // αναφορά), ΔΕΝ σβήνεται — απενεργοποιείται, κρατώντας links/ιστορικό.
        if (courseTeacherRepo.existsByTeacherId(id)) {
            teacher.setActive(false);
            teacherRepo.save(teacher);
            return ResponseEntity.ok(Map.of("deactivated", true, "id", id));
        }
        // Ποτέ δεν χρησιμοποιήθηκε: hard-delete (constraints + teacher, ατομικά).
        constraintRepo.deleteByTeacherId(id);
        teacherRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Courses ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}/courses")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getTeacherCourses(@PathVariable Long id) {
        if (!teacherRepo.existsById(id)) return ResponseEntity.notFound().build();
        List<CourseTeacher> relations = courseTeacherRepo.findByTeacherId(id);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseTeacher ct : relations) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("courseId",   ct.getCourse().getId());
            dto.put("courseCode", ct.getCourse().getCode());
            dto.put("courseName", ct.getCourse().getName());
            dto.put("semester",   ct.getCourse().getSemester());
            dto.put("studyYear",  ct.getCourse().getStudyYear());
            dto.put("role",       ct.getRole() != null ? ct.getRole().name() : null);
            result.add(dto);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Reverse M2M sync: αντικαθιστά (replace-set) τα μαθήματα του διδάσκοντα από
     * structured input (courseId + role) και αναπαράγει το {@code teachersText}
     * ΚΑΘΕ επηρεαζόμενου μαθήματος — όσων προστέθηκαν ΚΑΙ όσων αφαιρέθηκαν (το
     * string τους αλλάζει κι αυτών). Συμμετρικό του {@code CourseController.PUT
     * /{id}/teachers}: το {@code course_teachers} M2M μένει η authoritative πηγή.
     */
    @PutMapping("/{id}/courses")
    @Transactional
    public ResponseEntity<?> setTeacherCourses(@PathVariable Long id,
                                               @RequestBody List<CourseRef> body,
                                               Authentication auth) {
        Teacher teacher = teacherRepo.findById(id).orElse(null);
        if (teacher == null) return ResponseEntity.notFound().build();
        if (isForbiddenToEditTeacher(auth, id)) {
            return ResponseEntity.status(403).build();
        }

        // desired set (courseId, role)· validate κάθε courseId + role
        LinkedHashMap<RelationKey, Course> desired = new LinkedHashMap<>();
        if (body != null) {
            for (CourseRef ref : body) {
                if (ref == null || ref.courseId() == null) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Απαιτείται courseId σε κάθε εγγραφή."));
                }
                CourseTeacher.Role role;
                try {
                    role = CourseController.parseRole(ref.role());
                } catch (IllegalArgumentException ex) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Άγνωστος ρόλος διδάσκοντα: " + ref.role()));
                }
                Course course = courseRepo.findById(ref.courseId()).orElse(null);
                if (course == null) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Δεν βρέθηκε μάθημα με id " + ref.courseId()));
                }
                desired.put(new RelationKey(ref.courseId(), role), course);
            }
        }

        List<CourseTeacher> existing = courseTeacherRepo.findByTeacherId(id);
        Set<Long> affectedCourseIds = new LinkedHashSet<>();

        // delete όσα existing δεν είναι στο desired (το course τους θα ξανα-stamp-αριστεί)
        for (CourseTeacher ct : existing) {
            RelationKey key = new RelationKey(ct.getCourse().getId(), ct.getRole());
            if (!desired.containsKey(key)) {
                affectedCourseIds.add(ct.getCourse().getId());
                courseTeacherRepo.delete(ct);
            }
        }

        // insert όσα desired λείπουν (σεβασμός unique (course,teacher,role))
        for (Map.Entry<RelationKey, Course> e : desired.entrySet()) {
            RelationKey key = e.getKey();
            if (!courseTeacherRepo.existsByCourseIdAndTeacherIdAndRole(key.id(), id, key.role())) {
                affectedCourseIds.add(key.id());
                courseTeacherRepo.save(CourseTeacher.builder()
                        .course(e.getValue()).teacher(teacher).role(key.role()).build());
            }
        }

        // regenerate teachersText για ΚΑΘΕ επηρεαζόμενο μάθημα (added ή removed)
        for (Long courseId : affectedCourseIds) {
            Course course = courseRepo.findById(courseId).orElse(null);
            if (course == null) continue;
            course.setTeachersText(
                    TeacherDisplayText.buildTeachersText(courseTeacherRepo.findByCourseId(courseId)));
            courseRepo.save(course);
        }

        // τελικά relations του διδάσκοντα ως [{courseId, courseCode, courseName, role}]
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseTeacher ct : courseTeacherRepo.findByTeacherId(id)) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("courseId",   ct.getCourse().getId());
            dto.put("courseCode", ct.getCourse().getCode());
            dto.put("courseName", ct.getCourse().getName());
            dto.put("role",       ct.getRole() != null ? ct.getRole().name() : null);
            result.add(dto);
        }
        return ResponseEntity.ok(result);
    }

    /** Κλειδί ζεύγους (courseId, role) για diff/dedup των M2M σχέσεων. */
    private record RelationKey(Long id, CourseTeacher.Role role) {}

    // ── DB Constraints (editable) ────────────────────────────────────────────

    @GetMapping("/{id}/constraints")
    public ResponseEntity<?> getConstraints(@PathVariable Long id) {
        if (!teacherRepo.existsById(id)) return ResponseEntity.notFound().build();
        List<TeacherConstraint> list = constraintRepo.findByTeacherId(id);
        List<Map<String, Object>> result = list.stream().map(c -> {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id",             c.getId());
            dto.put("dayOfWeek",      c.getDayOfWeek());
            dto.put("hour",           c.getHour());
            dto.put("constraintType", c.getConstraintType().name());
            return dto;
        }).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Batch replace: αντικαθιστά ΟΛΟΥΣ τους περιορισμούς του καθηγητή.
     * Body: [ { dayOfWeek: "MONDAY", hour: 9, constraintType: "BLOCKED" }, ... ]
     */
    @PutMapping("/{id}/constraints")
    @Transactional
    public ResponseEntity<?> updateConstraints(
            @PathVariable Long id,
            @RequestBody List<Map<String, Object>> body,
            Authentication auth) {

        if (isForbiddenToEditTeacher(auth, id)) {
            return ResponseEntity.status(403).build();
        }
        Teacher teacher = teacherRepo.findById(id).orElse(null);
        if (teacher == null) return ResponseEntity.notFound().build();

        constraintRepo.deleteByTeacherId(id);

        List<TeacherConstraint> newList = new ArrayList<>();
        for (Map<String, Object> item : body) {
            TeacherConstraint c = new TeacherConstraint();
            c.setTeacher(teacher);
            c.setDayOfWeek((String) item.get("dayOfWeek"));
            c.setHour(((Number) item.get("hour")).intValue());
            c.setConstraintType(
                TeacherConstraint.ConstraintType.valueOf((String) item.get("constraintType"))
            );
            newList.add(c);
        }
        constraintRepo.saveAll(newList);
        return ResponseEntity.ok(Map.of("saved", newList.size()));
    }

    // ── Legacy registry (read-only, for solver compatibility) ────────────────

    @GetMapping("/{id}/availability")
    public ResponseEntity<?> getTeacherAvailability(@PathVariable Long id) {
        Teacher teacher = teacherRepo.findById(id).orElse(null);
        if (teacher == null) return ResponseEntity.notFound().build();
        if (TeacherAvailabilityConstraints.BLOCKED_SLOTS == null) {
            TeacherAvailabilityRegistry.load();
        }
        String key = generateTeacherKey(teacher.getName());
        Set<String> blocked   = TeacherAvailabilityConstraints.BLOCKED_SLOTS   != null
                ? TeacherAvailabilityConstraints.BLOCKED_SLOTS.getOrDefault(key, Set.of()) : Set.of();
        Set<String> preferred = TeacherAvailabilityConstraints.PREFERRED_SLOTS != null
                ? TeacherAvailabilityConstraints.PREFERRED_SLOTS.getOrDefault(key, Set.of()) : Set.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("teacherKey", key);
        result.put("blocked",    new ArrayList<>(blocked));
        result.put("preferred",  new ArrayList<>(preferred));
        return ResponseEntity.ok(result);
    }

    // ── Import helpers ───────────────────────────────────────────────────────

    @PostMapping("/import-from-courses")
    public ResponseEntity<?> importFromCourses() {
        return ResponseEntity.ok(teacherImportService.importFromCourseTeacherTexts());
    }

    @PostMapping("/reset-imported")
    public ResponseEntity<?> resetImportedTeachers() {
        return ResponseEntity.ok(teacherImportService.resetImportedTeachers());
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private String generateTeacherKey(String name) {
        if (name == null || name.isBlank()) return "";
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase();
        String[] parts = normalized.split("[\\s.,]+");
        String initial = "", surname = "";
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (p.length() == 1) initial = p; else surname = p;
        }
        if (surname.isEmpty()) return initial;
        if (initial.isEmpty()) return surname;
        return surname + "|" + initial;
    }

@GetMapping("/constraints/all")
public ResponseEntity<?> getAllTeacherConstraints() {
    java.util.List<gr.upatras.ceid.timetable.entity.TeacherConstraint> all =
        constraintRepo.findAllWithTeacher();
    java.util.LinkedHashMap<Long, java.util.Map<String, Object>> grouped = new java.util.LinkedHashMap<>();
    for (gr.upatras.ceid.timetable.entity.TeacherConstraint tc : all) {
        Long tid = tc.getTeacher().getId();
        grouped.computeIfAbsent(tid, k -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("teacherId", tid);
            m.put("teacherName", tc.getTeacher().getName());
            m.put("constraints", new java.util.ArrayList<java.util.Map<String,Object>>());
            return m;
        });
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String,Object>> cons =
            (java.util.List<java.util.Map<String,Object>>) grouped.get(tid).get("constraints");
        cons.add(java.util.Map.of(
            "dayOfWeek",       tc.getDayOfWeek().toString(),
            "hour",            tc.getHour(),
            "constraintType",  tc.getConstraintType().name()
        ));
    }
    return ResponseEntity.ok(new java.util.ArrayList<>(grouped.values()));
}

}