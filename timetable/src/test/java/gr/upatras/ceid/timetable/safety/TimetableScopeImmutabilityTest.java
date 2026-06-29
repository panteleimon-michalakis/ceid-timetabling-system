package gr.upatras.ceid.timetable.safety;

import gr.upatras.ceid.timetable.controller.TimetableController;
import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.Timetable;
import gr.upatras.ceid.timetable.entity.TimetableScopedCourse;
import gr.upatras.ceid.timetable.repository.CourseRepository;
import gr.upatras.ceid.timetable.repository.TimetableAssignmentRepository;
import gr.upatras.ceid.timetable.repository.TimetableRepository;
import gr.upatras.ceid.timetable.repository.TimetableScopedCourseRepository;
import gr.upatras.ceid.timetable.service.TimetableScopeService;
import gr.upatras.ceid.timetable.util.CourseRelevance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #5 — Architecture invariant #1 (snapshot immutability) επεκταμένο από «ανά
 * ανάθεση» (S3) στο «ανά πρόγραμμα». Το completeness (validate/progress) διαβάζει
 * το ΠΑΓΩΜΕΝΟ {@link TimetableScopedCourse} scope, ΟΧΙ τον ζωντανό κατάλογο, ώστε
 * προσθήκες/διαγραφές/edits μαθημάτων να ΜΗΝ διαρρέουν αναδρομικά σε υπάρχοντα
 * προγράμματα ως phantom MISSING_HOURS/MISSING_EXAM.
 *
 * Σκόπιμα ΧΩΡΙΣ @Transactional: τα writes (create/materialize/delete) γίνονται
 * commit ώστε το πραγματικό transaction του controller/service να είναι το unit
 * under test· καθαρισμός με MARK prefix.
 */
@SpringBootTest
class TimetableScopeImmutabilityTest {

    private static final String MARK = "TEST_SCOPE5_";

    @Autowired TimetableController controller;
    @Autowired TimetableScopeService scopeService;
    @Autowired TimetableScopedCourseRepository scopedCourseRepo;
    @Autowired TimetableRepository timetableRepo;
    @Autowired CourseRepository courseRepo;
    @Autowired TimetableAssignmentRepository assignmentRepo;

    @BeforeEach
    void clean() { cleanup(); }

    @AfterEach
    void cleanAfter() { cleanup(); }

    // ====================================================================
    // 1. freeze-on-create: το create() παγώνει τα relevant μαθήματα του καταλόγου
    // ====================================================================
    @Test
    void create_freezesScopeToRelevantCatalog() {
        Course mine = saveCourse("CREATE_C"); // εγγυάται ≥1 relevant μάθημα (dev ή κενή CI βάση)
        Timetable tt = createViaController("CREATE");

        // BL-9 / #4 contract: το freeze (materializeScopeIfAbsent) παγώνει ΜΟΝΟ
        // μη-διαγραμμένα relevant μαθήματα (findByDeletedFalse). Το expected ΠΡΕΠΕΙ να
        // χρησιμοποιεί ΑΚΡΙΒΩΣ το ίδιο predicate, αλλιώς soft-deleted relevant μαθήματα
        // στη dev DB δίνουν ψευδή απόκλιση (findAll: 61 vs frozen: 59).
        long expected = courseRepo.findByDeletedFalse().stream()
                .filter(c -> CourseRelevance.isRelevant(c, tt))
                .count();
        List<TimetableScopedCourse> scoped = scopedCourseRepo.findByTimetableId(tt.getId());

        assertEquals(expected, scoped.size(),
                "freeze-on-create: scope == πλήθος μη-διαγραμμένων relevant μαθημάτων του καταλόγου");
        assertTrue(scoped.stream().anyMatch(s -> mine.getId().equals(s.getCourseId())),
                "το νεοδημιουργημένο πρόγραμμα παγώνει το τρέχον relevant μάθημα");
    }

