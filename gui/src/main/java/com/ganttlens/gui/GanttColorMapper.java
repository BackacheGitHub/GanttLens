package com.ganttlens.gui;

import com.ganttlens.model.TaskStatus;
import javafx.scene.paint.Color;

/**
 * Maps task status and custom color strings to JavaFX Color objects.
 * Pure mapping logic — no canvas dependency.
 */
public final class GanttColorMapper {

    // Default colors per status
    private static final Color COLOR_PENDING    = Color.web("#B0BEC5"); // light gray
    private static final Color COLOR_IN_PROGRESS = Color.web("#42A5F5"); // blue
    private static final Color COLOR_COMPLETED  = Color.web("#66BB6A"); // green
    private static final Color COLOR_BLOCKED    = Color.web("#FFA726"); // orange
    private static final Color COLOR_DEFAULT    = Color.web("#9E9E9E"); // gray

    // Progress fill is a darker shade of the bar color
    private static final Color FILL_PENDING     = Color.web("#90A4AE");
    private static final Color FILL_IN_PROGRESS = Color.web("#1E88E5");
    private static final Color FILL_COMPLETED   = Color.web("#43A047");
    private static final Color FILL_BLOCKED     = Color.web("#F57C00");

    // Critical path highlight
    public static final Color CRITICAL_PATH_BORDER = Color.RED;
    public static final double CRITICAL_PATH_BORDER_WIDTH = 2.0;

    // Today line
    public static final Color TODAY_LINE_COLOR = Color.RED;

    private GanttColorMapper() {} // utility class

    /**
     * Returns the bar color for the given status and optional custom color string.
     */
    public static Color barColor(TaskStatus status, String customColor) {
        if (customColor != null && !customColor.isBlank()) {
            try {
                return Color.web(customColor);
            } catch (IllegalArgumentException ignored) {
                // Fall through to status-based color
            }
        }
        return switch (status) {
            case PENDING     -> COLOR_PENDING;
            case IN_PROGRESS -> COLOR_IN_PROGRESS;
            case COMPLETED   -> COLOR_COMPLETED;
            case BLOCKED     -> COLOR_BLOCKED;
        };
    }

    /**
     * Returns the progress fill color (darker shade) for the given status.
     */
    public static Color progressColor(TaskStatus status) {
        return switch (status) {
            case PENDING     -> FILL_PENDING;
            case IN_PROGRESS -> FILL_IN_PROGRESS;
            case COMPLETED   -> FILL_COMPLETED;
            case BLOCKED     -> FILL_BLOCKED;
        };
    }

    /**
     * Returns the background color for weekend (non-working) days.
     */
    public static Color weekendBackground() {
        return Color.web("#F5F5F5");
    }

    /**
     * Returns the grid line color for date separators.
     */
    public static Color gridLineColor() {
        return Color.web("#E0E0E0");
    }
}
