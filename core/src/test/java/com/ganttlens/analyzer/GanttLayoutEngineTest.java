package com.ganttlens.analyzer;

import com.ganttlens.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GanttLayoutEngineTest {

    private final GanttLayoutEngine engine = new GanttLayoutEngine();

    private static LayoutConfig defaultConfig(LocalDate start, LocalDate end) {
        return LayoutConfig.withDefaults(start, end);
    }

    private static Task makeTask(String id, String name, LocalDate start, LocalDate end, int days) {
        return new Task(id, name, null, start, end, days, List.of(), List.of(), TaskStatus.PENDING, null);
    }

    // ========== Empty / Edge Cases ==========

    @Test
    void computeLayout_emptyList_returnsEmpty() {
        LayoutConfig config = defaultConfig(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        List<TaskLayout> result = engine.computeLayout(List.of(), Set.of(), config);
        assertThat(result).isEmpty();
    }

    @Test
    void computeLayout_nullList_returnsEmpty() {
        LayoutConfig config = defaultConfig(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        List<TaskLayout> result = engine.computeLayout(null, Set.of(), config);
        assertThat(result).isEmpty();
    }

    // ========== Single Task ==========

    @Test
    void computeLayout_singleTask_correctCoordinates() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 5);
        LayoutConfig config = defaultConfig(start, end);

        Task task = makeTask("t1", "Task A", start, end, 5);
        List<TaskLayout> layouts = engine.computeLayout(List.of(task), Set.of(), config);

        assertThat(layouts).hasSize(1);
        TaskLayout tl = layouts.get(0);
        assertThat(tl.taskId()).isEqualTo("t1");
        assertThat(tl.y()).isEqualTo(0.0); // first row
        assertThat(tl.x()).isEqualTo(config.labelColumnWidth()); // starts at label column
        assertThat(tl.height()).isEqualTo(config.rowHeight());
        assertThat(tl.width()).isGreaterThan(0);
    }

    @Test
    void computeLayout_singleTask_criticalPathMarked() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 5);
        LayoutConfig config = defaultConfig(start, end);

        Task task = makeTask("t1", "Task A", start, end, 5);
        List<TaskLayout> layouts = engine.computeLayout(List.of(task), Set.of("t1"), config);

        assertThat(layouts.get(0).isCriticalPath()).isTrue();
    }

    // ========== Multiple Tasks ==========

    @Test
    void computeLayout_multipleTasks_rowsDoNotOverlap() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 10);
        LayoutConfig config = defaultConfig(start, end);

        List<Task> tasks = List.of(
            makeTask("t1", "A", start, start.plusDays(4), 5),
            makeTask("t2", "B", start.plusDays(3), start.plusDays(7), 5),
            makeTask("t3", "C", start.plusDays(6), end, 5)
        );

        List<TaskLayout> layouts = engine.computeLayout(tasks, Set.of(), config);

        assertThat(layouts).hasSize(3);
        // Y values should be sequential
        assertThat(layouts.get(0).y()).isEqualTo(0.0);
        assertThat(layouts.get(1).y()).isEqualTo(config.rowHeight());
        assertThat(layouts.get(2).y()).isEqualTo(2 * config.rowHeight());
    }

    // ========== Date ↔ Pixel Conversion ==========

    @Test
    void dateToXOffset_zeroDays_returnsZero() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        double offset = engine.dateToXOffset(date, date, 30.0);
        assertThat(offset).isEqualTo(0.0);
    }

    @Test
    void dateToXOffset_fiveDays_returnsCorrectPixels() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate fiveDaysLater = LocalDate.of(2026, 7, 6);
        double offset = engine.dateToXOffset(fiveDaysLater, start, 30.0);
        assertThat(offset).isEqualTo(150.0); // 5 * 30
    }

    @Test
    void xToDate_roundTripConsistent() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        double pixelsPerDay = 30.0;
        double labelWidth = 150.0;

        LocalDate original = LocalDate.of(2026, 7, 8);
        double pixelX = labelWidth + engine.dateToXOffset(original, start, pixelsPerDay);
        LocalDate recovered = engine.xToDate(pixelX, start, pixelsPerDay, labelWidth);

        assertThat(recovered).isEqualTo(original);
    }

    // ========== Hit Test ==========

    @Test
    void hitTest_hit_returnsTaskId() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 5);
        LayoutConfig config = defaultConfig(start, end);

        Task task = makeTask("t1", "Task A", start, end, 5);
        List<TaskLayout> layouts = engine.computeLayout(List.of(task), Set.of(), config);

        // Click in the middle of the task bar
        double clickX = config.labelColumnWidth() + 60; // within the bar
        double clickY = config.rowHeight() / 2; // middle of first row

        Optional<String> hit = engine.hitTest(clickX, clickY, layouts);
        assertThat(hit).isPresent();
        assertThat(hit.get()).isEqualTo("t1");
    }

    @Test
    void hitTest_miss_returnsEmpty() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 5);
        LayoutConfig config = defaultConfig(start, end);

        Task task = makeTask("t1", "Task A", start, end, 5);
        List<TaskLayout> layouts = engine.computeLayout(List.of(task), Set.of(), config);

        // Click in the label area (left of the bar)
        Optional<String> hit = engine.hitTest(10, 15, layouts);
        assertThat(hit).isEmpty();
    }

    // ========== Performance: 200 Tasks ==========

    @Test
    void computeLayout_200Tasks_performanceUnder10ms() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        LayoutConfig config = defaultConfig(start, end);

        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            LocalDate taskStart = start.plusDays(i);
            LocalDate taskEnd = taskStart.plusDays(3);
            tasks.add(makeTask("t" + i, "Task " + i, taskStart, taskEnd, 4));
        }

        Set<String> critIds = tasks.stream().limit(20).map(Task::id).collect(Collectors.toSet());

        long startNanos = System.nanoTime();
        List<TaskLayout> layouts = engine.computeLayout(tasks, critIds, config);
        long elapsedNanos = System.nanoTime() - startNanos;

        assertThat(layouts).hasSize(200);
        assertThat(elapsedNanos).isLessThan(10_000_000); // < 10ms
    }

    // ========== Progress Width ==========

    @Test
    void computeLayout_completedTask_fullProgressWidth() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 5);
        LayoutConfig config = defaultConfig(start, end);

        Task task = new Task("t1", "Done", null, start, end, 5,
            List.of(), List.of(), TaskStatus.COMPLETED, null);
        List<TaskLayout> layouts = engine.computeLayout(List.of(task), Set.of(), config);

        assertThat(layouts.get(0).progressWidth()).isEqualTo(layouts.get(0).width());
    }

    @Test
    void computeLayout_inProgressTask_halfProgressWidth() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 5);
        LayoutConfig config = defaultConfig(start, end);

        Task task = new Task("t1", "WIP", null, start, end, 5,
            List.of(), List.of(), TaskStatus.IN_PROGRESS, null);
        List<TaskLayout> layouts = engine.computeLayout(List.of(task), Set.of(), config);

        assertThat(layouts.get(0).progressWidth()).isCloseTo(layouts.get(0).width() * 0.5,
            org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void computeLayout_pendingTask_zeroProgressWidth() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 5);
        LayoutConfig config = defaultConfig(start, end);

        Task task = makeTask("t1", "Pending", start, end, 5);
        List<TaskLayout> layouts = engine.computeLayout(List.of(task), Set.of(), config);

        assertThat(layouts.get(0).progressWidth()).isEqualTo(0.0);
    }

    // ========== Canvas Size ==========

    @Test
    void computeCanvasSize_emptyTasks_returnsMinimalSize() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LayoutConfig config = defaultConfig(start, start.plusDays(30));
        double[] size = engine.computeCanvasSize(List.of(), config);
        assertThat(size[0]).isEqualTo(config.labelColumnWidth());
        assertThat(size[1]).isEqualTo(config.rowHeight());
    }

    // ========== Task 1: Label Column Width ==========

    @Test
    void computeLayout_withLabelColumnWidth_taskXIncludesOffset() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 5);
        LayoutConfig config = new LayoutConfig(start, end, 30.0, 30.0, 0, 150.0);

        Task task = makeTask("t1", "Task A", start, end, 5);
        List<TaskLayout> layouts = engine.computeLayout(List.of(task), Set.of(), config);

        assertThat(layouts).hasSize(1);
        assertThat(layouts.get(0).x()).isGreaterThanOrEqualTo(150.0);
    }

    @Test
    void hitTest_inLabelColumn_returnsEmpty() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 5);
        LayoutConfig config = new LayoutConfig(start, end, 30.0, 30.0, 0, 150.0);

        Task task = makeTask("t1", "Task A", start, end, 5);
        List<TaskLayout> layouts = engine.computeLayout(List.of(task), Set.of(), config);

        // Click in the label column area (x < labelColumnWidth)
        Optional<String> hit = engine.hitTest(50, 15, layouts);
        assertThat(hit).isEmpty();
    }

    // ========== Task 2: Time Axis Fixed at Top ==========

    @Test
    void computeLayout_withStartY20_firstTaskYEquals20() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 5);
        LayoutConfig config = new LayoutConfig(start, end, 30.0, 30.0, 20.0, 150.0);

        Task task = makeTask("t1", "Task A", start, end, 5);
        List<TaskLayout> layouts = engine.computeLayout(List.of(task), Set.of(), config);

        assertThat(layouts.get(0).y()).isEqualTo(20.0);
    }

    // ========== Task 3: Row Styles ==========

    @Test
    void computeRowStyles_alternatingPattern() {
        boolean[] styles = engine.computeRowStyles(4);
        assertThat(styles).containsExactly(true, false, true, false);
    }

    @Test
    void computeRowStyles_zeroTasks_returnsEmpty() {
        boolean[] styles = engine.computeRowStyles(0);
        assertThat(styles).isEmpty();
    }

    // ========== Task 5: Arrow Path ==========

    @Test
    void computeArrowPath_sameRow_straightLine() {
        TaskLayout from = new TaskLayout("t1", 100, 30, 80, 30, 0, false);
        TaskLayout to   = new TaskLayout("t2", 200, 30, 80, 30, 0, false);

        double[][] path = engine.computeArrowPath(from, to);

        assertThat(path).hasNumberOfRows(2); // Same row: straight horizontal line
        
        // Start at right edge of from (not bottom for same row)
        assertThat(path[0][0]).isEqualTo(180.0); // from.x() + from.width()
        assertThat(path[0][1]).isEqualTo(45.0);  // from.y() + from.height()/2
        
        // End at left edge of to
        assertThat(path[1][0]).isEqualTo(200.0);
        assertThat(path[1][1]).isEqualTo(45.0);  // to.y() + to.height()/2
    }

    @Test
    void computeArrowPath_crossRow_orthogonalBend() {
        TaskLayout from = new TaskLayout("t1", 100, 30, 80, 30, 0, false);
        TaskLayout to   = new TaskLayout("t3", 50,  90, 80, 30, 0, false);

        double[][] path = engine.computeArrowPath(from, to);

        assertThat(path).hasNumberOfRows(3); // L-shaped: bottom -> vertical -> horizontal
        
        // Start at bottom of source task, offset from right edge (8px)
        double expectedStartX = 100 + 80 - 8; // x + width - bottomOffset
        double expectedStartY = 30 + 30;      // y + height (bottom edge)
        assertThat(path[0][0]).isEqualTo(expectedStartX);
        assertThat(path[0][1]).isEqualTo(expectedStartY);
        
        // Vertical down to target's Y level
        double expectedEndY = 90 + 15; // to.y() + to.height()/2
        assertThat(path[1][0]).isEqualTo(expectedStartX);
        assertThat(path[1][1]).isEqualTo(expectedEndY);
        
        // Horizontal to target's left edge
        assertThat(path[2][0]).isEqualTo(50.0);
        assertThat(path[2][1]).isEqualTo(expectedEndY);
    }

    // ========== Task 6: Milestone ==========

    @Test
    void computeMilestoneShape_returnsDiamondPoints() {
        double[] shape = engine.computeMilestoneShape(100, 50, 8);

        assertThat(shape).hasSize(8);
        // Top
        assertThat(shape[0]).isEqualTo(100.0);
        assertThat(shape[1]).isEqualTo(42.0);
        // Right
        assertThat(shape[2]).isEqualTo(108.0);
        assertThat(shape[3]).isEqualTo(50.0);
        // Bottom
        assertThat(shape[4]).isEqualTo(100.0);
        assertThat(shape[5]).isEqualTo(58.0);
        // Left
        assertThat(shape[6]).isEqualTo(92.0);
        assertThat(shape[7]).isEqualTo(50.0);
    }

    @Test
    void computeLayout_zeroDuration_marksAsMilestone() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        LayoutConfig config = defaultConfig(date, date.plusDays(5));

        Task milestone = new Task("m1", "Release", null, date, date, 0,
            List.of(), List.of(), TaskStatus.PENDING, null);
        List<TaskLayout> layouts = engine.computeLayout(List.of(milestone), Set.of(), config);

        assertThat(layouts.get(0).isMilestone()).isTrue();
    }

    @Test
    void computeLayout_nonDuration_notMilestone() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 5);
        LayoutConfig config = defaultConfig(start, end);

        Task task = makeTask("t1", "Task A", start, end, 5);
        List<TaskLayout> layouts = engine.computeLayout(List.of(task), Set.of(), config);

        assertThat(layouts.get(0).isMilestone()).isFalse();
    }

    // ========== Task 7: Group Headers ==========

    @Test
    void computeLayout_withGroups_insertsGroupHeaders() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 10);
        LayoutConfig config = defaultConfig(start, end);

        List<Task> tasks = List.of(
            new Task("t1", "A", "Phase1", start, start.plusDays(2), 3,
                List.of(), List.of(), TaskStatus.PENDING, null),
            new Task("t2", "B", "Phase1", start.plusDays(3), start.plusDays(5), 3,
                List.of(), List.of(), TaskStatus.PENDING, null),
            new Task("t3", "C", "Phase2", start.plusDays(6), end, 5,
                List.of(), List.of(), TaskStatus.PENDING, null)
        );

        List<TaskLayout> layouts = engine.computeLayout(tasks, Set.of(), config);

        // Should have 3 tasks + 2 group headers = 5 layouts
        long groupHeaders = layouts.stream().filter(TaskLayout::isGroupHeader).count();
        assertThat(groupHeaders).isEqualTo(2);

        // First entry should be a group header
        assertThat(layouts.get(0).isGroupHeader()).isTrue();
        assertThat(layouts.get(0).groupLabel()).isEqualTo("Phase1");

        // Second task should come after the group header
        TaskLayout firstTask = layouts.stream()
            .filter(l -> !l.isGroupHeader())
            .findFirst().orElseThrow();
        assertThat(firstTask.y()).isGreaterThan(layouts.get(0).y());
    }

    @Test
    void computeLayout_noGroups_noGroupHeaders() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 10);
        LayoutConfig config = defaultConfig(start, end);

        List<Task> tasks = List.of(
            makeTask("t1", "A", start, start.plusDays(2), 3),
            makeTask("t2", "B", start.plusDays(3), start.plusDays(5), 3)
        );

        List<TaskLayout> layouts = engine.computeLayout(tasks, Set.of(), config);

        long groupHeaders = layouts.stream().filter(TaskLayout::isGroupHeader).count();
        assertThat(groupHeaders).isEqualTo(0);
        assertThat(layouts).hasSize(2);
    }

    // ========== Task 8: Progress Percentage ==========

    @Test
    void computeLayout_progressPercent0_progressWidthZero() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 5);
        LayoutConfig config = defaultConfig(start, end);

        Task task = new Task("t1", "Pending", null, start, end, 5,
            List.of(), List.of(), TaskStatus.PENDING, null, 0);
        List<TaskLayout> layouts = engine.computeLayout(List.of(task), Set.of(), config);

        assertThat(layouts.get(0).progressWidth()).isEqualTo(0.0);
    }

    @Test
    void computeLayout_progressPercent75_correctWidth() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 5);
        LayoutConfig config = defaultConfig(start, end);

        Task task = new Task("t1", "WIP", null, start, end, 5,
            List.of(), List.of(), TaskStatus.IN_PROGRESS, null, 75);
        List<TaskLayout> layouts = engine.computeLayout(List.of(task), Set.of(), config);

        double expectedWidth = layouts.get(0).width() * 0.75;
        assertThat(layouts.get(0).progressWidth())
            .isCloseTo(expectedWidth, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void computeLayout_completed_progressEqualsWidth() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 5);
        LayoutConfig config = defaultConfig(start, end);

        Task task = new Task("t1", "Done", null, start, end, 5,
            List.of(), List.of(), TaskStatus.COMPLETED, null, 100);
        List<TaskLayout> layouts = engine.computeLayout(List.of(task), Set.of(), config);

        assertThat(layouts.get(0).progressWidth()).isEqualTo(layouts.get(0).width());
    }

    @Test
    void computeLayout_progressPercent30_correctWidth() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 5);
        LayoutConfig config = defaultConfig(start, end);

        Task task = new Task("t1", "Early", null, start, end, 5,
            List.of(), List.of(), TaskStatus.IN_PROGRESS, null, 30);
        List<TaskLayout> layouts = engine.computeLayout(List.of(task), Set.of(), config);

        double expectedWidth = layouts.get(0).width() * 0.30;
        assertThat(layouts.get(0).progressWidth())
            .isCloseTo(expectedWidth, org.assertj.core.data.Offset.offset(0.01));
    }
}
