package com.ganttlens.model;

import java.util.List;

/**
 * Represents a fully parsed PlantUML Gantt schedule.
 */
public record GanttSchedule(
    ScheduleConfig config,
    List<Task> tasks
) {
    /**
     * Finds a task by its name.
     */
    public Task findTaskByName(String name) {
        return tasks.stream()
            .filter(t -> t.name().equals(name))
            .findFirst()
            .orElse(null);
    }
}
