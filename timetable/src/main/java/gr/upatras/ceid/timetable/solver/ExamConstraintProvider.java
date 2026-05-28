package gr.upatras.ceid.timetable.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;


/**
 * Constraints ειδικά για πρόγραμμα εξεταστικής.
 *
 * Βασικές διαφορές από πρόγραμμα εξαμήνου:
 * - 1 εξέταση ανά μάθημα.
 * - Τα slots είναι τρίωρα, π.χ. 09:00-12:00, 12:00-15:00.
 * - Στην εξεταστική Σεπτεμβρίου εξετάζονται μαθήματα FALL και SPRING.
 * - Κρίσιμος hard κανόνας: δύο υποχρεωτικές εξετάσεις ίδιου έτους
 *   δεν επιτρέπεται να τοποθετηθούν την ίδια ημερομηνία.
 * - Η χωρητικότητα αίθουσας δεν μπλοκάρει από μόνη της την εξέταση,
 *   αλλά μειώνει το score, επειδή μια εξέταση μπορεί να μοιραστεί σε πολλές αίθουσες.
 */
public class ExamConstraintProvider implements ConstraintProvider {
@Override
public Constraint[] defineConstraints(ConstraintFactory factory) {
    return new Constraint[]{
            // HARD
            roomConflict(factory),
            teacherConflict(factory),
            requiredSameYearSameDay(factory),

            // SOFT
            roomCapacityMatch(factory),
            spreadSameYear(factory),
            directionGroupADifferentDays(factory),
            teacherMultipleExamsSameDay(factory),
            preferMorningForLargeCourses(factory),
    };
}

    // ===================== HARD =====================

    /**
     * HARD: Δύο εξετάσεις δεν μπορούν στην ίδια αίθουσα, ίδια ώρα.
     */
    Constraint roomConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getTimeSlot),
                        Joiners.equal(Lesson::getRoom))
                .filter((a, b) -> a.getTimeSlot() != null && a.getRoom() != null)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Exam room conflict");
    }

    /**
     * HARD: Καθηγητής δεν μπορεί σε δύο εξετάσεις ταυτόχρονα.
     */
    Constraint teacherConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getTimeSlot))
                .filter((a, b) ->
                        a.getTimeSlot() != null
                        && !a.getCourseId().equals(b.getCourseId())
                        && a.sharesTeacher(b))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Exam teacher conflict");
    }

    /**
 * HARD: Δύο υποχρεωτικές εξετάσεις του ίδιου έτους δεν μπορούν
 * να τοποθετηθούν την ίδια ημερομηνία.
 *
 * Χρησιμοποιεί dayKey: για exams = "2026-09-01" (specificDate).
 */
Constraint requiredSameYearSameDay(ConstraintFactory factory) {
    return factory.forEachUniquePair(Lesson.class,
                    Joiners.equal(Lesson::getStudyYear),
                    Joiners.equal(l -> l.getTimeSlot() != null
                            ? l.getTimeSlot().getDayKey() : ""))
            .filter((a, b) ->
                    a.getTimeSlot() != null
                    && b.getTimeSlot() != null
                    && a.isRequired()
                    && b.isRequired()
                    && !a.getCourseId().equals(b.getCourseId()))
            .penalize(HardSoftScore.ofHard(5))
            .asConstraint("Required same-year exams on same day");
}

    // ===================== SOFT =====================

/**
 * SOFT: Αν η αίθουσα έχει μικρότερη χωρητικότητα από τους αναμενόμενους
 * φοιτητές, δεν μπλοκάρουμε την τοποθέτηση, αλλά τη βαθμολογούμε χειρότερα.
 *
 * Αυτό είναι σωστό για εξεταστική, γιατί αρκετές εξετάσεις μπορούν πρακτικά
 * να μοιραστούν σε περισσότερες από μία αίθουσες.
 */
