package com.ganttlens.model;

import java.time.LocalDate;
import java.util.Set;

/**
 * Configuration for the schedule, including working calendar settings.
 */
public record ScheduleConfig(
    String title,
    LocalDate projectStartDate,
    boolean saturdayClosed,
    boolean sundayClosed,
    Set<LocalDate> holidays,
    Set<PersonOffEntry> personOffDays
) {
    public record PersonOffEntry(String person, LocalDate date) {}

    /**
     * Returns true if the given date is a working day
     * (not a closed weekend and not a holiday).
     */
    public boolean isWorkingDay(LocalDate date) {
        if (saturdayClosed && date.getDayOfWeek().getValue() == 6) return false;
        if (sundayClosed && date.getDayOfWeek().getValue() == 7) return false;
        return holidays == null || !holidays.contains(date);
    }
}