    // ====================================================================
    // 2. immunity: νέο μάθημα ΜΕΤΑ το freeze δεν διαρρέει στο παλιό πρόγραμμα
    // ====================================================================
    @Test
    void existingTimetableImmuneToNewCourse() {
        Timetable old = seedTimetableWithScope("OLD");
        // FALL + 2 ώρες θεωρίας → προ-#5 θα διέρρεε ως MISSING_HOURS/MISSING στο παλιό
        Course added = saveCourse("NEW2");

        List<Map<String, Object>> warnings = extractList(
                controller.validateTimetableReport(old.getId()), "warnings");
        assertFalse(warnings.stream().anyMatch(w -> added.getId().equals(toLong(w.get("referenceId")))),
                "το νέο μάθημα ΔΕΝ διαρρέει ως warning στο παλιό πρόγραμμα (validate)");

        List<Map<String, Object>> missing = extractList(
                controller.getProgress(old.getId()), "missingCourses");
        assertFalse(missing.stream().anyMatch(m -> added.getId().equals(toLong(m.get("courseId")))),
                "το νέο μάθημα ΔΕΝ διαρρέει ως missing στο παλιό πρόγραμμα (progress)");
    }

    // ====================================================================
    // 3. new timetable sees it: πρόγραμμα που φτιάχνεται ΜΕΤΑ βλέπει το νέο μάθημα
    // ====================================================================
    @Test
    void newTimetableSeesNewCourse() {
        Course added = saveCourse("NEW3");
        Timetable fresh = seedTimetableWithScope("FRESH");

        assertTrue(scopedCourseRepo.findByTimetableId(fresh.getId()).stream()
                        .anyMatch(s -> added.getId().equals(s.getCourseId())),
                "νέο πρόγραμμα (μετά την προσθήκη) περιλαμβάνει το νέο μάθημα στο scope");
    }

    // ====================================================================
    // 4. delete-survival: hard delete μαθήματος → οι scope rows παραμένουν (FK-free)
    // ====================================================================
    @Test
    void scopeSurvivesCourseHardDelete() {
        Course added = saveCourse("DEL4");
        Timetable tt = seedTimetableWithScope("DELTT");
        Long courseId = added.getId();

        assertTrue(scopedCourseRepo.findByTimetableId(tt.getId()).stream()
                        .anyMatch(s -> courseId.equals(s.getCourseId())),
                "precondition: το μάθημα είναι στο scope");

        courseRepo.deleteById(courseId); // hard delete — course_id ΧΩΡΙΣ FK

        assertFalse(courseRepo.existsById(courseId), "το μάθημα διαγράφηκε από τον κατάλογο");
        assertTrue(scopedCourseRepo.findByTimetableId(tt.getId()).stream()
                        .anyMatch(s -> courseId.equals(s.getCourseId())),
                "delete-survival: η scope row παραμένει μετά το hard delete του μαθήματος");
    }

    // ====================================================================
    // 5. backfill idempotency: 2η materialize στο ίδιο πρόγραμμα → 0, καμία διπλοεγγραφή
    // ====================================================================
    @Test
    void materializeScopeIsIdempotent() {
        Timetable tt = seedTimetableWithScope("IDEM"); // 1η κλήση (μέσα στο helper)
        int before = scopedCourseRepo.findByTimetableId(tt.getId()).size();

        int written = scopeService.materializeScopeIfAbsent(tt); // 2η κλήση

        assertEquals(0, written, "freeze-once: η 2η κλήση γράφει 0 γραμμές");
        assertEquals(before, scopedCourseRepo.findByTimetableId(tt.getId()).size(),
                "καμία διπλοεγγραφή");
    }