Constraint roomCapacityMatch(ConstraintFactory factory) {
    return factory.forEach(Lesson.class)
            .filter(l -> l.getRoom() != null
                    && l.getExpectedStudents() > 0
                    && l.getRoom().getCapacity() > 0
                    && l.getExpectedStudents() > l.getRoom().getCapacity())
            .penalize(HardSoftScore.ONE_SOFT,
                    l -> l.getExpectedStudents() - l.getRoom().getCapacity())
            .asConstraint("Exam room capacity exceeded");
}

    /**
 * SOFT: Οι υποχρεωτικές εξετάσεις του ίδιου έτους καλό είναι να είναι
 * απλωμένες και όχι σε συνεχόμενες ημερομηνίες.
 */
Constraint spreadSameYear(ConstraintFactory factory) {
    return factory.forEachUniquePair(Lesson.class,
                    Joiners.equal(Lesson::getStudyYear))
            .filter((a, b) ->
                    a.getTimeSlot() != null
                    && b.getTimeSlot() != null
                    && a.isRequired()
                    && b.isRequired()
                    && !a.getCourseId().equals(b.getCourseId())
                    && areConsecutiveDays(a.getTimeSlot().getDayKey(),
                                         b.getTimeSlot().getDayKey()))
            .penalize(HardSoftScore.ONE_SOFT, (a, b) -> 3)
            .asConstraint("Spread same-year exams");
}

    /**
     * SOFT: Μαθήματα Ομάδας Α κατεύθυνσης σε διαφορετικές μέρες.
     */
    Constraint directionGroupADifferentDays(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(l -> l.getTimeSlot() != null
                                ? l.getTimeSlot().getDayKey() : ""))
                .filter((a, b) ->
        a.getTimeSlot() != null
        && b.getTimeSlot() != null
        && !a.getCourseId().equals(b.getCourseId())
        && shareDirectionGroupA(a.getCourseCode(), b.getCourseCode()))
                .penalize(HardSoftScore.ONE_SOFT, (a, b) -> 5)
                .asConstraint("Direction Group A exams on same day");
    }

    /**
     * SOFT: Αποφυγή πολλών εξετάσεων ίδιου καθηγητή ίδια μέρα.
     */
    Constraint teacherMultipleExamsSameDay(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(l -> l.getTimeSlot() != null
                                ? l.getTimeSlot().getDayKey() : ""))
                .filter((a, b) ->
        a.getTimeSlot() != null
        && b.getTimeSlot() != null
        && !a.getCourseId().equals(b.getCourseId())
        && a.sharesTeacher(b))
                .penalize(HardSoftScore.ONE_SOFT, (a, b) -> 2)
                .asConstraint("Teacher multiple exams same day");
    }

    /**
     * SOFT: Μεγάλα μαθήματα (>150 φοιτητές) στο πρωινό slot (09:00).
     */
    Constraint preferMorningForLargeCourses(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null
                        && l.getExpectedStudents() > 150
                        && l.getTimeSlot().getStartHour() >= 15)
                .penalize(HardSoftScore.ONE_SOFT, l -> 1)
                .asConstraint("Prefer morning for large exams");
    }

    // ===================== HELPERS =====================

    private boolean shareDirectionGroupA(String codeA, String codeB) {
        for (java.util.Set<String> groupA : DirectionRegistry.GROUP_A.values()) {
            if (groupA.contains(codeA) && groupA.contains(codeB)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ελέγχει αν δύο dayKeys αντιπροσωπεύουν γειτονικές μέρες.
     * dayKey format: "2026-01-15"
     */
    private static boolean areConsecutiveDays(String dayKeyA, String dayKeyB) {
        try {
            java.time.LocalDate dateA = java.time.LocalDate.parse(dayKeyA);
            java.time.LocalDate dateB = java.time.LocalDate.parse(dayKeyB);
            long daysBetween = Math.abs(
                    java.time.temporal.ChronoUnit.DAYS.between(dateA, dateB));
            return daysBetween == 1;
        } catch (Exception e) {
            return false;
        }
    }
}