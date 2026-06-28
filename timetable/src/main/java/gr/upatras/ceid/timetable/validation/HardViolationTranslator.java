package gr.upatras.ceid.timetable.validation;

import gr.upatras.ceid.timetable.solver.ConstraintCodeMapping;
import gr.upatras.ceid.timetable.solver.HardViolation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Φ-SV2b-i: μετατρέπει τα {@link HardViolation} (Φ-SV1 engine) σε report issues
 * (code / referenceId / assignmentIds / message). ΠΛΗΡΩΣ unit-testable: τα live
 * δεδομένα έρχονται μέσω {@code lookup} (assignmentId -> {@link AssignmentView}),
 * τον οποίο θα δώσει η Φάση 2b-ii. ΚΑΜΙΑ live σύνδεση εδώ — ο translator δεν
 * καλείται από κανένα production flow ακόμη.
 *
 * Αποφάσεις:
 *  - D1 (referenceId): aggregate codes -> null· τα υπόλοιπα -> min(assignmentIds)
 *    (ascending sort για ντετερμινισμό· mirror του παλιού «a = πρώτο»). Πάντα
 *    προστίθεται additive πεδίο "assignmentIds" (sorted, νέο).
 *  - D2: 2 NEW codes (TEACHER_BLOCKED / ROOM_BLOCKED) με δικά τους μηνύματα.
 *  - D3: TEACHER_CONFLICT names = τομή των teacherNames· κενή τομή -> «κοινός διδάσκων».
 *
 * Aggregates (DAILY_LECTURE_LIMIT / LUNCH_BREAK_REQUIRED): ο Φ-SV1 engine τα παίρνει
 * από group-by constraints που indict ΜΟΝΟ το group-key (studyYear/day/count) — ΟΧΙ
 * Lessons (επιβεβαιωμένο με probe). Άρα {@code assignmentIds} είναι κενό και δεν
 * υπάρχει view για άντληση year/day/N -> generic μήνυμα (documented fallback).
 */
public final class HardViolationTranslator {

    private HardViolationTranslator() {}

    /** Codes χωρίς συγκεκριμένο assignment ως «σημείο» (referenceId = null) — D1. */
    private static final Set<String> AGGREGATE_CODES =
            Set.of("DAILY_LECTURE_LIMIT", "LUNCH_BREAK_REQUIRED");

    private static final Map<String, String> GREEK_DAYS = Map.of(
            "MONDAY", "Δευτέρα",
            "TUESDAY", "Τρίτη",
            "WEDNESDAY", "Τετάρτη",
            "THURSDAY", "Πέμπτη",
            "FRIDAY", "Παρασκευή",
            "SATURDAY", "Σάββατο",
            "SUNDAY", "Κυριακή");

    /** violations -> report issues. lookup: assignmentId -> AssignmentView (live, από 2b-ii). */
    public static List<Map<String, Object>> translate(
            List<HardViolation> violations,
            Function<Long, AssignmentView> lookup) {
        List<Map<String, Object>> issues = new ArrayList<>();
        if (violations == null) return issues;

        for (HardViolation v : violations) {
            String code = ConstraintCodeMapping.codeFor(v.constraintName()).orElse(null);
            if (code == null) continue; // defensive skip· το completeness gate (2a) εγγυάται mapping

            List<Long> ids = new ArrayList<>(v.assignmentIds() != null ? v.assignmentIds() : List.of());
            Collections.sort(ids); // ντετερμινισμός

            boolean aggregate = AGGREGATE_CODES.contains(code);
            Long referenceId = (aggregate || ids.isEmpty()) ? null : ids.get(0);

            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("code", code);
            issue.put("referenceId", referenceId);
            issue.put("assignmentIds", ids);
            issue.put("message", buildMessage(code, ids, v.contextFacts(), lookup));
            issues.add(issue);
        }
        return issues;
    }

