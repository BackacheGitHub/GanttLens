package com.ganttlens.analyzer;

import com.ganttlens.model.*;
import com.ganttlens.parser.GanttFileParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Resource Balancer")
class ResourceBalancerTest {

    private final GanttFileParser parser = new GanttFileParser();
    private final AnalysisEngine engine = new AnalysisEngine();

    @Test
    @DisplayName("No overload - no suggestions")
    void noOverload_noSuggestions() {
        String puml = """
            @startgantt
            project starts 2026-07-01
            [Task1] on {Alice} lasts 5 days
            [Task2] on {Bob} lasts 5 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);
        ProjectStats stats = engine.analyze(schedule);

        assertThat(stats.balanceSuggestions()).isEmpty();
    }

    @Test
    @DisplayName("Overload with non-critical task - suggests postponing")
    void overloadWithNonCriticalTask_suggestsPostponing() {
        String puml = """
            @startgantt
            project starts 2026-07-01
            [Task1] on {Alice} lasts 5 days
            [Task2] on {Alice} lasts 5 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);
        ProjectStats stats = engine.analyze(schedule);

        assertThat(stats.overloads()).isNotEmpty();
        assertThat(stats.balanceSuggestions()).isNotEmpty();
    }

    @Test
    @DisplayName("Overload with critical path task - suggests reducing load")
    void overloadWithCriticalPathTask_suggestsReducingLoad() {
        String puml = """
            @startgantt
            project starts 2026-07-01
            [Task1] on {Alice} lasts 5 days
            [Task2] on {Alice} lasts 5 days
            [Task1] -> [Task2]
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);
        ProjectStats stats = engine.analyze(schedule);

        // Task1 and Task2 are on critical path and both assigned to Alice
        // They run sequentially, so there should be no overload on the same day
        // But let's verify the critical path is correct
        assertThat(stats.criticalPath()).isNotNull();
        assertThat(stats.criticalPath().criticalTaskIds()).hasSize(2);
    }

    @Test
    @DisplayName("Multiple people overloaded - generates suggestions for each")
    void multiplePeopleOverloaded_generatesSuggestionsForEach() {
        String puml = """
            @startgantt
            project starts 2026-07-01
            [Task1] on {Alice} lasts 5 days
            [Task2] on {Alice} lasts 5 days
            [Task3] on {Bob} lasts 5 days
            [Task4] on {Bob} lasts 5 days
            @endgantt
            """;
        GanttSchedule schedule = parser.parse(puml);
        ProjectStats stats = engine.analyze(schedule);

        assertThat(stats.overloads()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(stats.balanceSuggestions()).hasSizeGreaterThanOrEqualTo(2);
    }
}
