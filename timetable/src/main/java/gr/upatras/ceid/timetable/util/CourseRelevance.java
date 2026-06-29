package gr.upatras.ceid.timetable.util;

import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.Timetable;

public final class CourseRelevance {

    private CourseRelevance() {}

    public static boolean isRelevant(Course course, Timetable timetable) {
        if (course == null || timetable == null) return false;
        if (course.getSemesterType() == null || timetable.getSemesterType() == null) return true;
        if (course.getSemesterType().name().equals("BOTH")) return true;
        if (timetable.getSemesterType().name().equals("SEPTEMBER")) return true;
        return course.getSemesterType().name().equals(timetable.getSemesterType().name());
    }

    /**
     * Schedulability predicate: το μάθημα θεωρείται "τοποθετήσιμο" σε αυτό το πρόγραμμα.
     * Ταυτόσημη null-handling με τον solver (null active/visible => θεωρείται ναι).
     * Αυτή είναι η ΜΟΝΑΔΙΚΗ πηγή για: (α) freeze του scope, (β) solver relevance,
     * (γ) auto-schedule. Έτσι το frozen scope == ό,τι επιχειρεί ο solver (BL-10).
     */
    public static boolean isSchedulable(Course course, Timetable timetable) {
        if (course == null || timetable == null) return false;
        if (course.getActive() != null && !course.getActive()) return false;
        if (course.getVisibleInTimetable() != null && !course.getVisibleInTimetable()) return false;
        return isRelevant(course, timetable);
    }
}
