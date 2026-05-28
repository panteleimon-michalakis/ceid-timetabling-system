package gr.upatras.ceid.timetable.solver;

public class SolverTimeSlot {

    private Long id;
    private String dayOfWeek;  // MONDAY, TUESDAY, ...
    private int startHour;     // 9, 10, 11, ... or 9, 12, 15, 18 for exams
    private String dayKey;     // For semester: "MONDAY", for exam: "2026-01-15"

    public SolverTimeSlot() {}

    public SolverTimeSlot(Long id, String dayOfWeek, int startHour) {
        this.id = id;
        this.dayOfWeek = dayOfWeek;
        this.startHour = startHour;
        this.dayKey = dayOfWeek; // default for semester
    }

    public SolverTimeSlot(Long id, String dayOfWeek, int startHour, String dayKey) {
        this.id = id;
        this.dayOfWeek = dayOfWeek;
        this.startHour = startHour;
        this.dayKey = dayKey;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public int getStartHour() { return startHour; }
    public void setStartHour(int startHour) { this.startHour = startHour; }

    public String getDayKey() { return dayKey; }
    public void setDayKey(String dayKey) { this.dayKey = dayKey; }

    public boolean isLunchHour() {
        return startHour >= 12 && startHour <= 14;
    }

    @Override
    public String toString() {
        return dayKey + " " + startHour + ":00";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SolverTimeSlot other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}