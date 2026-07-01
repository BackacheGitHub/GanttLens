package com.ganttlens.analyzer;

import com.ganttlens.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OverloadCheckerTest {

    @Test
    void detectOverload() {
        LocalDate start = LocalDate.of(2026, 7, 1);

        Task taskA = new Task(
            "task-1", "任务A", null,
            start, start.plusDays(2),
            3,
            List.of(Assignment.fullLoad("张三")),
            List.of(),
            TaskStatus.PENDING,
            null
        );

        Task taskB = new Task(
            "task-2", "任务B", null,
            start, start.plusDays(2),
            3,
            List.of(Assignment.fullLoad("张三")),
            List.of(),
            TaskStatus.PENDING,
            null
        );

        GanttSchedule schedule = new GanttSchedule(
            new ScheduleConfig("", null, false, false, java.util.Set.of(), java.util.Set.of()),
            List.of(taskA, taskB)
        );

        WorkloadAnalyzer analyzer = new WorkloadAnalyzer();
        OverloadChecker checker = new OverloadChecker(analyzer);

        List<OverloadRecord> overloads = checker.check(schedule);

        assertThat(overloads).isNotEmpty();
        assertThat(overloads.get(0).person()).isEqualTo("张三");
        assertThat(overloads.get(0).totalLoad()).isCloseTo(2.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void noOverloadWhenSingleTask() {
        LocalDate start = LocalDate.of(2026, 7, 1);

        Task taskA = new Task(
            "task-1", "任务A", null,
            start, start.plusDays(2),
            3,
            List.of(Assignment.fullLoad("张三")),
            List.of(),
            TaskStatus.PENDING,
            null
        );

        GanttSchedule schedule = new GanttSchedule(
            new ScheduleConfig("", null, false, false, java.util.Set.of(), java.util.Set.of()),
            List.of(taskA)
        );

        WorkloadAnalyzer analyzer = new WorkloadAnalyzer();
        OverloadChecker checker = new OverloadChecker(analyzer);

        List<OverloadRecord> overloads = checker.check(schedule);

        assertThat(overloads).isEmpty();
    }

    @Test
    void detectPartialOverload() {
        LocalDate start = LocalDate.of(2026, 7, 1);

        Task taskA = new Task(
            "task-1", "任务A", null,
            start, start.plusDays(2),
            3,
            List.of(Assignment.fullLoad("张三")),
            List.of(),
            TaskStatus.PENDING,
            null
        );

        Task taskB = new Task(
            "task-2", "任务B", null,
            start, start.plusDays(2),
            3,
            List.of(Assignment.withPercent("张三", 60)),
            List.of(),
            TaskStatus.PENDING,
            null
        );

        GanttSchedule schedule = new GanttSchedule(
            new ScheduleConfig("", null, false, false, java.util.Set.of(), java.util.Set.of()),
            List.of(taskA, taskB)
        );

        WorkloadAnalyzer analyzer = new WorkloadAnalyzer();
        OverloadChecker checker = new OverloadChecker(analyzer);

        List<OverloadRecord> overloads = checker.check(schedule);

        assertThat(overloads).isNotEmpty();
        assertThat(overloads.get(0).totalLoad()).isCloseTo(1.6, org.assertj.core.data.Offset.offset(0.01));
    }
}
