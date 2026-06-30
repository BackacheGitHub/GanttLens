package com.ganttlens.export;

import com.ganttlens.analyzer.AnalysisEngine;
import com.ganttlens.model.*;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelGanttExporterTest {

    private final ExcelGanttExporter exporter = new ExcelGanttExporter();

    @TempDir
    Path tempDir;

    @Test
    void export_producesValidWorkbook() throws IOException {
        GanttSchedule schedule = buildSampleSchedule();
        ProjectStats stats = new AnalysisEngine().analyze(schedule);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.export(schedule, stats, out);

        assertThat(out.size()).isGreaterThan(0);

        // Verify the output is a valid XLSX by re-reading it
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(4);
        }
    }

    @Test
    void export_createsExpectedSheetNames() throws IOException {
        GanttSchedule schedule = buildSampleSchedule();
        ProjectStats stats = new AnalysisEngine().analyze(schedule);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.export(schedule, stats, out);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Gantt Chart");
            assertThat(wb.getSheetAt(1).getSheetName()).isEqualTo("Workload Heatmap");
            assertThat(wb.getSheetAt(2).getSheetName()).isEqualTo("Summary");
            assertThat(wb.getSheetAt(3).getSheetName()).isEqualTo("Task Details");
        }
    }

    @Test
    void export_ganttSheetHasCorrectRowCount() throws IOException {
        GanttSchedule schedule = buildSampleSchedule();
        ProjectStats stats = new AnalysisEngine().analyze(schedule);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.export(schedule, stats, out);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            // Gantt Chart sheet: 2 header rows + 3 task rows
            assertThat(wb.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(5);
        }
    }

    @Test
    void export_taskDetailSheetHasCorrectData() throws IOException {
        GanttSchedule schedule = buildSampleSchedule();
        ProjectStats stats = new AnalysisEngine().analyze(schedule);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.export(schedule, stats, out);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            // Task Details sheet: 1 header row + 3 task rows
            assertThat(wb.getSheetAt(3).getPhysicalNumberOfRows()).isEqualTo(4);

            // Verify first task name
            String taskName = wb.getSheetAt(3).getRow(1).getCell(1).getStringCellValue();
            assertThat(taskName).isEqualTo("设计");
        }
    }

    @Test
    void export_summarySheetHasTaskCount() throws IOException {
        GanttSchedule schedule = buildSampleSchedule();
        ProjectStats stats = new AnalysisEngine().analyze(schedule);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.export(schedule, stats, out);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            var summarySheet = wb.getSheetAt(2);
            // Find "Total Tasks:" cell
            boolean found = false;
            for (int r = 0; r <= summarySheet.getLastRowNum(); r++) {
                var row = summarySheet.getRow(r);
                if (row != null && row.getCell(0) != null &&
                    "Total Tasks:".equals(row.getCell(0).getStringCellValue())) {
                    assertThat(row.getCell(1).getStringCellValue()).isEqualTo("3");
                    found = true;
                    break;
                }
            }
            assertThat(found).isTrue();
        }
    }

    @Test
    void export_emptySchedule_doesNotCrash() throws IOException {
        GanttSchedule schedule = new GanttSchedule(
            new ScheduleConfig("", null, false, false, Set.of(), Set.of()),
            List.of()
        );
        ProjectStats stats = new AnalysisEngine().analyze(schedule);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.export(schedule, stats, out);

        // Should produce a valid (but small) workbook
        assertThat(out.size()).isGreaterThan(0);
    }

    @Test
    void export_workloadHeatmapHasPersonRows() throws IOException {
        GanttSchedule schedule = buildSampleSchedule();
        ProjectStats stats = new AnalysisEngine().analyze(schedule);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.export(schedule, stats, out);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            // Heatmap sheet: 1 header row + 2 person rows
            assertThat(wb.getSheetAt(1).getPhysicalNumberOfRows()).isEqualTo(3);

            // First person name
            String person = wb.getSheetAt(1).getRow(1).getCell(0).getStringCellValue();
            assertThat(person).isIn("张三", "李四");
        }
    }

    // ========== Helper ==========

    private GanttSchedule buildSampleSchedule() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end2 = LocalDate.of(2026, 7, 3);

        Task task1 = new Task(
            "task-1", "设计", null,
            start, start.plusDays(1),
            2,
            List.of(Assignment.fullLoad("张三")),
            List.of(),
            TaskStatus.PENDING
        );
        Task task2 = new Task(
            "task-2", "开发", null,
            start.plusDays(2), start.plusDays(4),
            3,
            List.of(Assignment.fullLoad("张三"), Assignment.withPercent("李四", 50)),
            List.of("设计"),
            TaskStatus.PENDING
        );
        Task task3 = new Task(
            "task-3", "测试", null,
            start.plusDays(5), start.plusDays(6),
            2,
            List.of(Assignment.fullLoad("李四")),
            List.of("开发"),
            TaskStatus.PENDING
        );

        return new GanttSchedule(
            new ScheduleConfig("测试项目", null, true, true, Set.of(), Set.of()),
            List.of(task1, task2, task3)
        );
    }
}
