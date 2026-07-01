package com.ganttlens.model;

import java.time.LocalDate;

/**
 * Configuration for Gantt chart layout calculation.
 * Pure Java record — no JavaFX dependency.
 */
public record LayoutConfig(
    LocalDate startDate,
    LocalDate endDate,
    double pixelsPerDay,
    double rowHeight,
    double startY,
    double labelColumnWidth
) {
    public static final double DEFAULT_PIXELS_PER_DAY = 30.0;
    public static final double DEFAULT_ROW_HEIGHT = 30.0;
    public static final double DEFAULT_START_Y = 0.0;
    public static final double DEFAULT_LABEL_COLUMN_WIDTH = 150.0;

    /**
     * Creates a LayoutConfig with default values derived from the task list date range.
     */
    public static LayoutConfig withDefaults(LocalDate startDate, LocalDate endDate) {
        return new LayoutConfig(
            startDate,
            endDate,
            DEFAULT_PIXELS_PER_DAY,
            DEFAULT_ROW_HEIGHT,
            DEFAULT_START_Y,
            DEFAULT_LABEL_COLUMN_WIDTH
        );
    }
}
