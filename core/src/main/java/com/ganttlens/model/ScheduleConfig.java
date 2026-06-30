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
}
