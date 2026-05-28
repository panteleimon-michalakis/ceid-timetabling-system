package gr.upatras.ceid.timetable.solver;

public class SolverRoom {

    private Long id;
    private String code;
    private int capacity;
    private String roomType; // AMPHITHEATER, CLASSROOM, LAB

    public SolverRoom() {}

    public SolverRoom(Long id, String code, int capacity, String roomType) {
        this.id = id;
        this.code = code;
        this.capacity = capacity;
        this.roomType = roomType;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }

    public boolean isLab() { return "LAB".equals(roomType); }

    @Override
    public String toString() { return code; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SolverRoom other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}