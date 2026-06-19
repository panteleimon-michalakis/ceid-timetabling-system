package gr.upatras.ceid.timetable.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
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
            teacherConflict(factory),
            roomBlockedSlot(factory),
            requiredSameYearSameDay(factory),

            // SOFT
            sharedRoomCapacity(factory),
            sameYearSameDay(factory),
            dailyLoadBalance(factory),
            preferDistinctRoomsWithinSlot(factory),
            requiredBeforeElectives(factory),
            preferredExamRoom(factory),
            preferredExamHour(factory),
            spreadSameYear(factory),
            directionGroupADifferentDays(factory),
            teacherMultipleExamsSameDay(factory),
            preferMorningForLargeCourses(factory),
    };
}


    // ===================== HARD =====================

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
                .penalize(SolverWeights.hard("EXAM_TEACHER_CONFLICT"))
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
            .penalize(SolverWeights.hard("EXAM_REQUIRED_SAME_YEAR_SAME_DAY"))
            .asConstraint("Required same-year exams on same day");
}

    /**
     * HARD: Η αίθουσα δεν διατίθεται σε δεσμευμένες ώρες (room_constraints).
     * Τα exam slots είναι 3ωρα — ελέγχεται όλο το παράθυρο.
     */
    Constraint roomBlockedSlot(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(RoomAvailabilityConstraints::isBlockedExam)
                .penalize(SolverWeights.hard("EXAM_ROOM_BLOCKED"))
                .asConstraint("Exam room blocked slot");
    }

    // ===================== SOFT =====================

    /**
     * A6 SOFT(4): Προτιμώμενη αίθουσα εξέτασης (φόρμες διδασκόντων).
     * Ποινή όταν το μάθημα έχει δηλωμένες προτιμήσεις και τοποθετηθεί
     * σε αίθουσα εκτός λίστας. Βάρος 4: χαμηλότερα από τους κανόνες
     * τμήματος sameYearSameDay(6) και direction(5), ψηλότερα από όλα
     * τα tie-breakers (teacher/day 2, load/distinct 1).
     */
    Constraint preferredExamRoom(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.getRoom() != null && l.hasRoomPreference()
                        && !l.getPreferredRoomCodes().contains(l.getRoom().getCode()))
                .penalize(SolverWeights.soft("EXAM_PREFERRED_ROOM"))
                .asConstraint("Preferred exam room");
    }

    /** A6 SOFT(4): Προτιμώμενη ώρα έναρξης εξέτασης (9/12/15/18). */
    Constraint preferredExamHour(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.hasHourPreference()
                        && !l.getPreferredStartHours().contains(l.getTimeSlot().getStartHour()))
                .penalize(SolverWeights.soft("EXAM_PREFERRED_HOUR"))
                .asConstraint("Preferred exam start hour");
    }


    /**
     * SOFT: Κανόνας τμήματος — πολλαπλές εξετάσεις ΕΠΙΤΡΕΠΕΤΑΙ να μοιράζονται
     * την ίδια αίθουσα την ίδια ώρα. Ποινή υπάρχει μόνο όταν το ΑΘΡΟΙΣΜΑ των
     * αναμενόμενων φοιτητών όλων των εξετάσεων της αίθουσας ξεπερνά τη
     * χωρητικότητά της, αναλογικά με το μέγεθος της υπέρβασης.
     */
    Constraint sharedRoomCapacity(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.getRoom() != null && l.getTimeSlot() != null)
                .groupBy(Lesson::getRoom,
                         Lesson::getTimeSlot,
                         ConstraintCollectors.sum(Lesson::getExpectedStudents))
                .filter((room, slot, totalStudents) ->
                        room.getCapacity() > 0 && totalStudents > room.getCapacity())
                .penalize(HardSoftScore.ONE_SOFT,
                        (room, slot, totalStudents) -> SolverWeights.w("EXAM_SHARED_ROOM_CAPACITY") * (totalStudents - room.getCapacity()))
                .asConstraint("Shared exam room over capacity");
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
            .penalize(HardSoftScore.ONE_SOFT, (a, b) -> SolverWeights.w("EXAM_SPREAD_SAME_YEAR"))
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
                .penalize(HardSoftScore.ONE_SOFT, (a, b) -> SolverWeights.w("EXAM_DIRECTION_GROUP_A_SAME_DAY"))
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
                .penalize(HardSoftScore.ONE_SOFT, (a, b) -> SolverWeights.w("EXAM_TEACHER_MULTIPLE_SAME_DAY"))
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
                .penalize(HardSoftScore.ONE_SOFT, l -> SolverWeights.w("EXAM_PREFER_MORNING_LARGE"))
                .asConstraint("Prefer morning for large exams");
    }

    /**
     * SOFT: Εξετάσεις του ίδιου έτους αποθαρρύνονται την ίδια μέρα όταν
     * τουλάχιστον η μία είναι επιλογής/ΓΠ, ώστε φοιτητής του ίδιου έτους
     * να μην βρεθεί με πολλές εξετάσεις μαζεμένες. Το ζεύγος
     * υποχρεωτικό+υποχρεωτικό καλύπτεται ήδη από το HARD
     * requiredSameYearSameDay, οπότε εξαιρείται εδώ.
     */
    Constraint sameYearSameDay(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getStudyYear),
                        Joiners.equal(l -> l.getTimeSlot() != null
                                ? l.getTimeSlot().getDayKey() : ""))
                .filter((a, b) ->
                        a.getTimeSlot() != null
                        && b.getTimeSlot() != null
                        && !(a.isRequired() && b.isRequired())
                        && !a.getCourseId().equals(b.getCourseId()))
                .penalize(HardSoftScore.ONE_SOFT, (a, b) -> SolverWeights.w("EXAM_SAME_YEAR_SAME_DAY"))
                .asConstraint("Same-year exams stacked on same day");
    }

    /**
     * SOFT: Όταν στο ίδιο slot συνυπάρχουν πολλές εξετάσεις, προτιμάμε να
     * κατανέμονται σε διαφορετικές αίθουσες όσο υπάρχουν διαθέσιμες.
     * Το μοίρασμα αίθουσας παραμένει επιτρεπτό (κανόνας τμήματος #3) —
     * η ποινή είναι σκόπιμα ελαφριά (1) ώστε να λειτουργεί μόνο ως
     * tie-breaker κατανομής, ποτέ ενάντια σε σημαντικότερους κανόνες.
     */
    Constraint preferDistinctRoomsWithinSlot(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getTimeSlot),
                        Joiners.equal(Lesson::getRoom))
                .filter((a, b) -> a.getTimeSlot() != null && a.getRoom() != null)
                .penalize(SolverWeights.soft("EXAM_PREFER_DISTINCT_ROOMS"))
                .asConstraint("Prefer distinct exam rooms within slot");
    }

    /**
     * SOFT: Εξισορρόπηση φόρτου ανά ημέρα. Κάθε ζεύγος εξετάσεων στην ίδια
     * ημερομηνία κοστίζει 1, άρα μέρα με n εξετάσεις κοστίζει n(n-1)/2.
     * Η τετραγωνική αύξηση σπρώχνει τον solver να απλώσει το πρόγραμμα
     * σε όλο το διαθέσιμο εύρος (~4 εβδομάδες) αντί να στοιβάζει τα
     * μαθήματα επιλογής στις πρώτες μέρες — διασπορά που πριν επιβαλλόταν
     * έμμεσα (και κατά λάθος) από το hard room conflict.
     */
    Constraint dailyLoadBalance(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(l -> l.getTimeSlot() != null
                                ? l.getTimeSlot().getDayKey() : ""))
                .filter((a, b) -> a.getTimeSlot() != null && b.getTimeSlot() != null)
                .penalize(SolverWeights.soft("EXAM_DAILY_LOAD_BALANCE"))
                .asConstraint("Daily exam load balance");
    }

   /**
     * SOFT (προαιρετική προτίμηση γραμματείας): τα υποχρεωτικά μαθήματα
     * προτιμάται να εξετάζονται νωρίτερα από τα επιλογής. Ποινή 1 για κάθε
     * ζεύγος (υποχρεωτικό, επιλογής) όπου το υποχρεωτικό είναι αργότερα.
     */
    Constraint requiredBeforeElectives(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.isRequired())
                .join(Lesson.class)
                .filter((req, el) -> el.getTimeSlot() != null
                        && !el.isRequired()
                        && req.getTimeSlot().getDayKey()
                              .compareTo(el.getTimeSlot().getDayKey()) > 0)
                .penalize(SolverWeights.soft("EXAM_REQUIRED_BEFORE_ELECTIVES"))
                .asConstraint("Required exams before electives");
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