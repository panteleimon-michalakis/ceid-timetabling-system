package gr.upatras.ceid.timetable.service;

import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.CourseTeacher;
import gr.upatras.ceid.timetable.entity.Teacher;
import gr.upatras.ceid.timetable.repository.CourseRepository;
import gr.upatras.ceid.timetable.repository.CourseTeacherRepository;
import gr.upatras.ceid.timetable.repository.TeacherRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;

@Service
public class TeacherImportService {

    private static final String AUTO_CREATED_NOTE =
            "Αυτόματη δημιουργία από το teachersText του μαθήματος.";

    private final CourseRepository courseRepo;
    private final TeacherRepository teacherRepo;
    private final CourseTeacherRepository courseTeacherRepo;

    public TeacherImportService(
            CourseRepository courseRepo,
            TeacherRepository teacherRepo,
            CourseTeacherRepository courseTeacherRepo
    ) {
        this.courseRepo = courseRepo;
        this.teacherRepo = teacherRepo;
        this.courseTeacherRepo = courseTeacherRepo;
    }

    @Transactional
    public Map<String, Object> importFromCourseTeacherTexts() {
        List<Course> courses = courseRepo.findByDeletedFalse();

        int createdTeachers = 0;
        int createdRelations = 0;
        int skippedCoursesWithoutTeachers = 0;

        List<String> warnings = new ArrayList<>();

        for (Course course : courses) {
            String teachersText = course.getTeachersText();

            if (teachersText == null || teachersText.trim().isEmpty()) {
                skippedCoursesWithoutTeachers++;
                continue;
            }

            List<String> teacherNames = splitTeacherNames(teachersText);

            if (teacherNames.isEmpty()) {
                skippedCoursesWithoutTeachers++;
                warnings.add("Δεν εντοπίστηκε καθαρό όνομα διδάσκοντα για το μάθημα " + course.getCode());
                continue;
            }

            for (int i = 0; i < teacherNames.size(); i++) {
                String teacherName = teacherNames.get(i);

                Optional<Teacher> existingTeacher = teacherRepo.findByName(teacherName);

                Teacher teacher;

                if (existingTeacher.isPresent()) {
                    teacher = existingTeacher.get();
                } else {
                    teacher = Teacher.builder()
                            .name(teacherName)
                            .shortName(generateUniqueShortName(teacherName))
                            .teacherType(detectTeacherType(teacherName))
                            .department("CEID")
                            .notes(AUTO_CREATED_NOTE)
                            .build();

                    teacher = teacherRepo.save(teacher);
                    createdTeachers++;
                }

                CourseTeacher.Role role = (i == 0)
                        ? CourseTeacher.Role.PRIMARY
                        : CourseTeacher.Role.SECONDARY;

                boolean relationExists = courseTeacherRepo.existsByCourseIdAndTeacherIdAndRole(
                        course.getId(),
                        teacher.getId(),
                        role
                );

                if (!relationExists) {
                    CourseTeacher relation = CourseTeacher.builder()
                            .course(course)
                            .teacher(teacher)
                            .role(role)
                            .build();

                    courseTeacherRepo.save(relation);
                    createdRelations++;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("coursesChecked", courses.size());
        result.put("createdTeachers", createdTeachers);
        result.put("createdCourseTeacherRelations", createdRelations);
        result.put("skippedCoursesWithoutTeachers", skippedCoursesWithoutTeachers);
        result.put("warnings", warnings);

        return result;
    }

    @Transactional
    public Map<String, Object> resetImportedTeachers() {
        List<Teacher> autoCreatedTeachers = teacherRepo.findAll()
                .stream()
                .filter(this::isAutoCreatedTeacher)
                .toList();

        Set<Long> autoCreatedTeacherIds = new HashSet<>();
        for (Teacher teacher : autoCreatedTeachers) {
            autoCreatedTeacherIds.add(teacher.getId());
        }

        List<CourseTeacher> relationsToDelete = courseTeacherRepo.findAll()
                .stream()
                .filter(relation ->
                        relation.getTeacher() != null
                                && relation.getTeacher().getId() != null
                                && autoCreatedTeacherIds.contains(relation.getTeacher().getId())
                )
                .toList();

        courseTeacherRepo.deleteAll(relationsToDelete);
        teacherRepo.deleteAll(autoCreatedTeachers);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deletedImportedCourseTeacherRelations", relationsToDelete.size());
        result.put("deletedImportedTeachers", autoCreatedTeachers.size());

        return result;
    }

    private boolean isAutoCreatedTeacher(Teacher teacher) {
        if (teacher == null || teacher.getNotes() == null) {
            return false;
        }

        String notes = teacher.getNotes();

        return notes.contains("teachersText")
                || notes.contains("Αυτόματη δημιουργία");
    }

private List<String> splitTeacherNames(String teachersText) {
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
        String name = cleanTeacherName(part);
        if (name.isBlank()) {
            continue;
        }

        if (isStandaloneTeacherRole(name)) {
            if (!result.isEmpty()) {
                int last = result.size() - 1;
                result.set(last, appendRoleToTeacher(result.get(last), normalizeStandaloneTeacherRole(name)));
            }
            continue;
        }

        if (isUsefulTeacherName(name) && !result.contains(name)) {
            result.add(name);
        }
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

private String cleanTeacherName(String value) {
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

    private boolean isUsefulTeacherName(String name) {
        if (name == null) {
            return false;
        }

        String cleaned = name.trim();

        if (cleaned.length() < 2) {
            return false;
        }

        String upper = cleaned.toUpperCase(Locale.ROOT);

        if (upper.equals("Η") || upper.equals("Ή") || upper.equals("ΚΑΙ")) {
            return false;
        }

        if (upper.equals("ΑΑΔΕ")) {
            return false;
        }

        if (upper.matches("Ε\\.?\\s*Δ\\.?\\s*Ι\\.?\\s*Π\\.?")) {
            return false;
        }

        if (upper.matches("Ε\\.?\\s*Τ\\.?\\s*Ε\\.?\\s*Π\\.?")) {
            return false;
        }

        return true;
    }

private Teacher.TeacherType detectTeacherType(String teacherName) {
    if (teacherName == null) {
        return Teacher.TeacherType.PROFESSOR;
    }

    String upper = Normalizer.normalize(teacherName, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT);

    if (upper.matches(".*Ε\\.?\\s*Δ\\.?\\s*Ι\\.?\\s*Π\\.?.*")) {
        return Teacher.TeacherType.EDIP;
    }

    if (upper.matches(".*Ε\\.?\\s*Τ\\.?\\s*Ε\\.?\\s*Π\\.?.*")) {
        return Teacher.TeacherType.ETEP;
    }

    if (upper.contains("ΕΝΤΕΤΑΛΜΕΝΟΣ") || upper.contains("ΕΝΤΕΤΑΛΜΕΝΗ")) {
        return Teacher.TeacherType.APPOINTED;
    }

    return Teacher.TeacherType.PROFESSOR;
}

    private String generateUniqueShortName(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");

        if (base.isBlank()) {
            base = "TEACHER";
        }

        if (base.length() > 80) {
            base = base.substring(0, 80);
        }

        String candidate = base;
        int counter = 2;

        while (teacherRepo.findByShortName(candidate).isPresent()) {
            candidate = base + "_" + counter;
            counter++;
        }

        return candidate;
    }
}