package gr.upatras.ceid.timetable.solver;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class Lesson {

    @ai.timefold.solver.core.api.domain.lookup.PlanningId
    private Long id;
    private Long courseId;
    private String courseCode;
    private String courseName;
    private int studyYear;
    private String courseType;     // REQUIRED, ELECTIVE, etc.
    private String assignmentType; // LECTURE, TUTORIAL, LAB
    private int expectedStudents;
    private String semesterType;   // FALL, SPRING
    private int semester;          // 1-8

    @PlanningVariable(valueRangeProviderRefs = "timeSlotRange")
    private SolverTimeSlot timeSlot;

    @PlanningVariable(valueRangeProviderRefs = "roomRange")
    private SolverRoom room;

    // Teacher keys for conflict checking
    private java.util.Set<String> teacherKeys = new java.util.HashSet<>();

    public Lesson() {}

    public Lesson(Long id, Long courseId, String courseCode, String courseName,
                  int studyYear, String courseType, String assignmentType,
                  int expectedStudents, String semesterType, int semester) {
        this.id = id;
        this.courseId = courseId;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.studyYear = studyYear;
        this.courseType = courseType;
        this.assignmentType = assignmentType;
        this.expectedStudents = expectedStudents;
        this.semesterType = semesterType;
        this.semester = semester;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public int getStudyYear() { return studyYear; }
    public void setStudyYear(int studyYear) { this.studyYear = studyYear; }

    public String getCourseType() { return courseType; }
    public void setCourseType(String courseType) { this.courseType = courseType; }

    public String getAssignmentType() { return assignmentType; }
    public void setAssignmentType(String assignmentType) { this.assignmentType = assignmentType; }

    public int getExpectedStudents() { return expectedStudents; }
    public void setExpectedStudents(int expectedStudents) { this.expectedStudents = expectedStudents; }

    public String getSemesterType() { return semesterType; }
    public void setSemesterType(String semesterType) { this.semesterType = semesterType; }

    public SolverTimeSlot getTimeSlot() { return timeSlot; }
    public void setTimeSlot(SolverTimeSlot timeSlot) { this.timeSlot = timeSlot; }

    public SolverRoom getRoom() { return room; }
    public void setRoom(SolverRoom room) { this.room = room; }

    public java.util.Set<String> getTeacherKeys() { return teacherKeys; }
    public void setTeacherKeys(java.util.Set<String> teacherKeys) { this.teacherKeys = teacherKeys; }

    // A6: Προτιμήσεις εξεταστικής — κενά σύνολα = καμία προτίμηση.
    private java.util.Set<String> preferredRoomCodes = java.util.Set.of();
    private java.util.Set<Integer> preferredStartHours = java.util.Set.of();

    public java.util.Set<String> getPreferredRoomCodes() { return preferredRoomCodes; }
    public void setPreferredRoomCodes(java.util.Set<String> v) { this.preferredRoomCodes = v != null ? v : java.util.Set.of(); }
    public java.util.Set<Integer> getPreferredStartHours() { return preferredStartHours; }
    public void setPreferredStartHours(java.util.Set<Integer> v) { this.preferredStartHours = v != null ? v : java.util.Set.of(); }

    public boolean hasRoomPreference() { return !preferredRoomCodes.isEmpty(); }
    public boolean hasHourPreference() { return !preferredStartHours.isEmpty(); }

    public int getSemester() { return semester; }
    public void setSemester(int semester) { this.semester = semester; }

    public boolean isRequired() { return "REQUIRED".equals(courseType); }
    public boolean isLecture() { return "LECTURE".equals(assignmentType); }
    public boolean isTutorial() { return "TUTORIAL".equals(assignmentType); }
    public boolean isLab() { return "LAB".equals(assignmentType); }

    public boolean sharesTeacher(Lesson other) {
        for (String key : this.teacherKeys) {
            if (other.teacherKeys.contains(key)) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return courseCode + " " + assignmentType;
    }
}