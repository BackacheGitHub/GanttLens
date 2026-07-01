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
}
