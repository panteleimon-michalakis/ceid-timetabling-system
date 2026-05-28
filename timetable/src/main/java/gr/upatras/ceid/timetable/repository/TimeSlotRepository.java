package gr.upatras.ceid.timetable.repository;

import gr.upatras.ceid.timetable.entity.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {
    List<TimeSlot> findByDayOfWeek(DayOfWeek dayOfWeek);
    List<TimeSlot> findBySlotType(TimeSlot.SlotType slotType);

    // Για idempotent seeding: βρες SEMESTER slot με ίδια μέρα + ώρα
    Optional<TimeSlot> findBySlotTypeAndDayOfWeekAndStartTime(
            TimeSlot.SlotType slotType,
            DayOfWeek dayOfWeek,
            LocalTime startTime
    );

    // Για EXAM solver: βρες exam slots μέσα στο εύρος ημερομηνιών του timetable
    List<TimeSlot> findBySlotTypeAndSpecificDateBetweenOrderBySpecificDateAscStartTimeAsc(
            TimeSlot.SlotType slotType,
            LocalDate startDate,
            LocalDate endDate
    );

    // Για να μη δημιουργούνται διπλά exam slots αν ξανατρέξει ο solver
    List<TimeSlot> findBySlotTypeAndSpecificDateAndStartTime(
        TimeSlot.SlotType slotType,
        LocalDate specificDate,
        LocalTime startTime
    );
}