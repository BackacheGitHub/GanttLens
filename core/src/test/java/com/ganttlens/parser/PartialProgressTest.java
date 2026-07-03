package com.ganttlens.parser;

import com.ganttlens.analyzer.AnalysisEngine;
import com.ganttlens.analyzer.GanttLayoutEngine;
import com.ganttlens.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diagnostic tests for the "is X% complete" partial progress feature.
 * Verifies the full pipeline: parse → layout → progressWidth.
 */
class PartialProgressTest {

    private static final String GUI_DEFAULT_TEXT = """
            @startgantt
            title Sample Project
            project starts 2026-07-01
            saturday are closed
            sunday are closed

            -- Phase 1 Planning --
            [Requirements] on {Alice} lasts 3 days is completed
            [Design] on {Bob:50%} starts at [Requirements]'s end lasts 4 days is 60% complete

            -- Phase 2 Build --
            [Coding] on {Alice:80%} {Bob:80%} starts at [Design]'s end lasts 5 days
            [Testing] on {Charlie} starts at [Coding]'s end lasts 3 days

            Release happens at [Testing]'s end
            @endgantt
            """;

    private final GanttFileParser parser = new GanttFileParser();
    private final AnalysisEngine engine = new AnalysisEngine();
    private final GanttLayoutEngine layoutEngine = new GanttLayoutEngine();

    @Test
    void designTask_hasProgressPercent60() {
        GanttSchedule schedule = parser.parse(GUI_DEFAULT_TEXT);

        for (Task t : schedule.tasks()) {
            System.out.printf("Task: %-15s status=%-12s progress=%d%%%n",
                t.name(), t.status(), t.progressPercent());
        }

        Task design = schedule.tasks().stream()
            .filter(t -> t.name().equals("Design"))
            .findFirst().orElseThrow();

        assertThat(design.progressPercent())
            .as("Design should have 60%% progress")
            .isEqualTo(60);
        assertThat(design.status())
            .as("Design should be IN_PROGRESS")
            .isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void requirementsTask_hasProgressPercent100() {
        GanttSchedule schedule = parser.parse(GUI_DEFAULT_TEXT);

        Task req = schedule.tasks().stream()
            .filter(t -> t.name().equals("Requirements"))
            .findFirst().orElseThrow();

        assertThat(req.progressPercent()).isEqualTo(100);
        assertThat(req.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void codingTask_hasProgressPercent0() {
        GanttSchedule schedule = parser.parse(GUI_DEFAULT_TEXT);

        Task coding = schedule.tasks().stream()
            .filter(t -> t.name().equals("Coding"))
            .findFirst().orElseThrow();

        assertThat(coding.progressPercent()).isEqualTo(0);
        assertThat(coding.status()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    void endToEnd_designProgressWidth_isPositive() {
        GanttSchedule schedule = parser.parse(GUI_DEFAULT_TEXT);
        ProjectStats stats = engine.analyze(schedule);

        List<Task> tasks = schedule.tasks();
        LocalDate minDate = tasks.stream().map(Task::startDate).filter(d -> d != null)
            .min(LocalDate::compareTo).orElse(LocalDate.now()).minusDays(2);
        LocalDate maxDate = tasks.stream().map(Task::endDate).filter(d -> d != null)
            .max(LocalDate::compareTo).orElse(minDate.plusDays(30)).plusDays(2);

        LayoutConfig config = new LayoutConfig(
            minDate, maxDate, LayoutConfig.DEFAULT_PIXELS_PER_DAY,
            LayoutConfig.DEFAULT_ROW_HEIGHT, 20.0, LayoutConfig.DEFAULT_LABEL_COLUMN_WIDTH
        );

        Set<String> critIds = stats.criticalPath() != null
            ? Set.copyOf(stats.criticalPath().criticalTaskIds()) : Set.of();
        List<TaskLayout> layouts = layoutEngine.computeLayout(tasks, critIds, config);

        // Find Design task layout (skip group headers)
        TaskLayout designLayout = null;
        int taskIdx = 0;
        for (TaskLayout tl : layouts) {
            if (tl.isGroupHeader()) continue;
            Task t = tasks.get(taskIdx++);
            if (t.name().equals("Design")) {
                designLayout = tl;
                break;
            }
        }

        assertThat(designLayout).isNotNull();
        System.out.printf("Design layout: x=%.1f width=%.1f progressWidth=%.1f%n",
            designLayout.x(), designLayout.width(), designLayout.progressWidth());

        assertThat(designLayout.progressWidth())
            .as("Design progressWidth should be > 0")
            .isGreaterThan(0);
        assertThat(designLayout.progressWidth())
            .as("Design progressWidth should be 60%% of width")
            .isCloseTo(designLayout.width() * 0.6,
                org.assertj.core.data.Offset.offset(0.01));
    }
}
