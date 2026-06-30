package com.ganttlens.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Records an overload situation for a person on a specific day.
 */
public record OverloadRecord(
    String person,
    LocalDate date,
    double totalLoad,
    List<PersonDailyLoad.TaskLoad> tasks
) {
    public String describe() {
        String taskDesc = tasks.stream()
            .map(t -> t.taskName() + " " + (int)(t.ratio() * 100) + "%")
            .reduce((a, b) -> a + " + " + b)
            .orElse("");
        return String.format("%s %s %.0f%% (%s)",
            date, person, totalLoad * 100, taskDesc);
    }
}
