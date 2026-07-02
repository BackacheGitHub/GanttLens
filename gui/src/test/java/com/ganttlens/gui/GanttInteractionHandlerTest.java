package com.ganttlens.gui;

import com.ganttlens.analyzer.GanttLayoutEngine;
import com.ganttlens.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GanttInteractionHandler — pure Java, no JavaFX.
 * Uses real GanttLayoutEngine (it's simple enough, no mocking needed).
 */
class GanttInteractionHandlerTest {

    private GanttLayoutEngine layoutEngine;
    private TaskSelectionModel selectionModel;
    private GanttInteractionHandler handler;

    private List<Task> tasks;
    private List<TaskLayout> layouts;
    private LayoutConfig config;

    @BeforeEach
    void setUp() {
        layoutEngine = new GanttLayoutEngine();
        selectionModel = new TaskSelectionModel();
        handler = new GanttInteractionHandler(layoutEngine, selectionModel);

        tasks = List.of(
            new Task("t1", "Alpha", null,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5), 5,
                List.of(new Assignment("Alice", 1.0)), List.of(),
                TaskStatus.IN_PROGRESS, null),
            new Task("t2", "Beta", null,
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 10), 5,
                List.of(new Assignment("Bob", 0.5)), List.of("Alpha"),
                TaskStatus.PENDING, null),
            new Task("t3", "Gamma", null,
                LocalDate.of(2026, 7, 11), LocalDate.of(2026, 7, 15), 5,
                List.of(), List.of("Beta"),
                TaskStatus.COMPLETED, null)
        );

        config = new LayoutConfig(
            LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 17),
            30.0, 30.0, 0, 0
        );

        layouts = layoutEngine.computeLayout(tasks, Set.of(), config);
        selectionModel.setTasks(tasks);
        handler.setData(layouts, tasks);
    }

    // ========== handleClick tests ==========

    @Test
    void handleClick_hitTask_selectsIt() {
        // Click on the first task bar (x=30 is 1 day from start, y=15 is middle of row)
        TaskLayout tl = layouts.get(0);
        double clickX = tl.x() + tl.width() / 2;
        double clickY = tl.y() + tl.height() / 2;

        var result = handler.handleClick(clickX, clickY);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("t1");
        assertThat(selectionModel.getSelectedIds()).containsExactly("t1");
    }

    @Test
    void handleClick_hitSecondTask_selectsIt() {
        TaskLayout tl = layouts.get(1);
        double clickX = tl.x() + tl.width() / 2;
        double clickY = tl.y() + tl.height() / 2;

        var result = handler.handleClick(clickX, clickY);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("t2");
    }

    @Test
    void handleClick_emptySpace_clearsSelection() {
        selectionModel.selectTask("t1");

        // Click far outside any task bar
        var result = handler.handleClick(5000, 5000);

        assertThat(result).isEmpty();
        assertThat(selectionModel.getSelectedIds()).isEmpty();
    }

    @Test
    void handleClick_clickDifferentTask_switchesSelection() {
        TaskLayout tl1 = layouts.get(0);
        handler.handleClick(tl1.x() + tl1.width() / 2, tl1.y() + tl1.height() / 2); // hit t1
        assertThat(selectionModel.getSelectedIds()).containsExactly("t1");

        TaskLayout tl2 = layouts.get(1);
        handler.handleClick(tl2.x() + tl2.width() / 2, tl2.y() + tl2.height() / 2); // hit t2
        assertThat(selectionModel.getSelectedIds()).containsExactly("t2");
    }

    // ========== handleHover tests ==========

    @Test
    void handleHover_hitTask_returnsTooltip() {
        TaskLayout tl = layouts.get(0);
        double hoverX = tl.x() + tl.width() / 2;
        double hoverY = tl.y() + tl.height() / 2;

        var result = handler.handleHover(hoverX, hoverY);

        assertThat(result).isPresent();
        String tooltip = result.get();
        assertThat(tooltip).contains("Alpha");
        assertThat(tooltip).contains("IN_PROGRESS");
        assertThat(tooltip).contains("2026-07-01");
        assertThat(tooltip).contains("2026-07-05");
        assertThat(tooltip).contains("Alice");
    }

    @Test
    void handleHover_emptySpace_returnsEmpty() {
        var result = handler.handleHover(5000, 5000);
        assertThat(result).isEmpty();
    }

    @Test
    void handleHover_completedTask_shows100Progress() {
        TaskLayout tl = layouts.get(2);
        double hoverX = tl.x() + tl.width() / 2;
        double hoverY = tl.y() + tl.height() / 2;

        var result = handler.handleHover(hoverX, hoverY);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("100%");
        assertThat(result.get()).contains("Gamma");
    }

    @Test
    void handleHover_pendingTask_shows0Progress() {
        TaskLayout tl = layouts.get(1);
        double hoverX = tl.x() + tl.width() / 2;
        double hoverY = tl.y() + tl.height() / 2;

        var result = handler.handleHover(hoverX, hoverY);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("0%");
    }

    @Test
    void tooltip_showsActualProgressPercent() {
        // Create a task with explicit progressPercent=75
        Task customTask = new Task("t10", "Custom", null,
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5), 5,
            List.of(), List.of(), TaskStatus.IN_PROGRESS, null, 75);

        LayoutConfig cfg = new LayoutConfig(
            LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 17),
            30.0, 30.0, 0, 0
        );
        List<TaskLayout> customLayouts = layoutEngine.computeLayout(List.of(customTask), Set.of(), cfg);
        selectionModel.setTasks(List.of(customTask));
        handler.setData(customLayouts, List.of(customTask));

        TaskLayout tl = customLayouts.get(0);
        var result = handler.handleHover(tl.x() + tl.width() / 2, tl.y() + tl.height() / 2);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("75%");
    }

    // ========== Integration: selection model callback ==========

    @Test
    void handleClick_updatesSelectionModelCallback() {
        final String[] lastCallbackId = {null};
        selectionModel.setOnSelectionChanged(task ->
            lastCallbackId[0] = task != null ? task.id() : null
        );

        TaskLayout tl = layouts.get(0);
        handler.handleClick(tl.x() + 5, tl.y() + 15);

        assertThat(lastCallbackId[0]).isEqualTo("t1");
    }

    // ========== setData edge cases ==========

    @Test
    void handleClick_noData_returnsEmpty() {
        GanttInteractionHandler emptyHandler = new GanttInteractionHandler(layoutEngine, selectionModel);
        var result = emptyHandler.handleClick(50, 15);
        assertThat(result).isEmpty();
        assertThat(selectionModel.getSelectedIds()).isEmpty();
    }

    @Test
    void handleHover_noData_returnsEmpty() {
        GanttInteractionHandler emptyHandler = new GanttInteractionHandler(layoutEngine, selectionModel);
        var result = emptyHandler.handleHover(50, 15);
        assertThat(result).isEmpty();
    }
}
