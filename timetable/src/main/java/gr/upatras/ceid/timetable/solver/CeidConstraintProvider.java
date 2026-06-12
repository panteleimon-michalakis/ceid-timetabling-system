package gr.upatras.ceid.timetable.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.Joiners;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.count;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.countDistinct;

public class CeidConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
        // HARD — βασικοί κανόνες
        roomConflict(factory),
        teacherConflict(factory),
        sameCourseConflict(factory),
        requiredSameYearConflict(factory),
        labInLabRoom(factory),
        firstYearOnlyGamma(factory),
        requiredOnlyBorG(factory),
        dailyLectureLimit(factory),
        lunchBreakFirstThreeYears(factory),

        // HARD — διαθεσιμότητα καθηγητών
        teacherBlockedSlot(factory),

        // HARD — δεσμευμένες ώρες αιθουσών
        roomBlockedSlot(factory),

        // SOFT
        roomCapacityMatch(factory),
        preferNormalHours(factory),
        avoidOverloadedDay(factory),
        teacherPreferredSlot(factory),
        requiredSameYearGaps(factory),

	directionGroupAConflict(factory),
        };
    }

    // HARD — βασικοί κανόνες

    Constraint roomConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getTimeSlot),
                        Joiners.equal(Lesson::getRoom))
                .filter((a, b) -> a.getTimeSlot() != null && a.getRoom() != null)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Room conflict");
    }

    Constraint teacherConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getTimeSlot))
                .filter((a, b) ->
                        a.getTimeSlot() != null &&
                        !a.getCourseId().equals(b.getCourseId()) &&
                        a.sharesTeacher(b))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Teacher conflict");
    }

    Constraint sameCourseConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getCourseId),
                        Joiners.equal(Lesson::getTimeSlot))
                .filter((a, b) -> a.getTimeSlot() != null)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Same course conflict");
    }

    Constraint requiredSameYearConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getTimeSlot),
                        Joiners.equal(Lesson::getStudyYear))
                .filter((a, b) ->
                        a.getTimeSlot() != null &&
                        a.isRequired() && b.isRequired() &&
                        !a.getCourseId().equals(b.getCourseId()))
                .penalize(HardSoftScore.ofHard(5))
                .asConstraint("Required same-year conflict");
    }

    Constraint labInLabRoom(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.isLab() && l.getRoom() != null && !l.getRoom().isLab())
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Lab must be in LAB room");
    }

    Constraint firstYearOnlyGamma(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.getStudyYear() == 1
                        && (l.isLecture() || l.isTutorial())
                        && l.getRoom() != null
                        && !"Γ".equals(l.getRoom().getCode()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("First year only in room G");
    }

    Constraint requiredOnlyBorG(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.isRequired()
                        && (l.isLecture() || l.isTutorial())
                        && l.getRoom() != null
                        && !"Β".equals(l.getRoom().getCode())
                        && !"Γ".equals(l.getRoom().getCode()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Required courses only in B or G");
    }

    Constraint dailyLectureLimit(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null
                        && l.isLecture()
                        && l.isRequired())
                .groupBy(
                        Lesson::getStudyYear,
                        l -> l.getTimeSlot().getDayOfWeek(),
                        count()
                )
                .filter((studyYear, day, lectureCount) -> lectureCount > 6)
                .penalize(
                        HardSoftScore.ONE_HARD,
                        (studyYear, day, lectureCount) -> lectureCount - 6
                )
                .asConstraint("Daily lecture limit for required courses");
    }

    Constraint lunchBreakFirstThreeYears(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null
                        && l.getStudyYear() >= 1
                        && l.getStudyYear() <= 3
                        && l.isRequired()
                        && l.getTimeSlot().isLunchHour())
                .groupBy(
                        Lesson::getStudyYear,
                        l -> l.getTimeSlot().getDayOfWeek(),
                        countDistinct(l -> l.getTimeSlot().getStartHour())
                )
                .filter((studyYear, day, occupiedLunchHours) -> occupiedLunchHours >= 3)
                .penalize(HardSoftScore.ofHard(3))
                .asConstraint("Lunch break required for first three years");
    }

    // HARD — διαθεσιμότητα καθηγητών

    Constraint teacherBlockedSlot(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null)
                .filter(TeacherAvailabilityConstraints::isBlocked)
                .penalize(HardSoftScore.ofHard(10))
                .asConstraint("Teacher blocked slot");
    }

    /**
     * SOFT (κανόνας τμήματος #1): Τα υποχρεωτικά μαθήματα του ίδιου έτους να
     * μην αφήνουν κενά στο ημερήσιο πρόγραμμα — και ειδικά όχι κενά άνω των
     * 2 ωρών. Μετρώνται Θεωρίες/Φροντιστήρια· τα εργαστήρια εξαιρούνται,
     * γιατί γίνονται σε τμήματα και δεν ορίζουν το κοινό πρόγραμμα του έτους.
     * Ποινή: 2/ώρα κενού + επιπλέον 4/ώρα πέρα από το όριο των 2 ωρών.
     * Αθροιζόμενη σε όλες τις μέρες, αποθαρρύνει και τα πολλά κενά/εβδομάδα.
     */
    Constraint requiredSameYearGaps(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null
                        && l.isRequired()
                        && (l.isLecture() || l.isTutorial()))
                .groupBy(Lesson::getStudyYear,
                         l -> l.getTimeSlot().getDayOfWeek(),
                         ConstraintCollectors.toList())
                .penalize(HardSoftScore.ONE_SOFT,
                        (studyYear, day, lessons) -> dailyGapPenalty(lessons))
                .asConstraint("Required same-year daily gaps");
    }

    /** Ποινή κενών μίας ημέρας για τα μαθήματα ενός έτους (1ωρα weekly slots). */
    static int dailyGapPenalty(java.util.List<Lesson> lessons) {
        boolean[] occupied = new boolean[24];
        int min = 24, max = -1;
        for (Lesson l : lessons) {
            int h = l.getTimeSlot().getStartHour();
            if (h < 0 || h > 23) continue;
            occupied[h] = true;
            if (h < min) min = h;
            if (h > max) max = h;
        }
        if (max <= min) return 0;
        int penalty = 0;
        int gap = 0;
        for (int h = min; h <= max; h++) {
            if (occupied[h]) {
                if (gap > 0) {
                    penalty += 2 * gap + (gap > 2 ? 4 * (gap - 2) : 0);
                    gap = 0;
                }
            } else {
                gap++;
            }
        }
        return penalty;
    }

    /** HARD: Η αίθουσα δεν διατίθεται σε δεσμευμένες ώρες (room_constraints). */
    Constraint roomBlockedSlot(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(RoomAvailabilityConstraints::isBlockedWeekly)
                .penalize(HardSoftScore.ofHard(10))
                .asConstraint("Room blocked slot");
    }

    // SOFT

    Constraint roomCapacityMatch(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.getRoom() != null && l.getExpectedStudents() > l.getRoom().getCapacity())
                .penalize(HardSoftScore.ONE_SOFT,
                        l -> l.getExpectedStudents() - l.getRoom().getCapacity())
                .asConstraint("Room capacity exceeded");
    }

    Constraint preferNormalHours(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.getTimeSlot().getStartHour() >= 18)
                .penalize(HardSoftScore.ONE_SOFT, l -> 2)
                .asConstraint("Prefer normal hours");
    }

    Constraint avoidOverloadedDay(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.isLecture() && l.isRequired())
                .groupBy(
                        Lesson::getStudyYear,
                        l -> l.getTimeSlot().getDayOfWeek(),
                        count()
                )
                .filter((studyYear, day, lectureCount) -> lectureCount > 4)
                .penalize(HardSoftScore.ONE_SOFT,
                        (studyYear, day, lectureCount) -> lectureCount - 4)
                .asConstraint("Avoid overloaded day");
    }

    Constraint teacherPreferredSlot(ConstraintFactory factory) {
        return factory.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null)
                .filter(TeacherAvailabilityConstraints::isNotPreferred)
                .penalize(HardSoftScore.ONE_SOFT, l -> 3)
                .asConstraint("Teacher not in preferred slot");
    }

     /**
     * SOFT: Τα μαθήματα Ομάδας Α κάθε κατεύθυνσης (Κ1-Κ6) δεν πρέπει
     * να συμπίπτουν χρονικά, ώστε ο φοιτητής να μπορεί να τα παρακολουθεί.
     */
    Constraint directionGroupAConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getTimeSlot))
                .filter((a, b) ->
                        a.getTimeSlot() != null
                        && !a.getCourseId().equals(b.getCourseId())
                        && shareDirectionGroupA(a.getCourseCode(), b.getCourseCode()))
                .penalize(HardSoftScore.ONE_SOFT, (a, b) -> 5)
                .asConstraint("Direction Group A conflict");
    }

    private boolean shareDirectionGroupA(String codeA, String codeB) {
        for (java.util.Set<String> groupA : DirectionRegistry.GROUP_A.values()) {
            if (groupA.contains(codeA) && groupA.contains(codeB)) {
                return true;
            }
        }
        return false;
    }
}