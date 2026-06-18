package gr.upatras.ceid.timetable.service;

import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.Room;
import gr.upatras.ceid.timetable.entity.TimeSlot;
import gr.upatras.ceid.timetable.entity.TimetableAssignment;
import gr.upatras.ceid.timetable.util.TeacherDisplayText;
import org.springframework.stereotype.Component;

/**
 * Snapshot-on-write (S3b-2): γράφει τα 16 denormalized πεδία της ανάθεσης από τα
 * ΤΡΕΧΟΝΤΑ live {@code course/room/timeSlot}.
 *
 * <p>Καλείται ΑΚΡΙΒΩΣ πριν από κάθε {@code save} κάθε write-path (manual placement,
 * move, auto-schedule· ο solver path μπαίνει στο S3c). Στο move ξανα-stamp-άρει
 * room/slot στη ΝΕΑ θέση — επανεγγράψιμο. Single source: live writes & backfill
 * (S3e) περνούν από εδώ, ώστε stamped == ό,τι θα έδειχνε το assignmentToDto.
 *
 * <p>Καθαρά in-memory (καμία DB πρόσβαση): τα course/room/timeSlot είναι ήδη
 * φορτωμένα (EAGER), ώστε να τρέχει και μέσα στο solver persistence loop χωρίς join.
 * Τα enum πεδία αποθηκεύονται ως {@code .name()} String. Null entity → αφήνει την
 * αντίστοιχη ομάδα ως έχει (το snapshot δεν χάνεται).
 */
@Component
public class AssignmentSnapshotStamper {

    public void stamp(TimetableAssignment a) {
        if (a == null) {
            return;
        }
        stampCourse(a, a.getCourse());
        stampRoom(a, a.getRoom());
        stampTimeSlot(a, a.getTimeSlot());
    }

    private void stampCourse(TimetableAssignment a, Course c) {
        if (c == null) {
            return;
        }
        a.setSnapshotCourseCode(c.getCode());
        a.setSnapshotCourseName(c.getName());
        a.setSnapshotSemester(c.getSemester());
        a.setSnapshotStudyYear(c.getStudyYear());
        a.setSnapshotCourseType(c.getCourseType() != null ? c.getCourseType().name() : null);
        a.setSnapshotTeachersText(TeacherDisplayText.normalizeTeachersTextForDto(c.getTeachersText()));
    }

    private void stampRoom(TimetableAssignment a, Room r) {
        if (r == null) {
            return;
        }
        a.setSnapshotRoomCode(r.getCode());
        a.setSnapshotRoomName(r.getName());
        a.setSnapshotRoomCapacity(r.getCapacity());
        a.setSnapshotRoomType(r.getRoomType() != null ? r.getRoomType().name() : null);
    }

    private void stampTimeSlot(TimetableAssignment a, TimeSlot t) {
        if (t == null) {
            return;
        }
        a.setSnapshotDayOfWeek(t.getDayOfWeek() != null ? t.getDayOfWeek().name() : null);
        a.setSnapshotStartTime(t.getStartTime());
        a.setSnapshotEndTime(t.getEndTime());
        a.setSnapshotSlotType(t.getSlotType() != null ? t.getSlotType().name() : null);
        a.setSnapshotSpecificDate(t.getSpecificDate());
        a.setSnapshotExamPeriodLabel(t.getExamPeriodLabel());
    }
}
