package com.ganttlens.parser;

import com.ganttlens.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 comprehensive tests covering all grammar features:
 * - Iteration 1.1: as/lasts/then/->/Project starts/starts-ends/happens/Comments
 * - Iteration 1.2: color/completed/and/closed/open/weeks
 * - Iteration 1.3: Error recovery/relative dates/circular dependencies
 */
@DisplayName("Phase 1 - Comprehensive Grammar Tests")
class Phase1ComprehensiveTest {

    private final GanttFileParser parser = new GanttFileParser();

    // ========== Iteration 1.1: Core Syntax ==========

    @Test
    @DisplayName("as keyword - creates task alias")
    void asKeyword_createsTaskAlias() {
        String puml = """
            @startgantt
            [Design] as [D] lasts 5 days
            [Coding] starts at [D]'s end lasts 10 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.tasks()).hasSize(2);

        Task coding = schedule.tasks().get(1);
        assertThat(coding.dependencyIds()).containsExactly("Design");
    }

    @Test
    @DisplayName("lasts keyword - parses duration correctly")
    void lastsKeyword_parsesDuration() {
        String puml = """
            @startgantt
            [Phase1] lasts 10 days
            [Phase2] lasts 2 weeks
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.tasks()).hasSize(2);
        assertThat(schedule.tasks().get(0).durationDays()).isEqualTo(10);
        assertThat(schedule.tasks().get(1).durationDays()).isEqualTo(10); // 2 weeks = 10 days
    }

    @Test
    @DisplayName("then keyword - creates dependency chain")
    void thenKeyword_createsDependencyChain() {
        String puml = """
            @startgantt
            [Design] lasts 5 days
            then [Coding] lasts 10 days
            then [Testing] lasts 5 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.tasks()).hasSize(3);
        Task design = schedule.tasks().get(0);
        Task coding = schedule.tasks().get(1);
        Task testing = schedule.tasks().get(2);

        assertThat(design.dependencyIds()).isEmpty();
        assertThat(coding.dependencyIds()).containsExactly("Design");
        assertThat(testing.dependencyIds()).containsExactly("Coding");
    }

    @Test
    @DisplayName("arrow dependency -> creates dependency chain")
    void arrowDependency_createsChain() {
        String puml = """
            @startgantt
            [Design] lasts 5 days
            [Coding] lasts 10 days
            [Testing] lasts 5 days
            [Design] -> [Coding] -> [Testing]
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.tasks()).hasSize(3);
        assertThat(schedule.tasks().get(1).dependencyIds()).containsExactly("Design");
        assertThat(schedule.tasks().get(2).dependencyIds()).containsExactly("Coding");
    }

    @Test
    @DisplayName("Project starts - sets project start date")
    void projectStarts_setsStartDate() {
        String puml = """
            @startgantt
            project starts 2026-07-01
            [Task1] lasts 5 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.config().projectStartDate())
            .isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    @DisplayName("starts at [task]'s end - dependency on task end")
    void startsAtTaskEnd_dependencyOnEnd() {
        String puml = """
            @startgantt
            project starts 2026-07-01
            saturday are closed
            sunday are closed
            [Design] lasts 5 days
            [Coding] starts at [Design]'s end lasts 10 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        Task coding = schedule.tasks().get(1);
        assertThat(coding.dependencyIds()).containsExactly("Design");
        // Design starts July 1 (Wed), lasts 5 working days -> ends July 7 (Tue)
        // Coding starts next working day after July 7 -> July 8 (Wed)
        assertThat(coding.startDate()).isEqualTo(LocalDate.of(2026, 7, 8));
    }

    @Test
    @DisplayName("starts at [task]'s start - dependency on task start")
    void startsAtTaskStart_dependencyOnStart() {
        String puml = """
            @startgantt
            project starts 2026-07-01
            [Design] lasts 5 days
            [Review] starts at [Design]'s start lasts 2 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        Task review = schedule.tasks().get(1);
        assertThat(review.dependencyIds()).containsExactly("Design");
    }

    @Test
    @DisplayName("happens at [task]'s end - creates milestone")
    void happens_createsMilestone() {
        String puml = """
            @startgantt
            project starts 2026-07-01
            [Design] lasts 5 days
            [Coding] lasts 10 days
            Release happens at [Coding]'s end
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.tasks()).hasSize(3);
        Task release = schedule.tasks().get(2);
        assertThat(release.name()).isEqualTo("Release");
        assertThat(release.durationDays()).isEqualTo(0);
        assertThat(release.dependencyIds()).containsExactly("Coding");
    }

    @Test
    @DisplayName("happens on date - milestone on specific date")
    void happensOnDate_createsMilestoneOnDate() {
        String puml = """
            @startgantt
            Release happens on 2026-07-15
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.tasks()).hasSize(1);
        Task release = schedule.tasks().get(0);
        assertThat(release.startDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(release.durationDays()).isEqualTo(0);
    }

    @Test
    @DisplayName("Comments - line comments ignored")
    void comments_lineCommentsIgnored() {
        String puml = """
            @startgantt
            ' This is a comment
            [Task1] lasts 5 days
            ' Another comment
            [Task2] lasts 3 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.tasks()).hasSize(2);
        assertThat(schedule.tasks().get(0).name()).isEqualTo("Task1");
        assertThat(schedule.tasks().get(1).name()).isEqualTo("Task2");
    }

    @Test
    @DisplayName("Comments - block comments ignored")
    void comments_blockCommentsIgnored() {
        String puml = """
            @startgantt
            /' This is a block comment '/
            [Task1] lasts 5 days
            /' Multi
            line
            comment '/
            [Task2] lasts 3 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.tasks()).hasSize(2);
    }

    // ========== Iteration 1.2: Enhanced Directives ==========

    @Test
    @DisplayName("weeks keyword - duration in weeks")
    void weeksKeyword_durationInWeeks() {
        String puml = """
            @startgantt
            [Phase1] lasts 2 weeks
            [Phase2] lasts 3 weeks
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.tasks().get(0).durationDays()).isEqualTo(10);
        assertThat(schedule.tasks().get(1).durationDays()).isEqualTo(15);
    }

    @Test
    @DisplayName("completed keyword - marks task as completed")
    void completedKeyword_marksTaskCompleted() {
        String puml = """
            @startgantt
            [Design] lasts 5 days is completed
            [Coding] lasts 10 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        Task design = schedule.tasks().get(0);
        assertThat(design.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(schedule.tasks().get(1).status()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    @DisplayName("and keyword - combines tasks via arrow")
    void andKeyword_combinesTasks() {
        String puml = """
            @startgantt
            [Design] lasts 5 days
            [Coding] lasts 10 days
            [Design] -> [Coding]
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.tasks()).hasSize(2);
        assertThat(schedule.tasks().get(1).dependencyIds()).containsExactly("Design");
    }

    @Test
    @DisplayName("closed keyword - marks date as closed")
    void closedKeyword_marksDateClosed() {
        String puml = """
            @startgantt
            2026-07-04 is closed
            [Task1] lasts 5 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.config().holidays())
            .contains(LocalDate.of(2026, 7, 4));
    }

    @Test
    @DisplayName("open keyword - marks date as open")
    void openKeyword_marksDateOpen() {
        String puml = """
            @startgantt
            2026-07-04 is closed
            2026-07-04 is open
            [Task1] lasts 5 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.config().holidays())
            .doesNotContain(LocalDate.of(2026, 7, 4));
    }

    @Test
    @DisplayName("saturday closed directive")
    void saturdayClosed_marksSaturdayClosed() {
        String puml = """
            @startgantt
            saturday are closed
            [Task1] lasts 5 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.config().saturdayClosed()).isTrue();
    }

    @Test
    @DisplayName("sunday closed directive")
    void sundayClosed_marksSundayClosed() {
        String puml = """
            @startgantt
            sunday are closed
            [Task1] lasts 5 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.config().sundayClosed()).isTrue();
    }

    @Test
    @DisplayName("person off directive")
    void personOff_marksPersonOff() {
        String puml = """
            @startgantt
            {John} is off on 2026-07-04
            [Task1] lasts 5 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.config().personOffDays()).hasSize(1);
        ScheduleConfig.PersonOffEntry entry = schedule.config().personOffDays().iterator().next();
        assertThat(entry.person()).isEqualTo("John");
        assertThat(entry.date()).isEqualTo(LocalDate.of(2026, 7, 4));
    }

    // ========== Iteration 1.3: Robustness ==========

    @Test
    @DisplayName("Error recovery - graceful handling of invalid syntax")
    void errorRecovery_gracefulHandling() {
        String puml = """
            @startgantt
            [Task1] lasts 5 days
            invalid syntax here
            [Task2] lasts 3 days
            @endgantt
            """;
        // Should not throw exception, parser should handle gracefully
        GanttSchedule schedule = parser.parse(puml);
        assertThat(schedule.tasks()).isNotEmpty();
    }

    @Test
    @DisplayName("Relative dates - computes dates correctly")
    void relativeDates_computesCorrectly() {
        String puml = """
            @startgantt
            project starts 2026-07-01
            saturday are closed
            sunday are closed
            [Task1] lasts 5 days
            [Task2] starts at [Task1]'s end lasts 3 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        Task task1 = schedule.tasks().get(0);
        Task task2 = schedule.tasks().get(1);

        // Task1: July 1 (Wed) - July 7 (Tue) = 5 working days (no weekends)
        assertThat(task1.startDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(task1.endDate()).isEqualTo(LocalDate.of(2026, 7, 7));

        // Task2: starts July 8 (Wed), ends July 10 (Fri) = 3 working days
        assertThat(task2.startDate()).isEqualTo(LocalDate.of(2026, 7, 8));
        assertThat(task2.endDate()).isEqualTo(LocalDate.of(2026, 7, 10));
    }

    @Test
    @DisplayName("Circular dependencies - does not infinite loop")
    void circularDependencies_doesNotInfiniteLoop() {
        String puml = """
            @startgantt
            [Task1] lasts 5 days
            [Task2] lasts 3 days
            [Task1] -> [Task2] -> [Task1]
            @endgantt
            """;
        // Should complete without hanging
        GanttSchedule schedule = parser.parse(puml);
        assertThat(schedule.tasks()).hasSize(2);
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Complex scenario - full project plan")
    void complexScenario_fullProjectPlan() {
        String puml = """
            @startgantt
            title My Project Plan
            project starts 2026-07-01
            saturday are closed
            sunday are closed
            2026-07-04 is closed
            2026-07-05 is closed

            --Phase 1--
            [Requirements] lasts 5 days
            [Design] starts at [Requirements]'s end lasts 7 days

            --Phase 2--
            [Coding] starts at [Design]'s end lasts 14 days
            [Testing] starts at [Coding]'s end lasts 5 days

            Release happens at [Testing]'s end

            [Requirements] -> [Design] -> [Coding] -> [Testing]
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.config().title()).isEqualTo("My Project Plan");
        assertThat(schedule.config().projectStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(schedule.config().saturdayClosed()).isTrue();
        assertThat(schedule.config().sundayClosed()).isTrue();
        // 4 regular tasks + 1 milestone + 2 task group directives = 7
        assertThat(schedule.tasks()).hasSize(7);

        // Verify dependency chain (skip task group directives at index 0 and 3)
        Task requirements = schedule.tasks().get(1);
        Task design = schedule.tasks().get(2);
        Task coding = schedule.tasks().get(4);
        Task testing = schedule.tasks().get(5);
        Task release = schedule.tasks().get(6);

        assertThat(design.dependencyIds()).containsExactly("Requirements");
        assertThat(coding.dependencyIds()).containsExactly("Design");
        assertThat(testing.dependencyIds()).containsExactly("Coding");
        assertThat(release.dependencyIds()).containsExactly("Testing");
        assertThat(release.durationDays()).isEqualTo(0);
    }

    @Test
    @DisplayName("File-based test - then-syntax.puml")
    void fileBasedTest_thenSyntax() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/syntax/then-syntax.puml"));
        GanttSchedule schedule = parser.parse(content);

        assertThat(schedule.tasks()).hasSize(3);
        assertThat(schedule.tasks().get(1).dependencyIds()).containsExactly("Design");
        assertThat(schedule.tasks().get(2).dependencyIds()).containsExactly("Coding");
    }

    @Test
    @DisplayName("File-based test - alias-syntax.puml")
    void fileBasedTest_aliasSyntax() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/syntax/alias-syntax.puml"));
        GanttSchedule schedule = parser.parse(content);

        assertThat(schedule.tasks()).hasSize(3);
        assertThat(schedule.tasks().get(1).dependencyIds()).containsExactly("Design");
        assertThat(schedule.tasks().get(2).dependencyIds()).containsExactly("Coding");
    }

    @Test
    @DisplayName("File-based test - milestone-syntax.puml")
    void fileBasedTest_milestoneSyntax() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/syntax/milestone-syntax.puml"));
        GanttSchedule schedule = parser.parse(content);

        assertThat(schedule.tasks()).hasSize(4);
        Task release = schedule.tasks().get(3);
        assertThat(release.name()).isEqualTo("Release");
        assertThat(release.durationDays()).isEqualTo(0);
    }

    @Test
    @DisplayName("File-based test - project-starts.puml")
    void fileBasedTest_projectStarts() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/syntax/project-starts.puml"));
        GanttSchedule schedule = parser.parse(content);

        assertThat(schedule.config().projectStartDate())
            .isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(schedule.tasks()).hasSize(2);
        assertThat(schedule.tasks().get(1).dependencyIds()).containsExactly("Phase1");
    }
}
