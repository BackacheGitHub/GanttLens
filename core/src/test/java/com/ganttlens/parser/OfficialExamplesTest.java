package com.ganttlens.parser;

import com.ganttlens.model.GanttSchedule;
import com.ganttlens.model.Task;
import com.ganttlens.model.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * File-based tests for official PlantUML Gantt examples.
 * Source: https://plantuml.com/gantt-diagram
 */
@DisplayName("Official Examples - PlantUML Gantt Syntax")
class OfficialExamplesTest {

    private final GanttFileParser parser = new GanttFileParser();

    private String readResource(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/official-examples/" + name));
    }

    // ========== workload.puml ==========

    @Test
    @DisplayName("Workload - requires with days and weeks")
    void workload_parsesDurationUnits() throws IOException {
        String content = readResource("workload.puml");
        GanttSchedule schedule = parser.parse(content);

        // 6 tasks (group header is not a task entry)
        assertThat(schedule.tasks()).hasSize(6);

        // Task durations: days
        assertThat(schedule.tasks().get(0).name()).isEqualTo("Prototype design");
        assertThat(schedule.tasks().get(0).durationDays()).isEqualTo(15);

        assertThat(schedule.tasks().get(1).name()).isEqualTo("Test prototype");
        assertThat(schedule.tasks().get(1).durationDays()).isEqualTo(10);

        // Tasks after group header (group name set via currentGroup)
        assertThat(schedule.tasks().get(2).name()).isEqualTo("Task1");
        assertThat(schedule.tasks().get(2).durationDays()).isEqualTo(1);   // 1 days
        assertThat(schedule.tasks().get(3).name()).isEqualTo("T2");
        assertThat(schedule.tasks().get(3).durationDays()).isEqualTo(5);   // 5 days
        assertThat(schedule.tasks().get(4).name()).isEqualTo("T3");
        assertThat(schedule.tasks().get(4).durationDays()).isEqualTo(5);   // 1 week = 5 working days
        assertThat(schedule.tasks().get(5).name()).isEqualTo("T5");
        assertThat(schedule.tasks().get(5).durationDays()).isEqualTo(10);  // 2 weeks = 10 working days
    }

    // ========== constraints.puml ==========

    @Test
    @DisplayName("Constraints - starts at task's end")
    void constraints_parsesStartsAtDependency() throws IOException {
        String content = readResource("constraints.puml");
        GanttSchedule schedule = parser.parse(content);

        // 2 tasks (Test prototype declaration + constraint merged)
        assertThat(schedule.tasks()).hasSize(2);

        Task prototype = schedule.tasks().get(0);
        assertThat(prototype.name()).isEqualTo("Prototype design");
        assertThat(prototype.durationDays()).isEqualTo(15);
        assertThat(prototype.dependencyIds()).isEmpty();

        Task test = schedule.tasks().get(1);
        assertThat(test.name()).isEqualTo("Test prototype");
        assertThat(test.durationDays()).isEqualTo(10);
        assertThat(test.dependencyIds()).containsExactly("Prototype design");
    }

    // ========== short-names-alias.puml ==========

    @Test
    @DisplayName("Short names / alias - as keyword with dependency via alias")
    void alias_resolvesAliasInDependency() throws IOException {
        String content = readResource("short-names-alias.puml");
        GanttSchedule schedule = parser.parse(content);

        // 2 tasks (T referenced by alias is merged into Test prototype)
        assertThat(schedule.tasks()).hasSize(2);

        Task design = schedule.tasks().get(0);
        assertThat(design.name()).isEqualTo("Prototype design");
        assertThat(design.durationDays()).isEqualTo(15);

        Task test = schedule.tasks().get(1);
        assertThat(test.name()).isEqualTo("Test prototype");
        assertThat(test.durationDays()).isEqualTo(10);
        // [T] starts at [D]'s end -> resolves alias D->Prototype design in dependency
        assertThat(test.dependencyIds()).containsExactly("Prototype design");
    }

    // ========== completion-status.puml ==========

    @Test
    @DisplayName("Completion status - is X% complete")
    void completionStatus_parsesPartialProgress() throws IOException {
        String content = readResource("completion-status.puml");
        GanttSchedule schedule = parser.parse(content);

        // 2 tasks (progress lines are merged into declaration tasks)
        assertThat(schedule.tasks()).hasSize(2);

        // foo: requires 21 days + is 40% complete (merged)
        Task foo = schedule.tasks().get(0);
        assertThat(foo.name()).isEqualTo("foo");
        assertThat(foo.durationDays()).isEqualTo(21);
        assertThat(foo.progressPercent()).isEqualTo(40);
        assertThat(foo.status()).isEqualTo(TaskStatus.IN_PROGRESS);

        // bar: requires 30 days + is 10% complete (merged)
        Task bar = schedule.tasks().get(1);
        assertThat(bar.name()).isEqualTo("bar");
        assertThat(bar.durationDays()).isEqualTo(30);
        assertThat(bar.progressPercent()).isEqualTo(10);
        assertThat(bar.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    // ========== overload.puml ==========

    @Test
    @DisplayName("Overload - resource overload scenario")
    void overload_parsesResourceOverloadScenario() throws IOException {
        String content = readResource("overload.puml");
        GanttSchedule schedule = parser.parse(content);

        assertThat(schedule.tasks()).hasSize(3);
        assertThat(schedule.tasks().get(0).name()).isEqualTo("任务A");
        assertThat(schedule.tasks().get(1).name()).isEqualTo("任务B");
        assertThat(schedule.tasks().get(2).name()).isEqualTo("任务C");

        // Both B and C depend on A
        assertThat(schedule.tasks().get(1).dependencyIds()).containsExactly("任务A");
        assertThat(schedule.tasks().get(2).dependencyIds()).containsExactly("任务A");
    }
}
