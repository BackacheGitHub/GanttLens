package com.ganttlens.analyzer;

import com.ganttlens.model.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pure-Java layout calculation engine for Gantt chart rendering.
 * Converts Task model data into pixel coordinates for canvas drawing.
 * Independent of JavaFX — fully testable with JUnit.
 */
public class GanttLayoutEngine {

    /**
     * Computes the layout for all tasks.
     *
     * @param tasks        the parsed task list
     * @param criticalIds  set of task IDs on the critical path (may be null/empty)
     * @param config       layout configuration (date range, pixel density, row height, etc.)
     * @return ordered list of TaskLayout matching the task order
     */
    public List<TaskLayout> computeLayout(List<Task> tasks, Set<String> criticalIds, LayoutConfig config) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

        Set<String> critSet = criticalIds != null ? criticalIds : Set.of();
        List<TaskLayout> layouts = new ArrayList<>(tasks.size());

        double groupHeaderHeight = 24.0;
        String currentGroup = null;
        double yOffset = 0;

        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);

            // Insert group header when group changes
            if (task.group() != null && !task.group().equals(currentGroup)) {
                currentGroup = task.group();
                double fullWidth = config.labelColumnWidth()
                    + (java.time.temporal.ChronoUnit.DAYS.between(config.startDate(), config.endDate()) + 1) * config.pixelsPerDay();
                layouts.add(TaskLayout.groupHeader(
                    currentGroup,
                    config.startY() + yOffset,
                    groupHeaderHeight,
                    fullWidth
                ));
                yOffset += groupHeaderHeight;
            }

            double y = config.startY() + yOffset;

            double x = 0;
            double width = 0;
            if (task.startDate() != null && task.endDate() != null) {
                x = config.labelColumnWidth() + dateToXOffset(task.startDate(), config.startDate(), config.pixelsPerDay());
                long daysBetween = ChronoUnit.DAYS.between(task.startDate(), task.endDate());
                width = Math.max((daysBetween + 1) * config.pixelsPerDay(), config.pixelsPerDay());
            } else if (task.startDate() != null) {
                x = config.labelColumnWidth() + dateToXOffset(task.startDate(), config.startDate(), config.pixelsPerDay());
                width = Math.max(task.durationDays() * config.pixelsPerDay(), config.pixelsPerDay());
            }

            double progressWidth = 0;
            if (task.progressPercent() > 0 && width > 0) {
                progressWidth = width * task.progressPercent() / 100.0;
            }

            layouts.add(new TaskLayout(
                task.id(),
                x, y, width, config.rowHeight(),
                progressWidth,
                critSet.contains(task.id()),
                task.durationDays() == 0
            ));
            yOffset += config.rowHeight();
        }

        return layouts;
    }

    /**
     * Converts a LocalDate to a pixel X offset relative to the layout start date.
     */
    public double dateToXOffset(LocalDate date, LocalDate layoutStart, double pixelsPerDay) {
        long days = ChronoUnit.DAYS.between(layoutStart, date);
        return days * pixelsPerDay;
    }

    /**
     * Converts a pixel X offset back to a LocalDate, given the layout start date.
     */
    public LocalDate xToDate(double pixelX, LocalDate layoutStart, double pixelsPerDay, double labelColumnWidth) {
        double adjustedX = pixelX - labelColumnWidth;
        long days = (long) Math.round(adjustedX / pixelsPerDay);
        return layoutStart.plusDays(days);
    }

    /**
     * Hit-tests a mouse coordinate against the layout to find which task (if any) is under the cursor.
     *
     * @param mouseX   mouse X coordinate in canvas space
     * @param mouseY   mouse Y coordinate in canvas space
     * @param layouts  pre-computed layout list
     * @return Optional containing the taskId if hit, empty otherwise
     */
    public Optional<String> hitTest(double mouseX, double mouseY, List<TaskLayout> layouts) {
        // Iterate in reverse so topmost (last drawn) task is hit first
        for (int i = layouts.size() - 1; i >= 0; i--) {
            TaskLayout tl = layouts.get(i);
            if (mouseX >= tl.x() && mouseX <= tl.x() + tl.width()
                && mouseY >= tl.y() && mouseY <= tl.y() + tl.height()) {
                return Optional.of(tl.taskId());
            }
        }
        return Optional.empty();
    }

    /**
     * Computes the overall canvas dimensions needed to render all tasks.
     */
    public double[] computeCanvasSize(List<Task> tasks, LayoutConfig config) {
        if (tasks == null || tasks.isEmpty()) {
            return new double[]{ config.labelColumnWidth(), config.rowHeight() };
        }

        double maxX = 0;
        for (Task task : tasks) {
            if (task.startDate() != null && task.endDate() != null) {
                double x = dateToXOffset(task.endDate(), config.startDate(), config.pixelsPerDay());
                maxX = Math.max(maxX, x);
            } else if (task.startDate() != null) {
                double x = dateToXOffset(task.startDate(), config.startDate(), config.pixelsPerDay())
                    + task.durationDays() * config.pixelsPerDay();
                maxX = Math.max(maxX, x);
            }
        }

        double width = config.labelColumnWidth() + maxX + config.pixelsPerDay() * 2; // padding
        double height = config.startY() + tasks.size() * config.rowHeight() + config.rowHeight(); // extra row for header
        return new double[]{ width, height };
    }

    /**
     * Computes alternating row style flags for zebra-striping.
     * Returns a boolean array where {@code true} means "even" (index 0, 2, 4...)
     * and {@code false} means "odd" (index 1, 3, 5...).
     *
     * @param taskCount number of tasks (rows)
     * @return boolean array of length taskCount; empty array if taskCount is 0
     */
    public boolean[] computeRowStyles(int taskCount) {
        if (taskCount <= 0) return new boolean[0];
        boolean[] styles = new boolean[taskCount];
        for (int i = 0; i < taskCount; i++) {
            styles[i] = (i % 2 == 0);
        }
        return styles;
    }

    /**
     * Computes the diamond (rhombus) shape vertices for a milestone marker.
     * Returns 8 double values: [topX, topY, rightX, rightY, bottomX, bottomY, leftX, leftY].
     *
     * @param centerX horizontal center of the diamond
     * @param centerY vertical center of the diamond
     * @param size    half-width/height of the diamond (radius)
     * @return array of 8 doubles representing 4 vertices
     */
    public double[] computeMilestoneShape(double centerX, double centerY, double size) {
        return new double[]{
            centerX,        centerY - size,  // top
            centerX + size, centerY,         // right
            centerX,        centerY + size,  // bottom
            centerX - size, centerY          // left
        };
    }

    /**
     * Computes an orthogonal (L-shaped) arrow path between two task layouts.
     * Arrow originates from the bottom of the source task (offset from right edge)
     * and connects to the left edge of the target task.
     * 
     * Path structure:
     * - Start: bottom of source task, offset from right edge
     * - Vertical down to target's Y level
     * - Horizontal to target's left edge
     * 
     * This design avoids overlapping with task text while saving horizontal space.
     *
     * @param from source task layout
     * @param to   target task layout
     * @return array of [x,y] coordinate pairs describing the polyline
     */
    public double[][] computeArrowPath(TaskLayout from, TaskLayout to) {
        // Start point: bottom of source task, offset from right edge
        // Offset ensures arrow doesn't originate from the exact corner (visually cleaner)
        double bottomOffset = 8; // distance from right edge where arrow starts
        double startX = from.x() + from.width() - bottomOffset;
        double startY = from.y() + from.height(); // bottom edge of source task
        
        // End point: left edge of target task, centered vertically
        double endX = to.x();
        double endY = to.y() + to.height() / 2.0;

        // Same row: use original horizontal approach (arrow from right to left)
        if (Math.abs(from.y() - to.y()) < 0.001) {
            double sameRowStartX = from.x() + from.width();
            return new double[][]{ {sameRowStartX, startY - from.height() / 2.0}, {endX, endY} };
        }

        // Cross row: L-shaped path (vertical then horizontal)
        return new double[][]{
            {startX, startY},      // start at bottom of source (offset from right)
            {startX, endY},        // vertical down to target's Y level
            {endX,   endY}         // horizontal to target's left edge
        };
    }
}
