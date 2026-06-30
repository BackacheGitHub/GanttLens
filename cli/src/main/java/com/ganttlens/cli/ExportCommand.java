package com.ganttlens.cli;

import com.ganttlens.analyzer.AnalysisEngine;
import com.ganttlens.export.ExcelGanttExporter;
import com.ganttlens.model.GanttSchedule;
import com.ganttlens.model.ProjectStats;
import com.ganttlens.parser.GanttFileParser;
import picocli.CommandLine;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Subcommand for exporting Gantt chart analysis to Excel format.
 */
@CommandLine.Command(
    name = "export",
    description = "Export Gantt chart analysis to Excel (.xlsx) format",
    mixinStandardHelpOptions = true
)
public class ExportCommand implements Callable<Integer> {

    @CommandLine.Parameters(
        index = "0",
        description = "Path to the PlantUML Gantt file (.puml)",
        arity = "0..1"
    )
    private String filePath;

    @CommandLine.Option(
        names = {"-o", "--output"},
        description = "Path to output Excel file (default: <input-name>.xlsx)",
        required = false
    )
    private String outputPath;

    @CommandLine.Option(
        names = {"-f", "--format"},
        description = "Output format: xlsx (default)",
        defaultValue = "xlsx"
    )
    private String format;

    @Override
    public Integer call() {
        try {
            Path inputFile = Path.of(filePath);
            if (!Files.exists(inputFile)) {
                System.err.println("Error: File not found: " + inputFile);
                return 1;
            }

            // Determine output path
            Path outputFile;
            if (outputPath != null) {
                outputFile = Path.of(outputPath);
            } else {
                String baseName = inputFile.getFileName().toString();
                if (baseName.endsWith(".puml")) {
                    baseName = baseName.substring(0, baseName.length() - 5);
                }
                outputFile = inputFile.getParent().resolve(baseName + ".xlsx");
            }

            // Parse
            System.out.println("Parsing: " + inputFile);
            GanttFileParser parser = new GanttFileParser();
            GanttSchedule schedule = parser.parseFile(inputFile);

            // Analyze
            AnalysisEngine engine = new AnalysisEngine();
            ProjectStats stats = engine.analyze(schedule);

            // Export to Excel
            System.out.println("Exporting to: " + outputFile);
            ExcelGanttExporter exporter = new ExcelGanttExporter();
            try (FileOutputStream fos = new FileOutputStream(outputFile.toFile())) {
                exporter.export(schedule, stats, fos);
            }

            System.out.println("Done! File created: " + outputFile);
            System.out.printf("  - %d tasks, %.1f man-days%n", schedule.tasks().size(), stats.totalManDays());
            if (!stats.overloads().isEmpty()) {
                System.out.printf("  - %d overload warning(s)%n", stats.overloads().size());
            }

            return 0;
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return 2;
        }
    }
}
