package com.ganttlens.cli;

import com.ganttlens.analyzer.AnalysisEngine;
import com.ganttlens.model.*;
import com.ganttlens.parser.GanttFileParser;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

/**
 * Subcommand for analyzing PlantUML Gantt chart files.
 */
@CommandLine.Command(
    name = "analyze",
    description = "Analyze a PlantUML Gantt chart file and produce a workload report",
    mixinStandardHelpOptions = true
)
public class AnalyzeCommand implements Callable<Integer> {

    @CommandLine.Parameters(
        index = "0",
        description = "Path to the PlantUML Gantt file (.puml)",
        arity = "0..1"
    )
    private String filePath;

    @CommandLine.Option(
        names = {"-o", "--output"},
        description = "Path to output file (default: stdout)"
    )
    private String outputPath;

    @Override
    public Integer call() {
        try {
            Path inputFile = Path.of(filePath);
            if (!Files.exists(inputFile)) {
                System.err.println("Error: File not found: " + inputFile);
                return 1;
            }

            // Parse
            GanttFileParser parser = new GanttFileParser();
            GanttSchedule schedule = parser.parseFile(inputFile);

            // Analyze
            AnalysisEngine engine = new AnalysisEngine();
            ProjectStats stats = engine.analyze(schedule);

            // Format output
            String report = formatReport(inputFile, schedule, stats);

            // Write output
            if (outputPath != null) {
                Files.writeString(Path.of(outputPath), report);
                System.out.println("Report written to: " + outputPath);
            } else {
                System.out.print(report);
            }

            return 0;
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }

    private String formatReport(Path inputFile, GanttSchedule schedule, ProjectStats stats) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("=".repeat(60)).append("\n");
        sb.append("  GanttLens Analysis Report\n");
        sb.append("=".repeat(60)).append("\n\n");

        // Project overview
        sb.append("--- Project Overview ---\n");
        if (!schedule.config().title().isEmpty()) {
            sb.append("  Title:       ").append(schedule.config().title()).append("\n");
        }
        sb.append("  File:        ").append(inputFile.getFileName()).append("\n");

        List<Task> tasks = schedule.tasks();
        sb.append("  Total Tasks: ").append(tasks.size()).append("\n");

        // Date range
        LocalDate earliest = tasks.stream()
            .map(Task::startDate)
            .filter(d -> d != null)
            .min(LocalDate::compareTo)
            .orElse(null);
        LocalDate latest = tasks.stream()
            .map(Task::endDate)
            .filter(d -> d != null)
            .max(LocalDate::compareTo)
            .orElse(null);

        if (earliest != null && latest != null) {
            sb.append("  Start Date:  ").append(earliest).append("\n");
            sb.append("  End Date:    ").append(latest).append("\n");
        }

        // Working calendar
        sb.append("  Calendar:    ");
        if (schedule.config().saturdayClosed()) sb.append("Sat ");
        if (schedule.config().sundayClosed()) sb.append("Sun ");
        if (schedule.config().holidays().isEmpty() && !schedule.config().saturdayClosed() && !schedule.config().sundayClosed()) {
            sb.append("All days");
        }
        sb.append("\n");
        if (!schedule.config().holidays().isEmpty()) {
            sb.append("  Holidays:    ").append(schedule.config().holidays().size()).append(" day(s)\n");
        }
        sb.append("\n");

        // Workload summary
        sb.append("--- Workload Summary ---\n");
        sb.append(String.format("  Total Man-Days: %.1f\n", stats.totalManDays()));
        sb.append("\n");

        // Person workload (sorted by name)
        sb.append("  Person         Man-Days\n");
        sb.append("  ─────────────  ────────\n");
        TreeMap<String, Double> sortedLoad = new TreeMap<>(stats.personManDays());
        for (Map.Entry<String, Double> entry : sortedLoad.entrySet()) {
            sb.append(String.format("  %-14s %6.1f\n", entry.getKey(), entry.getValue()));
        }
        sb.append("\n");

        // Overload warnings
        List<OverloadRecord> overloads = stats.overloads();
        if (!overloads.isEmpty()) {
            sb.append("--- Overload Warnings ---\n");
            for (OverloadRecord record : overloads) {
                sb.append(String.format("  [!] %s on %s: %.0f%% load\n",
                    record.person(),
                    record.date(),
                    record.totalLoad() * 100));
                sb.append("      Tasks: ");
                for (int i = 0; i < record.tasks().size(); i++) {
                    if (i > 0) sb.append(", ");
                    PersonDailyLoad.TaskLoad tl = record.tasks().get(i);
                    sb.append(tl.taskName()).append(" (").append(String.format("%.0f%%", tl.ratio() * 100)).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        } else {
            sb.append("--- No Overload Warnings ---\n\n");
        }

        // Task list
        sb.append("--- Task List ---\n");
        sb.append(String.format("  %-4s %-14s %-12s %-10s %-10s %s\n",
            "No.", "Name", "People", "Start", "End", "Days"));
        sb.append("  ──── ────────────── ──────────── ────────── ────────── ────\n");
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            StringBuilder people = new StringBuilder();
            for (int j = 0; j < t.assignments().size(); j++) {
                if (j > 0) people.append(",");
                Assignment a = t.assignments().get(j);
                people.append(a.person());
                if (a.ratio() < 1.0) {
                    people.append(String.format("(%.0f%%)", a.ratio() * 100));
                }
            }

            String startStr = t.startDate() != null ? t.startDate().toString() : "TBD";
            String endStr = t.endDate() != null ? t.endDate().toString() : "TBD";

            sb.append(String.format("  %-4d %-14s %-12s %-10s %-10s %d\n",
                i + 1,
                t.name(),
                people.toString(),
                startStr,
                endStr,
                t.durationDays()));
        }

        sb.append("\n");
        sb.append("=".repeat(60)).append("\n");

        return sb.toString();
    }
}
