package com.ganttlens.analyzer;

import com.ganttlens.model.*;
import com.ganttlens.parser.GanttFileParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Critical Path Analyzer")
class CriticalPathAnalyzerTest {

    private final GanttFileParser parser = new GanttFileParser();
    private final AnalysisEngine engine = new AnalysisEngine();

    @Test
    @DisplayName("Simple linear dependency - all tasks on critical path")
    void simpleLinearDependency_allTasksCritical() {
        String puml = """
            @startgantt
            project starts 2026-07-01
            [Design] lasts 3 days
            [Coding] starts at [Design]'s end lasts 5 days
            [Testing] starts at [Coding]'s end lasts 2 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);
        ProjectStats stats = engine.analyze(schedule);

        CriticalPathResult cp = stats.criticalPath();
        assertThat(cp).isNotNull();
        assertThat(cp.totalDurationDays()).isEqualTo(10);
        assertThat(cp.criticalTaskIds()).hasSize(3);
        // All tasks should be on critical path
        for (Task task : schedule.tasks()) {
            assertThat(cp.taskFloats().get(task.name())).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Parallel tasks - only longest path is critical")
    void parallelTasks_longestPathCritical() {
        String puml = """
            @startgantt
            project starts 2026-07-01
            [TaskA] lasts 3 days
            [TaskB] lasts 5 days
            [TaskC] starts at [TaskA]'s end lasts 2 days
            [TaskD] starts at [TaskB]'s end lasts 1 day
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);
        ProjectStats stats = engine.analyze(schedule);

        CriticalPathResult cp = stats.criticalPath();
        assertThat(cp).isNotNull();
        // Critical path: TaskB(5) + TaskD(1) = 6 days
        assertThat(cp.totalDurationDays()).isEqualTo(6);

        // TaskB and TaskD should have 0 float
        assertThat(cp.taskFloats().get("TaskB")).isEqualTo(0);
        assertThat(cp.taskFloats().get("TaskD")).isEqualTo(0);

        // TaskA and TaskC should have float > 0
        assertThat(cp.taskFloats().get("TaskA")).isGreaterThan(0);
        assertThat(cp.taskFloats().get("TaskC")).isGreaterThan(0);
    }

    @Test
    @DisplayName("Diamond dependency - correct critical path")
    void diamondDependency_correctCriticalPath() {
        String puml = """
            @startgantt
            project starts 2026-07-01
            [Start] lasts 1 day
            [TaskA] starts at [Start]'s end lasts 3 days
            [TaskB] starts at [Start]'s end lasts 5 days
            [End] starts at [TaskB]'s end lasts 1 day
            [TaskA] -> [End]
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);
        ProjectStats stats = engine.analyze(schedule);

        CriticalPathResult cp = stats.criticalPath();
        assertThat(cp).isNotNull();
        // Critical path: Start(1) + TaskB(5) + End(1) = 7 days
        assertThat(cp.totalDurationDays()).isEqualTo(7);

        assertThat(cp.taskFloats().get("Start")).isEqualTo(0);
        assertThat(cp.taskFloats().get("TaskB")).isEqualTo(0);
        assertThat(cp.taskFloats().get("End")).isEqualTo(0);
        // TaskA has float: 5 - 3 = 2 days
        assertThat(cp.taskFloats().get("TaskA")).isEqualTo(2);
    }

    @Test
    @DisplayName("Empty schedule - returns empty result")
    void emptySchedule_returnsEmptyResult() {
        String puml = """
            @startgantt
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);
        ProjectStats stats = engine.analyze(schedule);

        CriticalPathResult cp = stats.criticalPath();
        assertThat(cp).isNotNull();
        assertThat(cp.criticalTaskIds()).isEmpty();
        assertThat(cp.totalDurationDays()).isEqualTo(0);
        assertThat(cp.taskFloats()).isEmpty();
    }

    @Test
    @DisplayName("Single task - only task is critical")
    void singleTask_onlyTaskCritical() {
        String puml = """
            @startgantt
            [Solo] lasts 5 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);
        ProjectStats stats = engine.analyze(schedule);

        CriticalPathResult cp = stats.criticalPath();
        assertThat(cp).isNotNull();
        assertThat(cp.totalDurationDays()).isEqualTo(5);
        assertThat(cp.criticalTaskIds()).hasSize(1);
        assertThat(cp.taskFloats().get("Solo")).isEqualTo(0);
    }

    @Test
    @DisplayName("Complex project - identifies correct critical path")
    void complexProject_identifiesCorrectCriticalPath() {
        String puml = """
            @startgantt
            title Critical Path Test
            project starts 2026-07-01
            saturday are closed
            sunday are closed

            [Requirements] lasts 5 days
            [Design] starts at [Requirements]'s end lasts 7 days
            [Coding] starts at [Design]'s end lasts 14 days
            [Testing] starts at [Coding]'s end lasts 5 days

            [Documentation] starts at [Requirements]'s end lasts 3 days

            Release happens at [Testing]'s end
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);
        ProjectStats stats = engine.analyze(schedule);

        CriticalPathResult cp = stats.criticalPath();
        assertThat(cp).isNotNull();
        // Critical path: Requirements(5) + Design(7) + Coding(14) + Testing(5) = 31 days
        assertThat(cp.totalDurationDays()).isEqualTo(31);

        // Documentation should have float
        assertThat(cp.taskFloats().get("Documentation")).isGreaterThan(0);
    }
}
