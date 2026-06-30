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
    TaskStatus status
) {}
