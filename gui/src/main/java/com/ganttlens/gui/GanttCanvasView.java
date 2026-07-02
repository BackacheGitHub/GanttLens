package com.ganttlens.gui;

import com.ganttlens.analyzer.GanttLayoutEngine;
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

    /** Height in pixels reserved for the fixed time axis header at the top. */
    public static final double TIME_AXIS_HEIGHT = 20.0;

    /**
     * Returns the Y position where the time axis should be drawn.
     * Always 0 — the time axis is fixed at the top regardless of vertical scroll.
     */
    public static double computeTimeAxisY(double scrollY) {
        return 0.0;
    }

    // Colors
    private static final Color HEADER_BG = Color.web("#FAFAFA");
    private static final Color HEADER_TEXT = Color.web("#616161");
    private static final Color TODAY_COLOR = Color.RED;
    private static final Color ARROW_COLOR = Color.web("#9E9E9E");
    private static final Color SELECTED_BORDER = Color.web("#1565C0");  // blue
    private static final Color HOVERED_BORDER = Color.web("#64B5F6");   // light blue
    private static final double SELECTION_BORDER_WIDTH = 2.5;

    private final GanttLayoutEngine arrowEngine = new GanttLayoutEngine();

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
     * Uses a layered approach:
     *   Layer 1 — scrollable content (task bars, arrows, today line)
     *   Layer 2 — fixed time axis (top, scrolls X only)
     *   Layer 3 — fixed label column (left, scrolls Y only)
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

        // Layer 1: Scrollable content (task bars, dependency arrows, today line)
        gc.save();
        gc.translate(-scrollX, -scrollY);
        drawRowBackgrounds(gc);
        drawTaskBars(gc);
        drawDependencyArrows(gc);
        drawTodayLine(gc, h);
        gc.restore();

        // Layer 2: Fixed time axis at top (scrolls X only, not Y)
        gc.save();
        gc.translate(-scrollX, 0);
        drawTimeAxis(gc, w, h);
        gc.restore();

        // Layer 3: Fixed label column at left (scrolls Y only, not X)
        gc.save();
        gc.translate(0, -scrollY);
        drawTaskLabels(gc);
        gc.restore();
    }

    // ========== Time Axis ==========

    private void drawTimeAxis(GraphicsContext gc, double canvasWidth, double canvasHeight) {
        if (config.startDate() == null || config.endDate() == null) return;

        LocalDate current = config.startDate();
        LocalDate end = config.endDate();
        double labelWidth = config.labelColumnWidth();
        double contentHeight = Math.max(canvasHeight, layouts.size() * config.rowHeight() + config.startY());

        while (!current.isAfter(end)) {
            double x = labelWidth + (ChronoUnit.DAYS.between(config.startDate(), current)) * config.pixelsPerDay();

            // Weekend background
            if (isWeekend(current)) {
                gc.setFill(GanttColorMapper.weekendBackground());
                gc.fillRect(x, 0, config.pixelsPerDay(), contentHeight);
            }

            // Grid line
            gc.setStroke(GanttColorMapper.gridLineColor());
            gc.setLineWidth(0.5);
            gc.strokeLine(x, 0, x, contentHeight);

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

    // ========== Row Backgrounds ==========

    private void drawRowBackgrounds(GraphicsContext gc) {
        if (tasks.isEmpty()) return;

        double fullWidth = config.labelColumnWidth()
            + (ChronoUnit.DAYS.between(config.startDate(), config.endDate()) + 1) * config.pixelsPerDay();

        for (int i = 0; i < tasks.size(); i++) {
            double rowY = config.startY() + i * config.rowHeight();

            // Alternating background
            gc.setFill(i % 2 == 0 ? GanttColorMapper.rowBackgroundEven() : GanttColorMapper.rowBackgroundOdd());
            gc.fillRect(0, rowY, fullWidth, config.rowHeight());

            // Row separator line
            gc.setStroke(GanttColorMapper.rowSeparatorColor());
            gc.setLineWidth(0.5);
            gc.strokeLine(0, rowY + config.rowHeight(), fullWidth, rowY + config.rowHeight());
        }
    }

    // ========== Task Bars ==========

    private void drawTaskBars(GraphicsContext gc) {
        GanttLayoutEngine shapeEngine = new GanttLayoutEngine();
        int taskIdx = 0;

        for (int i = 0; i < layouts.size(); i++) {
            TaskLayout tl = layouts.get(i);

            // Skip group headers in task bar rendering (drawn separately)
            if (tl.isGroupHeader()) {
                // Group header: dark background + white bold text
                gc.setFill(Color.web("#424242"));
                gc.fillRect(tl.x(), tl.y(), tl.width(), tl.height());
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("System", javafx.scene.text.FontWeight.BOLD, 12));
                gc.fillText(tl.groupLabel() != null ? tl.groupLabel() : "", 8, tl.y() + tl.height() / 2 + 4);
                continue;
            }

            if (taskIdx >= tasks.size()) break;
            Task task = tasks.get(taskIdx++);

            double barY = tl.y() + 4; // padding from top of row
            double barHeight = tl.height() - 8;
            Color barColor = GanttColorMapper.barColor(task.status(), task.color());

            if (tl.isMilestone()) {
                // Draw diamond for milestones
                double centerX = tl.x() + tl.width() / 2;
                double centerY = tl.y() + tl.height() / 2;
                double size = barHeight / 2;
                double[] shape = shapeEngine.computeMilestoneShape(centerX, centerY, size);

                gc.setFill(barColor);
                gc.fillPolygon(
                    new double[]{shape[0], shape[2], shape[4], shape[6]},
                    new double[]{shape[1], shape[3], shape[5], shape[7]},
                    4
                );

                // Name to the right of diamond
                gc.setFill(Color.BLACK);
                gc.setFont(Font.font(11));
                gc.fillText(task.name(), centerX + size + 4, centerY + 4);
            } else {
                if (tl.width() <= 0) continue;

                // Main bar (background color)
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
    }

    // ========== Task Labels (Left Column) ==========

    private void drawTaskLabels(GraphicsContext gc) {
        double labelWidth = config.labelColumnWidth();
        if (labelWidth <= 0) return;

        // Estimate content height based on layouts (which includes group headers)
        double lastLayoutBottom = layouts.isEmpty() ? 0 :
            layouts.get(layouts.size() - 1).y() + layouts.get(layouts.size() - 1).height();
        double contentHeight = Math.max(getHeight(), lastLayoutBottom);

        // Opaque white background to cover scrolling chart content behind labels
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, labelWidth, contentHeight);

        // Draw labels aligned with actual layout positions
        int taskIdx = 0;
        for (int i = 0; i < layouts.size(); i++) {
            TaskLayout tl = layouts.get(i);

            if (tl.isGroupHeader()) {
                // Group header label
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("System", javafx.scene.text.FontWeight.BOLD, 12));
                gc.fillText(tl.groupLabel() != null ? tl.groupLabel() : "", 8, tl.y() + tl.height() / 2 + 4);
                continue;
            }

            if (taskIdx >= tasks.size()) break;
            Task task = tasks.get(taskIdx++);

            gc.setFill(Color.BLACK);
            gc.setFont(Font.font(12));
            double y = tl.y() + tl.height() / 2 + 4;

            String name = task.name();
            int maxChars = (int) ((labelWidth - 16) / 7); // approx chars at 7px each
            if (name.length() > maxChars) name = name.substring(0, maxChars - 1) + "...";
            gc.fillText(name, 8, y);
        }

        // Label column separator
        gc.setStroke(Color.web("#E0E0E0"));
        gc.setLineWidth(1);
        gc.strokeLine(labelWidth, 0, labelWidth, contentHeight);
    }

    // ========== Dependency Arrows ==========

    private void drawDependencyArrows(GraphicsContext gc) {
        gc.setStroke(ARROW_COLOR);
        gc.setLineWidth(1.0);

        // Build a map from task name to layout for arrow lookups
        java.util.Map<String, TaskLayout> nameToLayout = new java.util.HashMap<>();
        int taskIdx = 0;
        for (int i = 0; i < layouts.size(); i++) {
            TaskLayout tl = layouts.get(i);
            if (tl.isGroupHeader()) continue;
            if (taskIdx < tasks.size()) {
                nameToLayout.put(tasks.get(taskIdx).name(), tl);
                taskIdx++;
            }
        }

        for (Task task : tasks) {
            TaskLayout targetLayout = nameToLayout.get(task.name());
            if (targetLayout == null) continue;

            for (String depName : task.dependencyIds()) {
                TaskLayout sourceLayout = nameToLayout.get(depName);
                if (sourceLayout != null) {
                    drawArrow(gc, sourceLayout, targetLayout);
                }
            }
        }
    }

    private void drawArrow(GraphicsContext gc, TaskLayout from, TaskLayout to) {
        double[][] path = arrowEngine.computeArrowPath(from, to);

        // Draw polyline
        for (int i = 0; i < path.length - 1; i++) {
            gc.strokeLine(path[i][0], path[i][1], path[i + 1][0], path[i + 1][1]);
        }

        // Arrowhead at the last segment
        double[] last = path[path.length - 1];
        double[] prev = path[path.length - 2];
        double endX = last[0];
        double endY = last[1];
        double arrowSize = 6;
        double angle = Math.atan2(endY - prev[1], endX - prev[0]);
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
        double contentHeight = Math.max(canvasHeight, layouts.size() * config.rowHeight() + config.startY());

        gc.setStroke(TODAY_COLOR);
        gc.setLineWidth(1.5);
        gc.setLineDashOffset(0);
        gc.strokeLine(x, 0, x, contentHeight);
    }

    // ========== Helpers ==========

    private boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
}
