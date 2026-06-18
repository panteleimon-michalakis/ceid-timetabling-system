package gr.upatras.ceid.timetable.service;

import gr.upatras.ceid.timetable.entity.Course;
import gr.upatras.ceid.timetable.entity.Room;
import gr.upatras.ceid.timetable.entity.TimeSlot;
import gr.upatras.ceid.timetable.entity.TimetableAssignment;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test του {@link AssignmentSnapshotStamper} (S3b-2): επιβεβαιώνει ότι το
 * stamp() γεμίζει ΚΑΙ τα 16 πεδία (Course 6 / Room 4 / Time 6) από τα τρέχοντα
 * entities, ότι το move ξανα-stamp-άρει room/slot αφήνοντας το course, και ότι
 * είναι null-safe. Pure POJO — χωρίς Spring/DB.
 */
class AssignmentSnapshotStamperTest {

    private final AssignmentSnapshotStamper stamper = new AssignmentSnapshotStamper();

    private Course course() {
        return Course.builder()
                .code("CEID_TEST1").name("Δοκιμαστικό Μάθημα")
                .semester(3).studyYear(2)
                .courseType(Course.CourseType.REQUIRED)
                .teachersText("Καρβέλης, Αντωνόπουλος")
                .semesterType(Course.SemesterType.FALL)
                .active(true).visibleInTimetable(true)
                .build();
    }

    private Room room(String code) {
        return Room.builder()
                .name("Αίθουσα " + code).code(code).capacity(120)
                .roomType(Room.RoomType.AMPHITHEATER)
                .active(true)
                .build();
    }

    private TimeSlot semesterSlot(DayOfWeek day, LocalTime start, LocalTime end) {
        return TimeSlot.builder()
                .dayOfWeek(day).startTime(start).endTime(end)
                .slotType(TimeSlot.SlotType.SEMESTER)
                .build();
    }

    @Test
    void stamp_fillsAll16Fields() {
        TimetableAssignment a = TimetableAssignment.builder()
                .course(course())
                .room(room("Γ"))
                .timeSlot(semesterSlot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0)))
                .assignmentType(TimetableAssignment.AssignmentType.LECTURE)
                .build();

        stamper.stamp(a);

        // Course (6)
        assertEquals("CEID_TEST1", a.getSnapshotCourseCode());
        assertEquals("Δοκιμαστικό Μάθημα", a.getSnapshotCourseName());
        assertEquals(3, a.getSnapshotSemester().intValue());
        assertEquals(2, a.getSnapshotStudyYear().intValue());
        assertEquals("REQUIRED", a.getSnapshotCourseType());
        // teachers: normalized + sorted (βλ. TeacherDisplayTextTest)
        assertEquals("Αντωνόπουλος, Καρβέλης", a.getSnapshotTeachersText());
        // Room (4)
        assertEquals("Γ", a.getSnapshotRoomCode());
        assertEquals("Αίθουσα Γ", a.getSnapshotRoomName());
        assertEquals(120, a.getSnapshotRoomCapacity().intValue());
        assertEquals("AMPHITHEATER", a.getSnapshotRoomType());
        // TimeSlot (6)
        assertEquals("MONDAY", a.getSnapshotDayOfWeek());
        assertEquals(LocalTime.of(9, 0), a.getSnapshotStartTime());
        assertEquals(LocalTime.of(11, 0), a.getSnapshotEndTime());
        assertEquals("SEMESTER", a.getSnapshotSlotType());
        assertNull(a.getSnapshotSpecificDate());     // SEMESTER slot → χωρίς ημερομηνία
        assertNull(a.getSnapshotExamPeriodLabel());  // SEMESTER slot → χωρίς period
    }

    @Test
    void stamp_examSlot_capturesDateAndPeriod() {
        TimeSlot exam = TimeSlot.builder()
                .dayOfWeek(DayOfWeek.TUESDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0))
                .slotType(TimeSlot.SlotType.EXAM)
                .specificDate(LocalDate.of(2026, 9, 3))
                .examPeriodLabel("01-05/09")
                .build();
        TimetableAssignment a = TimetableAssignment.builder()
                .course(course()).room(room("Β")).timeSlot(exam)
                .assignmentType(TimetableAssignment.AssignmentType.EXAM)
                .build();

        stamper.stamp(a);

        assertEquals("EXAM", a.getSnapshotSlotType());
        assertEquals(LocalDate.of(2026, 9, 3), a.getSnapshotSpecificDate());
        assertEquals("01-05/09", a.getSnapshotExamPeriodLabel());
    }

    @Test
    void stamp_move_reStampsRoomAndSlot_courseUnchanged() {
        TimetableAssignment a = TimetableAssignment.builder()
                .course(course())
                .room(room("Γ"))
                .timeSlot(semesterSlot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0)))
                .build();
        stamper.stamp(a);
        assertEquals("Γ", a.getSnapshotRoomCode());
        assertEquals("MONDAY", a.getSnapshotDayOfWeek());

        // move → νέα room/slot, ίδιο course
        a.setRoom(room("Δ1"));
        a.setTimeSlot(semesterSlot(DayOfWeek.FRIDAY, LocalTime.of(13, 0), LocalTime.of(15, 0)));
        stamper.stamp(a);

        assertEquals("Δ1", a.getSnapshotRoomCode());            // re-stamped
        assertEquals("FRIDAY", a.getSnapshotDayOfWeek());        // re-stamped
        assertEquals(LocalTime.of(13, 0), a.getSnapshotStartTime());
        assertEquals("CEID_TEST1", a.getSnapshotCourseCode());  // αμετάβλητο
    }

    @Test
    void stamp_nullEntitiesAndNullAssignment_noNpe() {
        TimetableAssignment a = TimetableAssignment.builder().build();
        assertDoesNotThrow(() -> stamper.stamp(a));
        assertNull(a.getSnapshotCourseCode());
        assertNull(a.getSnapshotRoomCode());
        assertNull(a.getSnapshotDayOfWeek());

        assertDoesNotThrow(() -> stamper.stamp(null));
    }
}
