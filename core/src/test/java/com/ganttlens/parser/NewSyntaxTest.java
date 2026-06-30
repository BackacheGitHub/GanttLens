package com.ganttlens.parser;

import com.ganttlens.model.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for new PlantUML Gantt syntax features (Iteration 1.1).
 */
class NewSyntaxTest {

    private final GanttFileParser parser = new GanttFileParser();

    @Test
    void thenSyntax_createsDependencyChain() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/then-syntax.puml"));
        GanttSchedule schedule = parser.parse(content);

        assertThat(schedule.tasks()).hasSize(3);

        Task design = schedule.tasks().get(0);
        Task coding = schedule.tasks().get(1);
        Task testing = schedule.tasks().get(2);

        assertThat(design.name()).isEqualTo("Design");
        assertThat(design.dependencyIds()).isEmpty();

        assertThat(coding.name()).isEqualTo("Coding");
        assertThat(coding.dependencyIds()).containsExactly("Design");

        assertThat(testing.name()).isEqualTo("Testing");
        assertThat(testing.dependencyIds()).containsExactly("Coding");
    }

    @Test
    void arrowDependency_createsDependencyChain() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/arrow-deps.puml"));
        GanttSchedule schedule = parser.parse(content);

        assertThat(schedule.tasks()).hasSize(3);

        Task design = schedule.tasks().get(0);
        Task coding = schedule.tasks().get(1);
        Task testing = schedule.tasks().get(2);

        assertThat(design.dependencyIds()).isEmpty();
        assertThat(coding.dependencyIds()).containsExactly("Design");
        assertThat(testing.dependencyIds()).containsExactly("Coding");
    }

    @Test
    void aliasSyntax_resolvesAliasInDependencies() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/alias-syntax.puml"));
        GanttSchedule schedule = parser.parse(content);

        assertThat(schedule.tasks()).hasSize(3);

        Task coding = schedule.tasks().get(1);
        Task testing = schedule.tasks().get(2);

        // Coding depends on Design via alias 'D'
        assertThat(coding.dependencyIds()).containsExactly("Design");
        // Testing depends on Coding via alias 'C'
        assertThat(testing.dependencyIds()).containsExactly("Coding");
    }

    @Test
    void lastsSyntax_parsesDurationCorrectly() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/lasts-syntax.puml"));
        GanttSchedule schedule = parser.parse(content);

        assertThat(schedule.tasks()).hasSize(2);

        Task phase1 = schedule.tasks().get(0);
        Task phase2 = schedule.tasks().get(1);

        // 2 weeks = 10 working days
        assertThat(phase1.durationDays()).isEqualTo(10);
        assertThat(phase2.durationDays()).isEqualTo(10);
    }

    @Test
    void projectStarts_setsDefaultStartDate() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/project-starts.puml"));
        GanttSchedule schedule = parser.parse(content);

        assertThat(schedule.config().projectStartDate())
            .isEqualTo(java.time.LocalDate.of(2026, 7, 1));

        Task phase1 = schedule.tasks().get(0);
        // Phase1 should start on project start date (2026-07-01 is Wednesday)
        assertThat(phase1.startDate())
            .isEqualTo(java.time.LocalDate.of(2026, 7, 1));
    }

    @Test
    void milestone_createsZeroDurationTask() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/milestone-syntax.puml"));
        GanttSchedule schedule = parser.parse(content);

        assertThat(schedule.tasks()).hasSize(4);

        Task release = schedule.tasks().get(3);
        assertThat(release.name()).isEqualTo("Release");
        assertThat(release.durationDays()).isEqualTo(0);
        assertThat(release.dependencyIds()).containsExactly("Testing");
    }

    @Test
    void comments_ignoredDuringParsing() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/comments-syntax.puml"));
        GanttSchedule schedule = parser.parse(content);

        // Should parse 2 tasks, ignoring comments
        assertThat(schedule.tasks()).hasSize(2);
        assertThat(schedule.tasks().get(0).name()).isEqualTo("Design");
        assertThat(schedule.tasks().get(1).name()).isEqualTo("Coding");
    }

    @Test
    void dateRangeClose_addsMultipleHolidays() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/date-range-close.puml"));
        GanttSchedule schedule = parser.parse(content);

        // 2026-07-04 to 2026-07-06 = 3 days closed
        // 2026-07-05 is open = 1 day removed
        // Total: 2 holidays
        assertThat(schedule.config().holidays()).hasSize(2);
        assertThat(schedule.config().holidays())
            .contains(
                java.time.LocalDate.of(2026, 7, 4),
                java.time.LocalDate.of(2026, 7, 6)
            );
        assertThat(schedule.config().holidays())
            .doesNotContain(java.time.LocalDate.of(2026, 7, 5));
    }

    @Test
    void startsAbsoluteDate_setsStartDate() throws IOException {
        String puml = """
            @startgantt
            [Task1] starts 2026-07-15 requires 5 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.tasks()).hasSize(1);
        Task task = schedule.tasks().get(0);
        assertThat(task.startDate()).isEqualTo(java.time.LocalDate.of(2026, 7, 15));
    }
}
