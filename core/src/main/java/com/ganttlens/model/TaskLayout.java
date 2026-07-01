package com.ganttlens.model;

/**
 * Layout information for a single task bar on the Gantt canvas.
 * Pure Java record — no JavaFX dependency.
 */
public record TaskLayout(
    String taskId,
    double x,
    double y,
    double width,
    double height,
    double progressWidth,
    boolean isCriticalPath
) {}
