package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.CourseTeacher;
import gr.upatras.ceid.timetable.entity.Teacher;
import gr.upatras.ceid.timetable.entity.TeacherConstraint;
import gr.upatras.ceid.timetable.repository.CourseTeacherRepository;
import gr.upatras.ceid.timetable.repository.TeacherConstraintRepository;
import gr.upatras.ceid.timetable.repository.TeacherRepository;
import gr.upatras.ceid.timetable.service.TeacherImportService;
import gr.upatras.ceid.timetable.solver.TeacherAvailabilityConstraints;
import gr.upatras.ceid.timetable.solver.TeacherAvailabilityRegistry;
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
    private final TeacherConstraintRepository constraintRepo;
    private final TeacherImportService teacherImportService;

    public TeacherController(
            TeacherRepository teacherRepo,
            CourseTeacherRepository courseTeacherRepo,
            TeacherConstraintRepository constraintRepo,
            TeacherImportService teacherImportService
    ) {
        this.teacherRepo = teacherRepo;
        this.courseTeacherRepo = courseTeacherRepo;
        this.constraintRepo = constraintRepo;
        this.teacherImportService = teacherImportService;
    }

    // ── Basic CRUD ──────────────────────────────────────────────────────────

    @GetMapping
    public List<Teacher> getAll() {
        return teacherRepo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Teacher> getById(@PathVariable Long id) {
        return teacherRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Teacher create(@RequestBody Teacher teacher) {
        return teacherRepo.save(teacher);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Teacher> update(@PathVariable Long id, @RequestBody Teacher updated) {
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
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!teacherRepo.existsById(id)) return ResponseEntity.notFound().build();
        constraintRepo.deleteByTeacherId(id);
        courseTeacherRepo.deleteAll(courseTeacherRepo.findByTeacherId(id));
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
            @RequestBody List<Map<String, Object>> body) {

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
}