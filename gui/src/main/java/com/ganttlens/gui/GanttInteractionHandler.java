package com.ganttlens.gui;

import com.ganttlens.analyzer.GanttLayoutEngine;
import com.ganttlens.model.Task;
import com.ganttlens.model.TaskLayout;
import com.ganttlens.model.TaskSelectionModel;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Handles mouse interaction logic for the Gantt chart.
 * Pure Java — no JavaFX dependency. Can be unit-tested with a mock LayoutEngine.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Click → hitTest → update SelectionModel</li>
 *   <li>Hover → hitTest → produce tooltip text</li>
 * </ul>
 */
public class GanttInteractionHandler {

    private final GanttLayoutEngine layoutEngine;
    private final TaskSelectionModel selectionModel;

    private List<TaskLayout> currentLayouts = List.of();
    private List<Task> currentTasks = List.of();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public GanttInteractionHandler(GanttLayoutEngine layoutEngine, TaskSelectionModel selectionModel) {
        this.layoutEngine = layoutEngine;
        this.selectionModel = selectionModel;
    }

    /**
     * Updates the current layout and task data (called after each parse/layout cycle).
     */
    public void setData(List<TaskLayout> layouts, List<Task> tasks) {
        this.currentLayouts = layouts != null ? List.copyOf(layouts) : List.of();
        this.currentTasks = tasks != null ? List.copyOf(tasks) : List.of();
    }

    /**
     * Handles a mouse click at the given canvas coordinates.
     * If a task bar is hit, it is selected. If empty space is hit, selection is cleared.
     *
     * @param mouseX canvas X coordinate
     * @param mouseY canvas Y coordinate
     * @return the taskId if a task was selected, empty otherwise
     */
    public Optional<String> handleClick(double mouseX, double mouseY) {
        Optional<String> hit = layoutEngine.hitTest(mouseX, mouseY, currentLayouts);
        if (hit.isPresent()) {
            selectionModel.selectTask(hit.get());
        } else {
            selectionModel.clearSelection();
        }
        return hit;
    }

    /**
     * Handles a mouse hover at the given canvas coordinates.
     * Returns tooltip text if a task bar is under the cursor, empty otherwise.
     *
     * @param mouseX canvas X coordinate
     * @param mouseY canvas Y coordinate
     * @return tooltip text string, or empty if no task is hovered
     */
    public Optional<String> handleHover(double mouseX, double mouseY) {
        Optional<String> hit = layoutEngine.hitTest(mouseX, mouseY, currentLayouts);
        if (hit.isEmpty()) return Optional.empty();

        String taskId = hit.get();
        return findTask(taskId).map(this::buildTooltip);
    }

    /**
     * Returns the current selection model.
     */
    public TaskSelectionModel getSelectionModel() {
        return selectionModel;
    }

    private Optional<Task> findTask(String taskId) {
        return currentTasks.stream()
            .filter(t -> t.id().equals(taskId))
            .findFirst();
    }

    private String buildTooltip(Task task) {
        StringBuilder sb = new StringBuilder();
        sb.append(task.name());
        sb.append("\nStatus: ").append(task.status());

        if (task.startDate() != null) {
            sb.append("\nStart: ").append(task.startDate().format(DATE_FMT));
        }
        if (task.endDate() != null) {
            sb.append("\nEnd: ").append(task.endDate().format(DATE_FMT));
        }
        sb.append("\nDuration: ").append(task.durationDays()).append(" days");

        if (task.assignments() != null && !task.assignments().isEmpty()) {
            String assignees = task.assignments().stream()
                .map(a -> a.person())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
            sb.append("\nAssignee: ").append(assignees);
        }

        sb.append("\nProgress: ").append(task.progressPercent()).append("%");

        return sb.toString();
    }
}
