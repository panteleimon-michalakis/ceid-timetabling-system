package gr.upatras.ceid.timetable.solver;

import java.util.List;

/**
 * Μία HARD παραβίαση constraint, όπως την αναφέρει το score-explanation του solver
 * (Timefold {@code SolutionManager.explain}). Χρησιμοποιείται από τη μηχανή
 * ανάλυσης ({@code SolverService.analyzeHardViolations}) για να εκθέσει τις
 * πραγματικές solver-εκφράσιμες συγκρούσεις ενός αποθηκευμένου προγράμματος.
 *
 * @param constraintName το όνομα του constraint (π.χ. "Teacher blocked slot")
 * @param assignmentIds  τα ids των αναθέσεων που ενοχοποιούνται (Lesson.id := assignment.id)
 * @param hardImpact     η αρνητική hard ποινή του συγκεκριμένου match (π.χ. -1, -10)
 * @param contextFacts   raw non-Lesson indicted facts ΣΤΗ ΣΕΙΡΑ τους (π.χ. το group-key
 *                       [studyYear, day, count] των aggregate constraints). Ο engine τα
 *                       εξάγει γενικά (constraint-agnostic)· η ερμηνεία γίνεται στον translator.
 */
public record HardViolation(
        String constraintName,
        List<Long> assignmentIds,
        int hardImpact,
        List<Object> contextFacts
) {
    /** Backwards-compatible: παλιά 3-args call sites (tests) → κενά contextFacts. */
    public HardViolation(String constraintName, List<Long> assignmentIds, int hardImpact) {
        this(constraintName, assignmentIds, hardImpact, List.of());
    }
}
