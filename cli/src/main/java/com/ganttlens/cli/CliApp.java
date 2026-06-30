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
    subcommands = { AnalyzeCommand.class }
)
public class CliApp implements Runnable {

    @Override
    public void run() {
        System.out.println("GanttLens - PlantUML Gantt Chart Analyzer");
        System.out.println("Usage: ganttlens analyze -f <file.puml>");
        System.out.println("Try 'ganttlens analyze --help' for more information.");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CliApp()).execute(args);
        System.exit(exitCode);
    }
}
