package com.ganttlens.analyzer;

import com.ganttlens.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WorkloadAnalyzerTest {

    private final WorkloadAnalyzer analyzer = new WorkloadAnalyzer();

    @Test
    void analyzeDailyLoads_singleTask() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        Task task = new Task(
            "task-1", "开发", null,
            start, start.plusDays(2),
            3,
            List.of(Assignment.fullLoad("张三")),
            List.of(),
            TaskStatus.PENDING
        );

        GanttSchedule schedule = new GanttSchedule(
            new ScheduleConfig("", null, false, false, Set.of(), Set.of()),
            List.of(task)
        );

        List<PersonDailyLoad> loads = analyzer.analyzeDailyLoads(schedule);

        assertThat(loads).hasSize(3);
        assertThat(loads).allMatch(l -> l.person().equals("张三"));
        assertThat(loads).allMatch(l -> l.totalLoad() == 1.0);
    }

    @Test
    void analyzeDailyLoads_partialRatio() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        Task task = new Task(
            "task-1", "设计", null,
            start, start,
            1,
            List.of(Assignment.withPercent("李四", 50)),
            List.of(),
            TaskStatus.PENDING
        );

        GanttSchedule schedule = new GanttSchedule(
            new ScheduleConfig("", null, false, false, Set.of(), Set.of()),
            List.of(task)
        );

        List<PersonDailyLoad> loads = analyzer.analyzeDailyLoads(schedule);

        assertThat(loads).hasSize(1);
        assertThat(loads.get(0).totalLoad()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void analyzeDailyLoads_multipleTasksSamePerson() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        Task taskA = new Task(
            "task-1", "任务A", null,
            start, start.plusDays(1),
            2,
            List.of(Assignment.fullLoad("张三")),
            List.of(),
            TaskStatus.PENDING
        );
        Task taskB = new Task(
            "task-2", "任务B", null,
            start, start,
            1,
            List.of(Assignment.fullLoad("张三")),
            List.of(),
            TaskStatus.PENDING
        );

        GanttSchedule schedule = new GanttSchedule(
            new ScheduleConfig("", null, false, false, Set.of(), Set.of()),
            List.of(taskA, taskB)
        );

        List<PersonDailyLoad> loads = analyzer.analyzeDailyLoads(schedule);

        // Day 1: 200% load (both tasks)
        PersonDailyLoad day1 = loads.stream()
            .filter(l -> l.date().equals(start))
            .findFirst().orElseThrow();
        assertThat(day1.totalLoad()).isCloseTo(2.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(day1.tasks()).hasSize(2);

        // Day 2: 100% load (only taskA)
        PersonDailyLoad day2 = loads.stream()
            .filter(l -> l.date().equals(start.plusDays(1)))
            .findFirst().orElseThrow();
        assertThat(day2.totalLoad()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(day2.tasks()).hasSize(1);
    }

    @Test
    void analyzeDailyLoads_skipsPersonOffDays() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate offDay = LocalDate.of(2026, 7, 2);
        Task task = new Task(
            "task-1", "开发", null,
            start, start.plusDays(2),
            3,
            List.of(Assignment.fullLoad("张三")),
            List.of(),
            TaskStatus.PENDING
        );

        GanttSchedule schedule = new GanttSchedule(
            new ScheduleConfig("", null, false, false, Set.of(),
                Set.of(new ScheduleConfig.PersonOffEntry("张三", offDay))),
            List.of(task)
        );

        List<PersonDailyLoad> loads = analyzer.analyzeDailyLoads(schedule);

        // Should have 2 entries (day 1 and day 3), not 3
        assertThat(loads).hasSize(2);
        assertThat(loads).noneMatch(l -> l.date().equals(offDay));
        assertThat(loads).allMatch(l -> l.totalLoad() == 1.0);
    }

    @Test
    void analyzeDailyLoads_personOffDoesNotAffectOtherPersons() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        Task task = new Task(
            "task-1", "开发", null,
            start, start.plusDays(1),
            2,
            List.of(Assignment.fullLoad("张三"), Assignment.fullLoad("李四")),
            List.of(),
            TaskStatus.PENDING
        );

        GanttSchedule schedule = new GanttSchedule(
            new ScheduleConfig("", null, false, false, Set.of(),
                Set.of(new ScheduleConfig.PersonOffEntry("张三", start))),
            List.of(task)
        );

        List<PersonDailyLoad> loads = analyzer.analyzeDailyLoads(schedule);

        // 张三: 1 entry (day 2 only), 李四: 2 entries
        long zhangsanCount = loads.stream().filter(l -> l.person().equals("张三")).count();
        long lisiCount = loads.stream().filter(l -> l.person().equals("李四")).count();
        assertThat(zhangsanCount).isEqualTo(1);
        assertThat(lisiCount).isEqualTo(2);
    }

    @Test
    void computePersonManDays_basic() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        Task task = new Task(
            "task-1", "开发", null,
            start, start.plusDays(2),
            3,
            List.of(Assignment.fullLoad("张三"), Assignment.withPercent("李四", 50)),
            List.of(),
            TaskStatus.PENDING
        );

        GanttSchedule schedule = new GanttSchedule(
            new ScheduleConfig("", null, false, false, Set.of(), Set.of()),
            List.of(task)
        );

        Map<String, Double> manDays = analyzer.computePersonManDays(schedule);

        assertThat(manDays).containsEntry("张三", 3.0);
        assertThat(manDays).containsEntry("李四", 1.5);
    }

    @Test
    void analyzeDailyLoads_sortedByPersonThenDate() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        Task taskA = new Task(
            "task-1", "任务A", null,
            start, start,
            1,
            List.of(Assignment.fullLoad("李四")),
            List.of(),
            TaskStatus.PENDING
        );
        Task taskB = new Task(
            "task-2", "任务B", null,
            start, start,
            1,
            List.of(Assignment.fullLoad("张三")),
            List.of(),
            TaskStatus.PENDING
        );

        GanttSchedule schedule = new GanttSchedule(
            new ScheduleConfig("", null, false, false, Set.of(), Set.of()),
            List.of(taskA, taskB)
        );

        List<PersonDailyLoad> loads = analyzer.analyzeDailyLoads(schedule);

        assertThat(loads.get(0).person()).isEqualTo("张三");
        assertThat(loads.get(1).person()).isEqualTo("李四");
    }
}
