package com.ganttlens.cli;

import picocli.CommandLine;

/**
 * GanttLens CLI entry point.
 */
@CommandLine.Command(
    name = "ganttlens",
    description = "PlantUML Gantt chart analysis and insight tool",
    version = "GanttLens 1.0.0-SNAPSHOT",
    mixinStandardHelpOptions = true,
    subcommands = { AnalyzeCommand.class, ExportCommand.class }
)
public class CliApp implements Runnable {

    @Override
    public void run() {
        System.out.println("GanttLens - PlantUML Gantt Chart Analyzer");
        System.out.println("Usage: ganttlens <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  analyze   Analyze a .puml file and show workload report");
        System.out.println("  export    Export analysis to Excel (.xlsx) format");
        System.out.println();
        System.out.println("Try 'ganttlens <command> --help' for more information.");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CliApp()).execute(args);
        System.exit(exitCode);
    }
}
