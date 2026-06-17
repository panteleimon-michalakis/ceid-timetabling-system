package gr.upatras.ceid.timetable.solver;

import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.CourseTeacher;
import gr.upatras.ceid.timetable.entity.Teacher;
import gr.upatras.ceid.timetable.repository.CourseRepository;
import gr.upatras.ceid.timetable.repository.CourseTeacherRepository;
import gr.upatras.ceid.timetable.repository.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S2: επιβεβαιώνει ότι το course_teachers M2M είναι η authoritative πηγή των
 * teacherKeys του solver — με active filter (BL-5) και teachers_text fallback
 * ΜΟΝΟ για μαθήματα χωρίς M2M rows.
 *
 * @Transactional → rollback καθαρίζει τα fixtures (τα queries του
 * buildTeacherKeyMap τρέχουν μέσα στο ίδιο tx και βλέπουν τα inserts).
 */
@SpringBootTest
class TeacherKeyMapTest {

    @Autowired SolverService solverService;
    @Autowired CourseRepository courseRepo;
    @Autowired TeacherRepository teacherRepo;
    @Autowired CourseTeacherRepository courseTeacherRepo;

    @Test
    @Transactional
    void m2mAuthoritative_withActiveFilter_andTextFallback() {
        Teacher active = teacherRepo.save(Teacher.builder()
                .name("Ι. ΕνεργόςΔοκιμή").teacherType(Teacher.TeacherType.PROFESSOR)
                .active(true).build());
        Teacher inactive = teacherRepo.save(Teacher.builder()
                .name("Κ. ΑνενεργόςΔοκιμή").teacherType(Teacher.TeacherType.PROFESSOR)
                .active(false).build());

        // (Α) M2M ενεργός, χωρίς text → key από M2M
        Course cActive = saveCourse("TEST_S2_A", null);
        link(cActive, active);

        // (Β) M2M ΑΝΕΝΕΡΓΟΣ + text → inactive filtered, text ΔΕΝ είναι fallback
        //     (το μάθημα έχει M2M row) → κανένα key
        Course cInactive = saveCourse("TEST_S2_B", "Ι. ΕνεργόςΔοκιμή");
        link(cInactive, inactive);

        // (Γ) χωρίς M2M, μόνο text → fallback ενεργοποιείται
        Course cFallback = saveCourse("TEST_S2_C", "Ι. ΕνεργόςΔοκιμή");

        // (Δ) M2M ενεργός + text διαφορετικού ονόματος → authoritative: μόνο M2M
        Course cBoth = saveCourse("TEST_S2_D", "Ζ. ΆλλοςΔοκιμή");
        link(cBoth, active);

        Map<Long, Set<String>> map = solverService.buildTeacherKeyMap();

        assertEquals(1, map.getOrDefault(cActive.getId(), Set.of()).size(),
                "(Α) ενεργός M2M → ακριβώς 1 key");
        assertTrue(map.getOrDefault(cInactive.getId(), Set.of()).isEmpty(),
                "(Β) inactive filtered & χωρίς text-fallback (υπάρχει M2M row) → 0 keys");
        assertFalse(map.getOrDefault(cFallback.getId(), Set.of()).isEmpty(),
                "(Γ) χωρίς M2M → text fallback δίνει key");
        assertEquals(1, map.getOrDefault(cBoth.getId(), Set.of()).size(),
                "(Δ) authoritative: μόνο το M2M key, το text αγνοείται");
    }

    private Course saveCourse(String code, String teachersText) {
        return courseRepo.save(Course.builder()
                .code(code).name("Test " + code).semester(1).studyYear(1)
                .courseType(Course.CourseType.REQUIRED)
                .semesterType(Course.SemesterType.FALL)
                .active(true).visibleInTimetable(true)
                .teachersText(teachersText)
                .build());
    }

    private void link(Course c, Teacher t) {
        courseTeacherRepo.save(CourseTeacher.builder()
                .course(c).teacher(t).role(CourseTeacher.Role.PRIMARY).build());
    }
}
