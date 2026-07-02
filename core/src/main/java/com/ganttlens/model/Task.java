package com.ganttlens.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Represents a single task parsed from a PlantUML Gantt chart.
 */
public record Task(
    String id,
    String name,
    String group,
    LocalDate startDate,
    LocalDate endDate,
    int durationDays,
    List<Assignment> assignments,
    List<String> dependencyIds,
    TaskStatus status,
    String color,
    int progressPercent
) {
    /** Backward-compatible constructor without progressPercent (derives from status). */
    public Task(String id, String name, String group, LocalDate startDate, LocalDate endDate,
                int durationDays, List<Assignment> assignments, List<String> dependencyIds,
                TaskStatus status, String color) {
        this(id, name, group, startDate, endDate, durationDays, assignments, dependencyIds,
            status, color, deriveProgress(status));
    }

    private static int deriveProgress(TaskStatus status) {
        return switch (status) {
            case COMPLETED -> 100;
            case IN_PROGRESS -> 50;
            case PENDING -> 0;
            case BLOCKED -> 0;
        };
    }
}
