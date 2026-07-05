package gr.upatras.ceid.timetable.solver;

import java.util.List;

/**
 * Μία SOFT παραβίαση constraint, όπως την αναφέρει το score-explanation του solver
 * (Timefold {@code SolutionManager.explain}). Παράλληλο του {@link HardViolation} για
 * το soft μονοπάτι — χρησιμοποιείται ΜΟΝΟ για ΕΜΦΑΝΙΣΗ (warnings), καμία επίδραση στο
 * solve/βάρη.
 *
 * @param constraintName το όνομα του constraint (π.χ. "Teacher not in preferred slot")
 * @param assignmentIds  τα ids των αναθέσεων που ενοχοποιούνται (Lesson.id := assignment.id)
 * @param softImpact     η αρνητική soft ποινή του συγκεκριμένου match (π.χ. -3)
 */
public record SoftViolation(
        String constraintName,
        List<Long> assignmentIds,
        int softImpact
) {}
