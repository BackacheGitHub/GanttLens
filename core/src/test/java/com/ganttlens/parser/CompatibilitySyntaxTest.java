package com.ganttlens.parser;

import com.ganttlens.model.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PlantUML standard syntax compatibility (previously unsupported).
 * Covers 10 syntax categories that are part of official PlantUML but were missing in GanttLens.
 */
class CompatibilitySyntaxTest {

    private final GanttFileParser parser = new GanttFileParser();

    // ========== 1. !define macro replacement ==========

    @Test
    void defineMacro_replacesMacroInColor() {
        String input = """
            @startgantt
            !define MY_TASK_COLOR Turquoise
            project starts 2026-07-01
            saturday are closed
            sunday are closed
            [Task A] on {Alice} lasts 3 days is colored in MY_TASK_COLOR
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(input);

        Task taskA = schedule.tasks().stream()
            .filter(t -> t.name().equals("Task A")).findFirst().orElseThrow();
        assertThat(taskA.color()).contains("Turquoise");
    }

    // ========== 2. <style> block closedDayColor ==========

    @Test
    void styleBlock_extractsClosedDayColor() {
        String input = """
            @startgantt
            project starts 2026-07-01
            saturday are closed
            sunday are closed
            <style>
            ganttDiagram {
              closed {
                BackgroundColor WhiteSmoke
              }
            }
            </style>
            [Task A] on {Alice} lasts 3 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(input);
        assertThat(schedule.config().closedDayColor()).isEqualTo("WhiteSmoke");
    }

    // ========== 3. @startgantt with title ==========

    @Test
    void startganttWithTitle_titleParsedCorrectly() {
        String input = """
            @startgantt Sample Project
            project starts 2026-07-01
            saturday are closed
            sunday are closed
            [Task A] on {Alice} lasts 2 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(input);
        assertThat(schedule.config().title()).isEqualTo("Sample Project");
    }

    // ========== 4. language directive ==========

    @Test
    void languageDirective_storedInConfig() {
        String input = """
            @startgantt
            language zh
            project starts 2026-07-01
            saturday are closed
            sunday are closed
            [Task A] on {Alice} lasts 2 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(input);
        assertThat(schedule.config().language()).isEqualTo("zh");
    }

    // ========== 5. printscale with zoom ==========

    @Test
    void printscaleWithZoom_parsedCorrectly() {
        String input = """
            @startgantt
            printscale daily zoom 1.5
            project starts 2026-07-01
            saturday are closed
            sunday are closed
            [Task A] on {Alice} lasts 2 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(input);
        assertThat(schedule.config().printscaleZoom()).isEqualTo("1.5");
    }

    // ========== 6. today is / today is colored ==========

    @Test
    void todayDirectives_doNotCauseErrors() {
        String input = """
            @startgantt
            project starts 2026-07-01
            saturday are closed
            sunday are closed
            today is 2026-07-06
            today is colored in Pink
            [Task A] on {Alice} lasts 2 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(input);
        assertThat(schedule.tasks()).isNotEmpty();
    }

    // ========== 7. DATE is colored in COLOR ==========

    @Test
    void dateColorDirective_storedInDateColors() {
        String input = """
            @startgantt
            project starts 2026-07-01
            saturday are closed
            sunday are closed
            2026-08-25 is colored in MistyRose
            [Task A] on {Alice} lasts 2 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(input);
        assertThat(schedule.config().dateColors())
            .containsEntry(LocalDate.of(2026, 8, 25), "MistyRose");
    }

    // ========== 8. starts DATE and ends DATE ==========

    @Test
    void startsAndEnds_datesResolvedCorrectly() {
        String input = """
            @startgantt
            project starts 2026-06-23
            saturday are closed
            sunday are closed
            [Alpha] on {Alice} starts 2026-06-23 and ends 2026-07-03
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(input);

        Task alpha = schedule.tasks().stream()
            .filter(t -> t.name().equals("Alpha")).findFirst().orElseThrow();

        assertThat(alpha.startDate()).isEqualTo(LocalDate.of(2026, 6, 23));
        assertThat(alpha.endDate()).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(alpha.durationDays()).isGreaterThan(0);
    }

    // ========== 9. is X% completed (with d) ==========

    @Test
    void isCompletedWithD_recognizedAsInProgress() {
        String input = """
            @startgantt
            project starts 2026-07-01
            saturday are closed
            sunday are closed
            [Task A] on {Alice} lasts 5 days is 100% completed
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(input);

        Task taskA = schedule.tasks().stream()
            .filter(t -> t.name().equals("Task A")).findFirst().orElseThrow();

        assertThat(taskA.progressPercent()).isEqualTo(100);
        assertThat(taskA.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    // ========== 10. Milestone with bracket name + alias ==========

    @Test
    void milestoneWithBracketNameAndAlias_createdCorrectly() {
        String input = """
            @startgantt
            project starts 2026-07-01
            saturday are closed
            sunday are closed
            [Task A] on {Alice} lasts 3 days
            [My Milestone] as [MS1] happens at [Task A]'s end
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(input);

        Task milestone = schedule.tasks().stream()
            .filter(t -> t.name().equals("My Milestone")).findFirst().orElseThrow();

        assertThat(milestone.durationDays()).isZero();
        assertThat(milestone.dependencyIds()).isNotEmpty();
    }

    // ========== Integration: full extended-syntax.puml ==========

    @Test
    void fullExtendedSyntaxFile_parsesWithoutErrors() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/syntax/compatibility-syntax.puml")) {
            assertThat(is).as("compatibility-syntax.puml must exist on classpath").isNotNull();
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            GanttSchedule schedule = parser.parse(content);

            assertThat(schedule.tasks()).hasSizeGreaterThanOrEqualTo(4);
            assertThat(schedule.config().title()).isEqualTo("Extended Syntax Demo");
            assertThat(schedule.config().language()).isEqualTo("zh");
            assertThat(schedule.config().printscaleZoom()).isEqualTo("1.5");
            assertThat(schedule.config().closedDayColor()).isEqualTo("WhiteSmoke");
            assertThat(schedule.config().dateColors())
                .containsEntry(LocalDate.of(2026, 8, 25), "MistyRose");
        }
    }
}