    // ====================================================================
    // 6. BL-10: το freeze ευθυγραμμίζεται με τη schedulability του solver — μάθημα
    //    inactive (active=false) ή invisible (visibleInTimetable=false) ΔΕΝ παγώνει
    //    (αλλιώς phantom MISSING_HOURS/MISSING_EXAM: στο scope αλλά ποτέ τοποθετημένο).
    //    Σημ.: null active/visible δεν δοκιμάζεται εδώ (DB NOT NULL)· το null-handling
    //    του predicate καλύπτεται από το pure-unit CourseRelevanceSchedulabilityTest.
    // ====================================================================
    @Test
    void freeze_alignsWithSolverSchedulability_BL10() {
        Course inactive  = saveCourseWithFlags("BL10_INACT", false, true);  // active=false
        Course invisible = saveCourseWithFlags("BL10_INVIS", true, false);  // visibleInTimetable=false
        Course schedulable = saveCourseWithFlags("BL10_OK",  true, true);
        Timetable tt = seedTimetableWithScope("BL10");

        List<TimetableScopedCourse> scoped = scopedCourseRepo.findByTimetableId(tt.getId());
        assertFalse(scopeHasCourse(scoped, inactive),
                "active=false → μη-schedulable → ΔΕΝ παγώνει (BL-10)");
        assertFalse(scopeHasCourse(scoped, invisible),
                "visibleInTimetable=false → μη-schedulable → ΔΕΝ παγώνει (BL-10)");
        assertTrue(scopeHasCourse(scoped, schedulable),
                "active & visible → schedulable → παγώνει κανονικά");
    }

    // ====================================================================
    // Helpers
    // ====================================================================
    private Timetable createViaController(String suffix) {
        ResponseEntity<?> resp = controller.create(Map.of(
                "name", MARK + suffix,
                "timetableType", "SEMESTER",
                "semesterType", "FALL"));
        assertEquals(200, resp.getStatusCode().value(), "create -> 200");
        Timetable body = (Timetable) resp.getBody();
        assertNotNull(body, "create επιστρέφει το πρόγραμμα");
        return timetableRepo.findById(body.getId()).orElseThrow();
    }

    private Timetable seedTimetableWithScope(String suffix) {
        Timetable tt = timetableRepo.save(Timetable.builder()
                .name(MARK + suffix).academicYear("2025-26")
                .timetableType(Timetable.TimetableType.SEMESTER)
                .semesterType(Timetable.SemesterType.FALL)
                .status(Timetable.Status.DRAFT)
                .createdAt(LocalDateTime.now())
                .build());
        scopeService.materializeScopeIfAbsent(tt);
        return tt;
    }

    private Course saveCourse(String suffix) {
        return saveCourseWithFlags(suffix, true, true);
    }

    private Course saveCourseWithFlags(String suffix, Boolean active, Boolean visible) {
        return courseRepo.save(Course.builder()
                .code(MARK + suffix).name("Scope Test Course " + suffix)
                .semester(1).studyYear(1)
                .courseType(Course.CourseType.REQUIRED)
                .lectureHours(2).tutorialHours(0).labHours(0)
                .expectedStudents(10)
                .semesterType(Course.SemesterType.FALL)
                .active(active).visibleInTimetable(visible)
                .build());
    }

    private static boolean scopeHasCourse(List<TimetableScopedCourse> scoped, Course c) {
        return scoped.stream().anyMatch(s -> c.getId().equals(s.getCourseId()));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractList(ResponseEntity<?> resp, String key) {
        assertEquals(200, resp.getStatusCode().value(), "endpoint -> 200");
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertNotNull(body, "body μη-null");
        return (List<Map<String, Object>>) body.get(key);
    }

    private static Long toLong(Object o) {
        return (o instanceof Number n) ? n.longValue() : null;
    }

    private void cleanup() {
        // Πρόγραμματα (+ scope rows + αναθέσεις) με MARK όνομα
        timetableRepo.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().startsWith(MARK))
                .forEach(t -> {
                    scopedCourseRepo.deleteAll(scopedCourseRepo.findByTimetableId(t.getId()));
                    assignmentRepo.deleteAll(assignmentRepo.findByTimetableId(t.getId()));
                    timetableRepo.deleteById(t.getId());
                });
        // MARK μαθήματα (scope rows ΧΩΡΙΣ FK· έχουν ήδη καθαριστεί παραπάνω ανά πρόγραμμα)
        courseRepo.findAll().stream()
                .filter(c -> c.getCode() != null && c.getCode().startsWith(MARK))
                .forEach(c -> courseRepo.deleteById(c.getId()));
    }
}