    private static String buildMessage(String code, List<Long> ids,
                                       List<Object> contextFacts,
                                       Function<Long, AssignmentView> lookup) {
        AssignmentView a = ids.isEmpty() ? null : safeView(ids.get(0), lookup);
        AssignmentView b = ids.size() < 2 ? null : safeView(ids.get(1), lookup);

        return switch (code) {
            case "ROOM_CONFLICT" ->
                    "Η αίθουσα " + roomCode(a) + " έχει δύο μαθήματα την ίδια ώρα: "
                            + courseName(a) + " και " + courseName(b) + ".";
            case "TEACHER_CONFLICT" ->
                    "Σύγκρουση διδάσκοντα: " + sharedTeachers(a, b)
                            + " έχει δύο μαθήματα την ίδια ώρα: "
                            + courseName(a) + " και " + courseName(b) + ".";
            case "SAME_COURSE_SAME_SLOT" ->
                    "Το ίδιο μάθημα έχει τοποθετηθεί δύο φορές στην ίδια ώρα: " + courseName(a) + ".";
            case "REQUIRED_YEAR_CONFLICT" ->
                    "Σύγκρουση υποχρεωτικών μαθημάτων ίδιου έτους την ίδια ώρα: "
                            + courseName(a) + " και " + courseName(b) + ".";
            case "REQUIRED_YEAR_EXAM_SAME_DATE" ->
                    "Σύγκρουση υποχρεωτικών εξετάσεων ίδιου έτους την ίδια ημερομηνία: "
                            + courseName(a) + " και " + courseName(b) + ".";
            case "LAB_ROOM_REQUIRED" ->
                    "Το εργαστήριο " + courseName(a) + " πρέπει να βρίσκεται σε εργαστηριακή αίθουσα.";
            case "FIRST_YEAR_ROOM" ->
                    "Το μάθημα 1ου έτους " + courseName(a) + " πρέπει να μπει στο Αμφιθέατρο Γ.";
            case "REQUIRED_ROOM" ->
                    "Το υποχρεωτικό μάθημα " + courseName(a) + " πρέπει να βρίσκεται σε αίθουσα Β ή Γ.";
            // Aggregates: ερμηνεία του group-key (contextFacts) σε ΠΛΗΡΕΣ μήνυμα (έτος/ημέρα/N)·
            // defensive fallback στο generic αν το shape δεν ταιριάζει (ΠΟΤΕ crash).
            case "DAILY_LECTURE_LIMIT" -> {
                AggregateInfo agg = parseAggregate(contextFacts);
                yield agg != null
                        ? "Το " + agg.studyYear() + "ο έτος έχει " + agg.count()
                                + " ώρες θεωρίας την ημέρα " + gd(agg.day()) + ". Το μέγιστο επιτρεπτό είναι 6."
                        : "Υπέρβαση του ημερήσιου ορίου των 6 ωρών θεωρίας για υποχρεωτικά μαθήματα ίδιου έτους.";
            }
            case "LUNCH_BREAK_REQUIRED" -> {
                AggregateInfo agg = parseAggregate(contextFacts);
                yield agg != null
                        ? "Το " + agg.studyYear() + "ο έτος δεν έχει ελεύθερη ώρα για φαγητό μεταξύ "
                                + "12:00-15:00 την ημέρα " + gd(agg.day()) + "."
                        : "Υποχρεωτικά μαθήματα ίδιου έτους καλύπτουν όλο το μεσημεριανό διάστημα 12:00-15:00, "
                                + "χωρίς ελεύθερη ώρα για φαγητό.";
            }
            // NEW (D2)
            case "TEACHER_BLOCKED" ->
                    "Ο διδάσκων του μαθήματος " + courseName(a)
                            + " είναι δεσμευμένος τη συγκεκριμένη ώρα" + timeSuffix(a) + ".";
            case "ROOM_BLOCKED" ->
                    "Η αίθουσα " + roomCode(a) + " είναι δεσμευμένη τη συγκεκριμένη ώρα: "
                            + courseName(a) + timeSuffix(a) + ".";
            default -> "Παραβίαση περιορισμού: " + code + ".";
        };
    }

    // ----- helpers -----

    private static AssignmentView safeView(Long id, Function<Long, AssignmentView> lookup) {
        if (id == null || lookup == null) return null;
        return lookup.apply(id);
    }

    private static String courseName(AssignmentView v) {
        return v != null && v.courseName() != null ? v.courseName() : "(άγνωστο μάθημα)";
    }

    private static String roomCode(AssignmentView v) {
        return v != null && v.roomCode() != null ? v.roomCode() : "(άγνωστη αίθουσα)";
    }

    /** D3: τομή των teacherNames των δύο όψεων· κενή τομή -> «κοινός διδάσκων». */
    private static String sharedTeachers(AssignmentView a, AssignmentView b) {
        List<String> an = a != null && a.teacherNames() != null ? a.teacherNames() : List.of();
        List<String> bn = b != null && b.teacherNames() != null ? b.teacherNames() : List.of();
        LinkedHashSet<String> common = new LinkedHashSet<>(an);
        common.retainAll(new LinkedHashSet<>(bn));
        return common.isEmpty() ? "κοινός διδάσκων" : String.join(", ", common);
    }

    /** " (Δευτέρα 9:00)" αν υπάρχουν day/hour· ασφαλές παράλειψη αν λείπουν. */
    private static String timeSuffix(AssignmentView v) {
        if (v == null) return "";
        String day = v.dayOfWeekName() != null
                ? GREEK_DAYS.getOrDefault(v.dayOfWeekName(), v.dayOfWeekName()) : null;
        Integer h = v.startHour();
        if (day != null && h != null) return " (" + day + " " + h + ":00)";
        if (day != null) return " (" + day + ")";
        if (h != null) return " (" + h + ":00)";
        return "";
    }

    /** Ελληνική ημέρα (π.χ. "MONDAY" -> "Δευτέρα")· null/άγνωστο -> passthrough/κενό. */
    private static String gd(String day) {
        if (day == null) return "";
        return GREEK_DAYS.getOrDefault(day, day);
    }

    /** Ερμηνεία του group-key των aggregates: [studyYear:Integer, day:String, count:Integer]. */
    private record AggregateInfo(int studyYear, String day, int count) {}

    /**
     * DEFENSIVE parse του contextFacts ενός aggregate violation. Αν το shape δεν ταιριάζει
     * (π.χ. κενό, ή ο engine αλλάξει) -> null ώστε ο caller να πέσει σε generic fallback
     * (ΠΟΤΕ crash). Order: groupBy(studyYear, day, count).
     */
    private static AggregateInfo parseAggregate(List<Object> facts) {
        if (facts != null && facts.size() >= 3
                && facts.get(0) instanceof Integer y
                && facts.get(1) instanceof String d
                && facts.get(2) instanceof Integer c) {
            return new AggregateInfo(y, d, c);
        }
        return null;
    }
}
