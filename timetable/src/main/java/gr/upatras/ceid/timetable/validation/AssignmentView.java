package gr.upatras.ceid.timetable.validation;

import java.util.List;

/**
 * Φ-SV2b-i: ελαφρύ view ενός assignment — ΜΟΝΟ ό,τι χρειάζονται τα μηνύματα των
 * hard validation issues. Αποσυνδέει τον {@link HardViolationTranslator} από τα JPA
 * entities ώστε να είναι πλήρως unit-testable χωρίς DB. Ο adapter
 * {@code TimetableAssignment -> AssignmentView} (live course/room/teachers) έρχεται
 * στη Φάση 2b-ii.
 *
 * @param assignmentId      το id της ανάθεσης
 * @param courseName        όνομα μαθήματος (display)
 * @param studyYear         έτος σπουδών
 * @param roomCode          κωδικός αίθουσας (π.χ. "Δ1")
 * @param dayOfWeekName     όνομα ημέρας (π.χ. "MONDAY") ή null
 * @param startHour         ώρα έναρξης (π.χ. 9) ή null
 * @param teacherNames      ονόματα διδασκόντων (για TEACHER_CONFLICT/BLOCKED display)
 * @param assignmentTypeName LECTURE/TUTORIAL/LAB/EXAM
 */
public record AssignmentView(
        Long assignmentId,
        String courseName,
        int studyYear,
        String roomCode,
        String dayOfWeekName,
        Integer startHour,
        List<String> teacherNames,
        String assignmentTypeName
) {}
