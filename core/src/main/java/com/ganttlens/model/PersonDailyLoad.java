package com.ganttlens.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Represents a person's workload on a specific day.
 */
public record PersonDailyLoad(
    String person,
    LocalDate date,
    double totalLoad,
    List<TaskLoad> tasks
) {
    /**
     * A single task's contribution to a person's daily load.
     */
    public record TaskLoad(
        String taskId,
        String taskName,
        double ratio
    ) {}
}
