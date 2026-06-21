package gr.upatras.ceid.timetable.controller;

import gr.upatras.ceid.timetable.entity.Timetable;

import java.time.LocalDateTime;

/**
 * Δημόσιο, read-only προβολικό DTO για δημοσιευμένα προγράμματα.
 *
 * Εκτίθεται ΜΟΝΟ μέσω του {@link PublicTimetableController} (namespace
 * {@code /api/public}). Σκόπιμα ΔΕΝ εκθέτει ευαίσθητα/εσωτερικά πεδία του
 * {@link Timetable} (createdBy, notes, solverScore/conflicts/timeSeconds,
 * excludedDates, status) — μόνο τα ελάχιστα που χρειάζεται η δημόσια σελίδα.
 */
public record PublicTimetableDto(
        Long id,
        String name,
        String academicYear,
        String timetableType,
        String semesterType,
        LocalDateTime publishedAt
) {
    /** Map-άρει entity → public DTO, με τα enums ως String (null-safe). */
    static PublicTimetableDto from(Timetable t) {
        return new PublicTimetableDto(
                t.getId(),
                t.getName(),
                t.getAcademicYear(),
                t.getTimetableType() != null ? t.getTimetableType().name() : null,
                t.getSemesterType() != null ? t.getSemesterType().name() : null,
                t.getPublishedAt()
        );
    }
}
