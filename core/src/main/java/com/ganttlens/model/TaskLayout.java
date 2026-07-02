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
    boolean isCriticalPath,
    boolean isMilestone,
    boolean isGroupHeader,
    String groupLabel
) {
    /** Backward-compatible constructor without isMilestone/groupHeader fields. */
    public TaskLayout(String taskId, double x, double y, double width, double height,
                      double progressWidth, boolean isCriticalPath) {
        this(taskId, x, y, width, height, progressWidth, isCriticalPath, false, false, null);
    }

    /** Constructor with isMilestone but without groupHeader. */
    public TaskLayout(String taskId, double x, double y, double width, double height,
                      double progressWidth, boolean isCriticalPath, boolean isMilestone) {
        this(taskId, x, y, width, height, progressWidth, isCriticalPath, isMilestone, false, null);
    }

    /** Factory for creating a group header layout. */
    public static TaskLayout groupHeader(String label, double y, double height, double fullWidth) {
        return new TaskLayout("group:" + label, 0, y, fullWidth, height, 0, false, false, true, label);
    }
}
