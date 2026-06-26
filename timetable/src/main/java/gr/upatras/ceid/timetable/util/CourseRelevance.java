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
}
