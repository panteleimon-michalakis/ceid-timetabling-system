package gr.upatras.ceid.timetable.solver;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;

import java.util.List;

@PlanningSolution
public class CeidTimetable {

    @ValueRangeProvider(id = "timeSlotRange")
    private List<SolverTimeSlot> timeSlots;

    @ValueRangeProvider(id = "roomRange")
    private List<SolverRoom> rooms;

    @PlanningEntityCollectionProperty
    private List<Lesson> lessons;

    @PlanningScore
    private HardSoftScore score;

    public CeidTimetable() {}

    public CeidTimetable(List<SolverTimeSlot> timeSlots, List<SolverRoom> rooms, List<Lesson> lessons) {
        this.timeSlots = timeSlots;
        this.rooms = rooms;
        this.lessons = lessons;
    }

    public List<SolverTimeSlot> getTimeSlots() { return timeSlots; }
    public void setTimeSlots(List<SolverTimeSlot> timeSlots) { this.timeSlots = timeSlots; }

    public List<SolverRoom> getRooms() { return rooms; }
    public void setRooms(List<SolverRoom> rooms) { this.rooms = rooms; }

    public List<Lesson> getLessons() { return lessons; }
    public void setLessons(List<Lesson> lessons) { this.lessons = lessons; }

    public HardSoftScore getScore() { return score; }
    public void setScore(HardSoftScore score) { this.score = score; }
}