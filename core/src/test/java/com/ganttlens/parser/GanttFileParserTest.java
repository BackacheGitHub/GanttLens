package com.ganttlens.parser;

import com.ganttlens.model.GanttSchedule;
import com.ganttlens.model.Task;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class GanttFileParserTest {

    private final GanttFileParser parser = new GanttFileParser();

    @Test
    void parseSimpleSchedule() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/syntax/simple.puml"));
        GanttSchedule schedule = parser.parse(content);

        assertThat(schedule.tasks()).hasSize(3);
        assertThat(schedule.config().title()).isEmpty();
        assertThat(schedule.config().saturdayClosed()).isTrue();
        assertThat(schedule.config().sundayClosed()).isTrue();
    }

    @Test
    void parseTaskNames() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/syntax/simple.puml"));
        GanttSchedule schedule = parser.parse(content);

        assertThat(schedule.tasks().get(0).name()).isEqualTo("需求分析");
        assertThat(schedule.tasks().get(1).name()).isEqualTo("接口开发");
        assertThat(schedule.tasks().get(2).name()).isEqualTo("联调测试");
    }

    @Test
    void parseAssignments() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/syntax/simple.puml"));
        GanttSchedule schedule = parser.parse(content);

        Task firstTask = schedule.tasks().get(0);
        assertThat(firstTask.assignments()).hasSize(1);
        assertThat(firstTask.assignments().get(0).person()).isEqualTo("张三");
        assertThat(firstTask.assignments().get(0).ratio()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void parseDuration() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/syntax/simple.puml"));
        GanttSchedule schedule = parser.parse(content);

        assertThat(schedule.tasks().get(0).durationDays()).isEqualTo(4);
        assertThat(schedule.tasks().get(1).durationDays()).isEqualTo(6);
        assertThat(schedule.tasks().get(2).durationDays()).isEqualTo(3);
    }

    @Test
    void parseDependencies() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/syntax/simple.puml"));
        GanttSchedule schedule = parser.parse(content);

        Task secondTask = schedule.tasks().get(1);
        assertThat(secondTask.dependencyIds()).containsExactly("需求分析");
    }

    @Test
    void dateRangeClose_addsMultipleHolidays() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/syntax/date-range-close.puml"));
        GanttSchedule schedule = parser.parse(content);

        // 2026-07-04 to 2026-07-06 = 3 days closed
        // 2026-07-05 is open = 1 day removed
        // Total: 2 holidays
        assertThat(schedule.config().holidays()).hasSize(2);
        assertThat(schedule.config().holidays())
            .contains(
                LocalDate.of(2026, 7, 4),
                LocalDate.of(2026, 7, 6)
            );
        assertThat(schedule.config().holidays())
            .doesNotContain(LocalDate.of(2026, 7, 5));
    }

    @Test
    void startsAbsoluteDate_setsStartDate() {
        String puml = """
            @startgantt
            [Task1] starts 2026-07-15 requires 5 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);

        assertThat(schedule.tasks()).hasSize(1);
        Task task = schedule.tasks().get(0);
        assertThat(task.startDate()).isEqualTo(LocalDate.of(2026, 7, 15));
    }
}
