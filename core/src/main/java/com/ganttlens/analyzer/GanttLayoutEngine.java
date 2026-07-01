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

        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            double y = config.startY() + i * config.rowHeight();

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
            if (task.status() == TaskStatus.COMPLETED) {
                progressWidth = width;
            } else if (task.status() == TaskStatus.IN_PROGRESS && task.durationDays() > 0) {
                // Assume 50% progress for IN_PROGRESS if no other info
                progressWidth = width * 0.5;
            }

            layouts.add(new TaskLayout(
                task.id(),
                x, y, width, config.rowHeight(),
                progressWidth,
                critSet.contains(task.id())
            ));
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
}
