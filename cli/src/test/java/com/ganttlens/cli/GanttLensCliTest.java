package com.ganttlens.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GanttLensCliTest {

    @TempDir
    Path tempDir;

    // ========== AnalyzeCommand Tests ==========

    @Test
    void analyze_validFile_returnsZero() {
        String[] args = {"analyze", "src/test/resources/sample.puml"};
        int exitCode = new CommandLine(new CliApp()).execute(args);
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void analyze_validFile_outputsReport() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(baos));
        try {
            String[] args = {"analyze", "src/test/resources/sample.puml"};
            new CommandLine(new CliApp()).execute(args);
        } finally {
            System.setOut(original);
        }

        String output = baos.toString();
        assertThat(output).contains("GanttLens Analysis Report");
        assertThat(output).contains("Sample Project");
        assertThat(output).contains("Design");
        assertThat(output).contains("Coding");
        assertThat(output).contains("Testing");
    }

    @Test
    void analyze_nonExistentFile_returnsOne() {
        String[] args = {"analyze", "nonexistent.puml"};
        int exitCode = new CommandLine(new CliApp()).execute(args);
        assertThat(exitCode).isEqualTo(1);
    }

    @Test
    void analyze_outputToFile_createsFile() throws Exception {
        Path outputFile = tempDir.resolve("report.txt");
        String[] args = {"analyze", "src/test/resources/sample.puml", "-o", outputFile.toString()};
        int exitCode = new CommandLine(new CliApp()).execute(args);

        assertThat(exitCode).isEqualTo(0);
        assertThat(Files.exists(outputFile)).isTrue();
        String content = Files.readString(outputFile);
        assertThat(content).contains("GanttLens Analysis Report");
    }

    // ========== ExportCommand Tests ==========

    @Test
    void export_validFile_returnsZero() {
        Path outputFile = tempDir.resolve("output.xlsx");
        String[] args = {"export", "src/test/resources/sample.puml", "-o", outputFile.toString()};
        int exitCode = new CommandLine(new CliApp()).execute(args);
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void export_validFile_createsExcelFile() throws Exception {
        Path outputFile = tempDir.resolve("output.xlsx");
        String[] args = {"export", "src/test/resources/sample.puml", "-o", outputFile.toString()};
        new CommandLine(new CliApp()).execute(args);

        assertThat(Files.exists(outputFile)).isTrue();
        assertThat(Files.size(outputFile)).isGreaterThan(0);
    }

    @Test
    void export_nonExistentFile_returnsOne() {
        String[] args = {"export", "nonexistent.puml"};
        int exitCode = new CommandLine(new CliApp()).execute(args);
        assertThat(exitCode).isEqualTo(1);
    }

    @Test
    void export_defaultOutputName() {
        // When no -o is specified, output should be <input-name>.xlsx
        // We run from the CLI module directory so the .puml path is relative
        String[] args = {"export", "src/test/resources/sample.puml"};
        int exitCode = new CommandLine(new CliApp()).execute(args);
        assertThat(exitCode).isEqualTo(0);

        // Clean up the generated file
        Path expected = Path.of("src/test/resources/sample.xlsx");
        if (Files.exists(expected)) {
            try { Files.delete(expected); } catch (Exception ignored) {}
        }
    }

    // ========== CliApp Main Tests ==========

    @Test
    void noArgs_showsUsage() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(baos));
        try {
            new CommandLine(new CliApp()).execute();
        } finally {
            System.setOut(original);
        }

        String output = baos.toString();
        assertThat(output).contains("GanttLens");
        assertThat(output).contains("analyze");
        assertThat(output).contains("export");
    }

    @Test
    void helpOption_showsHelp() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(baos));
        try {
            new CommandLine(new CliApp()).execute("--help");
        } finally {
            System.setOut(original);
        }

        String output = baos.toString();
        assertThat(output).contains("Usage:");
        assertThat(output).contains("analyze");
        assertThat(output).contains("export");
    }
}
