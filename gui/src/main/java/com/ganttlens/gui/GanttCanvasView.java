package com.ganttlens.gui;

import com.ganttlens.model.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * JavaFX Canvas that renders a Gantt chart from layout and task data.
 * Supports scrolling, time axis, task bars, progress, critical path, dependency arrows, and today line.
 */
public class GanttCanvasView extends Canvas {

    private List<TaskLayout> layouts = List.of();
    private List<Task> tasks = List.of();
    private LayoutConfig config;
    private double scrollX = 0;
    private double scrollY = 0;

    // Interaction state
    private Set<String> selectedIds = Set.of();
    private String hoveredId = null;

    // Colors
    private static final Color HEADER_BG = Color.web("#FAFAFA");
    private static final Color HEADER_TEXT = Color.web("#616161");
    private static final Color TODAY_COLOR = Color.RED;
    private static final Color ARROW_COLOR = Color.web("#9E9E9E");
    private static final Color SELECTED_BORDER = Color.web("#1565C0");  // blue
    private static final Color HOVERED_BORDER = Color.web("#64B5F6");   // light blue
    private static final double SELECTION_BORDER_WIDTH = 2.5;

    public GanttCanvasView() {
        this(800, 400);
    }

    public GanttCanvasView(double width, double height) {
        super(width, height);
    }

    /**
     * Renders the full Gantt chart.
     */
    public void render(List<TaskLayout> layouts, List<Task> tasks, LayoutConfig config) {
        this.layouts = layouts;
        this.tasks = tasks;
        this.config = config;
        draw();
    }

    /**
     * Updates scroll offsets and redraws.
     */
    public void setScroll(double scrollX, double scrollY) {
        this.scrollX = Math.max(0, scrollX);
        this.scrollY = Math.max(0, scrollY);
        draw();
    }

    /**
     * Sets the currently selected task IDs for highlight rendering.
     */
    public void setSelectedIds(Set<String> selectedIds) {
        this.selectedIds = selectedIds != null ? Set.copyOf(selectedIds) : Set.of();
        draw();
    }

    /**
     * Sets the currently hovered task ID for highlight rendering.
     */
    public void setHoveredId(String hoveredId) {
        this.hoveredId = hoveredId;
        draw();
    }

    public Set<String> getSelectedIds() { return selectedIds; }
    public String getHoveredId() { return hoveredId; }

    public double getScrollX() { return scrollX; }
    public double getScrollY() { return scrollY; }

    /**
     * Redraws the entire canvas from current state.
     */
    public void draw() {
        if (config == null) return;

        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();

        // Clear
        gc.clearRect(0, 0, w, h);
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, w, h);

        gc.save();
        gc.translate(-scrollX, -scrollY);

        drawTimeAxis(gc, w, h);
        drawTaskBars(gc);
        drawDependencyArrows(gc);
        drawTodayLine(gc, h);

        gc.restore();
    }

    // ========== Time Axis ==========

    private void drawTimeAxis(GraphicsContext gc, double canvasWidth, double canvasHeight) {
        if (config.startDate() == null || config.endDate() == null) return;

        LocalDate current = config.startDate();
        LocalDate end = config.endDate();
        double labelWidth = config.labelColumnWidth();

        while (!current.isAfter(end)) {
            double x = labelWidth + (ChronoUnit.DAYS.between(config.startDate(), current)) * config.pixelsPerDay();

            // Weekend background
            if (isWeekend(current)) {
                gc.setFill(GanttColorMapper.weekendBackground());
                gc.fillRect(x, 0, config.pixelsPerDay(), canvasHeight + 200);
            }

            // Grid line
            gc.setStroke(GanttColorMapper.gridLineColor());
            gc.setLineWidth(0.5);
            gc.strokeLine(x, 0, x, canvasHeight + 200);

            // Date label (every day, or every 5 days for dense layouts)
            if (config.pixelsPerDay() >= 15 || current.getDayOfMonth() == 1 || current.getDayOfWeek() == DayOfWeek.MONDAY) {
                gc.setFill(HEADER_TEXT);
                gc.setFont(Font.font(10));
                String label = String.format("%d/%d", current.getMonthValue(), current.getDayOfMonth());
                gc.fillText(label, x + 2, 12);
            }

            current = current.plusDays(1);
        }

        // Header separator line
        gc.setStroke(Color.web("#E0E0E0"));
        gc.setLineWidth(1);
        gc.strokeLine(labelWidth, 16, labelWidth + (ChronoUnit.DAYS.between(config.startDate(), end) + 1) * config.pixelsPerDay(), 16);
    }

    // ========== Task Bars ==========

    private void drawTaskBars(GraphicsContext gc) {
        for (int i = 0; i < layouts.size(); i++) {
            TaskLayout tl = layouts.get(i);
            Task task = tasks.get(i);

            if (tl.width() <= 0) continue;

            double barY = tl.y() + 4; // padding from top of row
            double barHeight = tl.height() - 8;

            // Main bar (background color)
            Color barColor = GanttColorMapper.barColor(task.status(), task.color());
            gc.setFill(barColor);
            gc.fillRoundRect(tl.x(), barY, tl.width(), barHeight, 4, 4);

            // Progress fill
            if (tl.progressWidth() > 0) {
                Color fillColor = GanttColorMapper.progressColor(task.status());
                gc.setFill(fillColor);
                gc.fillRoundRect(tl.x(), barY, tl.progressWidth(), barHeight, 4, 4);
            }

            // Critical path border
            if (tl.isCriticalPath()) {
                gc.setStroke(GanttColorMapper.CRITICAL_PATH_BORDER);
                gc.setLineWidth(GanttColorMapper.CRITICAL_PATH_BORDER_WIDTH);
                gc.strokeRoundRect(tl.x(), barY, tl.width(), barHeight, 4, 4);
            }

            // Selection highlight border
            if (selectedIds.contains(tl.taskId())) {
                gc.setStroke(SELECTED_BORDER);
                gc.setLineWidth(SELECTION_BORDER_WIDTH);
                gc.strokeRoundRect(tl.x() - 1, barY - 1, tl.width() + 2, barHeight + 2, 5, 5);
            }

            // Hover highlight border
            if (tl.taskId().equals(hoveredId) && !selectedIds.contains(tl.taskId())) {
                gc.setStroke(HOVERED_BORDER);
                gc.setLineWidth(1.5);
                gc.strokeRoundRect(tl.x() - 1, barY - 1, tl.width() + 2, barHeight + 2, 5, 5);
            }

            // Task name on bar (if wide enough)
            if (tl.width() > 50) {
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font(11));
                String name = task.name();
                if (name.length() > 15) name = name.substring(0, 14) + "...";
                gc.fillText(name, tl.x() + 4, barY + barHeight / 2 + 4);
            }
        }
    }

    // ========== Task Labels (Left Column) ==========

    private void drawTaskLabels(GraphicsContext gc) {
        gc.setFill(Color.BLACK);
        gc.setFont(Font.font(12));

        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            double y = config.startY() + i * config.rowHeight() + config.rowHeight() / 2 + 4;

            String name = task.name();
            // Truncate if too long for label column
            if (name.length() > 14) name = name.substring(0, 13) + "...";
            gc.fillText(name, 8, y);
        }

        // Label column separator
        gc.setStroke(Color.web("#E0E0E0"));
        gc.setLineWidth(1);
        gc.strokeLine(config.labelColumnWidth(), 0, config.labelColumnWidth(), getHeight());
    }

    // ========== Dependency Arrows ==========

    private void drawDependencyArrows(GraphicsContext gc) {
        gc.setStroke(ARROW_COLOR);
        gc.setLineWidth(1.0);

        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            TaskLayout targetLayout = layouts.get(i);

            for (String depName : task.dependencyIds()) {
                // Find the dependency task and its layout
                for (int j = 0; j < tasks.size(); j++) {
                    if (tasks.get(j).name().equals(depName) && j < layouts.size()) {
                        TaskLayout sourceLayout = layouts.get(j);
                        drawArrow(gc, sourceLayout, targetLayout);
                        break;
                    }
                }
            }
        }
    }

    private void drawArrow(GraphicsContext gc, TaskLayout from, TaskLayout to) {
        double startX = from.x() + from.width();
        double startY = from.y() + from.height() / 2;
        double endX = to.x();
        double endY = to.y() + to.height() / 2;

        gc.strokeLine(startX, startY, endX, endY);

        // Arrowhead
        double arrowSize = 6;
        double angle = Math.atan2(endY - startY, endX - startX);
        double x1 = endX - arrowSize * Math.cos(angle - Math.PI / 6);
        double y1 = endY - arrowSize * Math.sin(angle - Math.PI / 6);
        double x2 = endX - arrowSize * Math.cos(angle + Math.PI / 6);
        double y2 = endY - arrowSize * Math.sin(angle + Math.PI / 6);

        gc.setFill(ARROW_COLOR);
        gc.fillPolygon(
            new double[]{endX, x1, x2},
            new double[]{endY, y1, y2},
            3
        );
    }

    // ========== Today Line ==========

    private void drawTodayLine(GraphicsContext gc, double canvasHeight) {
        if (config.startDate() == null) return;

        LocalDate today = LocalDate.now();
        if (today.isBefore(config.startDate()) || today.isAfter(config.endDate())) return;

        double x = config.labelColumnWidth() + ChronoUnit.DAYS.between(config.startDate(), today) * config.pixelsPerDay();

        gc.setStroke(TODAY_COLOR);
        gc.setLineWidth(1.5);
        gc.setLineDashOffset(0);
        gc.strokeLine(x, 0, x, canvasHeight + 200);
    }

    // ========== Helpers ==========

    private boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
}
