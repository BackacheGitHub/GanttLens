package com.ganttlens.export;

import com.ganttlens.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports Gantt schedule and analysis results to Excel format.
 * Creates a multi-sheet workbook with:
 * - Gantt chart visualization (colored cells as timeline bars)
 * - Workload heatmap per person
 * - Summary statistics
 * - Task details
 */
public class ExcelGanttExporter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter FULL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Color palette for tasks (ARGB)
    private static final int[] TASK_COLORS = {
        0xFF4472C4, // Blue
        0xFFED7D31, // Orange
        0xFFA5A5A5, // Gray
        0xFFFFC000, // Yellow
        0xFF5B9BD5, // Light Blue
        0xFF70AD47, // Green
        0xFF264478, // Dark Blue
        0xFF9B59B6, // Purple
    };

    // Overload warning colors
    private static final int COLOR_OVERLOAD_HIGH = 0xFFFF4444;  // Red
    private static final int COLOR_OVERLOAD_MED  = 0xFFFF8C00;  // Dark Orange
    private static final int COLOR_NORMAL        = 0xFF92D050;  // Green

    /**
     * Exports the schedule and stats to the given output stream.
     */
    public void export(GanttSchedule schedule, ProjectStats stats, OutputStream out) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            createGanttSheet(workbook, schedule);
            createWorkloadHeatmapSheet(workbook, schedule, stats);
            createSummarySheet(workbook, schedule, stats);
            createTaskDetailSheet(workbook, schedule);

            // Move Gantt sheet to first position
            workbook.setSheetOrder("Gantt Chart", 0);

            workbook.write(out);
        }
    }

    // ========== Sheet 1: Gantt Chart ==========

    private void createGanttSheet(Workbook wb, GanttSchedule schedule) {
        Sheet sheet = wb.createSheet("Gantt Chart");
        List<Task> tasks = schedule.tasks();

        if (tasks.isEmpty()) return;

        // Calculate date range
        LocalDate[] range = getDateRange(tasks);
        LocalDate startDate = range[0];
        LocalDate endDate = range[1];

        int totalDays = (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;

        // Build person-to-color mapping
        Map<String, Integer> personColorMap = buildPersonColorMap(tasks);

        // --- Header row: task info columns + date columns ---
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(wb);
        setCell(headerRow, 0, "No.", headerStyle);
        setCell(headerRow, 1, "Task Name", headerStyle);
        setCell(headerRow, 2, "People", headerStyle);
        setCell(headerRow, 3, "Days", headerStyle);
        sheet.setColumnWidth(0, 1200);
        sheet.setColumnWidth(1, 6000);
        sheet.setColumnWidth(2, 6000);
        sheet.setColumnWidth(3, 1800);

        // Date headers
        for (int d = 0; d < totalDays; d++) {
            LocalDate date = startDate.plusDays(d);
            int col = 4 + d;
            Cell cell = headerRow.createCell(col);
            cell.setCellValue(date.format(DATE_FMT));
            cell.setCellStyle(headerStyle);

            // Day-of-week header in next row
            Row dowRow = sheet.getRow(1) != null ? sheet.getRow(1) : sheet.createRow(1);
            Cell dowCell = dowRow.createCell(col);
            dowCell.setCellValue(date.getDayOfWeek().toString().substring(0, 2));
            CellStyle dowStyle = createDayOfWeekStyle(wb, date);
            dowCell.setCellStyle(dowStyle);

            sheet.setColumnWidth(col, 1400);
        }

        // --- Task rows ---
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            Row row = sheet.createRow(i + 2);

            // Task info
            setCell(row, 0, String.valueOf(i + 1), null);
            setCell(row, 1, task.name(), null);

            String people = task.assignments().stream()
                .map(a -> {
                    if (a.ratio() < 1.0) {
                        return a.person() + "(" + (int)(a.ratio() * 100) + "%)";
                    }
                    return a.person();
                })
                .collect(Collectors.joining(", "));
            setCell(row, 2, people, null);
            setCell(row, 3, String.valueOf(task.durationDays()), null);

            // Gantt bar: color cells for task duration
            if (task.startDate() != null && task.endDate() != null) {
                int barColor = getTaskColor(task, personColorMap);
                CellStyle barStyle = createBarStyle(wb, barColor);

                for (LocalDate d = task.startDate(); !d.isAfter(task.endDate()); d = d.plusDays(1)) {
                    if (d.isBefore(startDate)) continue;
                    if (d.isAfter(endDate)) break;

                    int col = 4 + (int) (d.toEpochDay() - startDate.toEpochDay());
                    Cell cell = row.createCell(col);
                    cell.setCellStyle(barStyle);
                }
            }
        }

        // Freeze panes: freeze first 4 columns and 2 header rows
        sheet.createFreezePane(4, 2);
    }

    // ========== Sheet 2: Workload Heatmap ==========

    private void createWorkloadHeatmapSheet(Workbook wb, GanttSchedule schedule, ProjectStats stats) {
        Sheet sheet = wb.createSheet("Workload Heatmap");
        List<Task> tasks = schedule.tasks();

        if (tasks.isEmpty()) return;

        LocalDate[] range = getDateRange(tasks);
        LocalDate startDate = range[0];
        LocalDate endDate = range[1];
        int totalDays = (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;

        // Collect all persons
        Set<String> allPersons = new LinkedHashSet<>();
        for (Task t : tasks) {
            for (Assignment a : t.assignments()) {
                allPersons.add(a.person());
            }
        }
        List<String> persons = new ArrayList<>(allPersons);

        // Build daily load map: person -> date -> load
        Map<String, Map<LocalDate, Double>> loadMap = new HashMap<>();
        for (PersonDailyLoad pdl : stats.dailyLoads()) {
            loadMap.computeIfAbsent(pdl.person(), k -> new HashMap<>())
                   .put(pdl.date(), pdl.totalLoad());
        }

        // Header row
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(wb);
        setCell(headerRow, 0, "Person", headerStyle);
        sheet.setColumnWidth(0, 4000);

        for (int d = 0; d < totalDays; d++) {
            LocalDate date = startDate.plusDays(d);
            int col = 1 + d;
            Cell cell = headerRow.createCell(col);
            cell.setCellValue(date.format(DATE_FMT));
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(col, 1600);
        }

        // Person rows with heatmap coloring
        for (int p = 0; p < persons.size(); p++) {
            String person = persons.get(p);
            Row row = sheet.createRow(p + 1);
            setCell(row, 0, person, null);

            Map<LocalDate, Double> personLoads = loadMap.getOrDefault(person, Collections.emptyMap());

            for (int d = 0; d < totalDays; d++) {
                LocalDate date = startDate.plusDays(d);
                double load = personLoads.getOrDefault(date, 0.0);

                int col = 1 + d;
                Cell cell = row.createCell(col);

                if (load > 0) {
                    cell.setCellValue(String.format("%.0f%%", load * 100));
                    CellStyle heatStyle = createHeatmapStyle(wb, load);
                    cell.setCellStyle(heatStyle);
                }
            }
        }

        sheet.createFreezePane(1, 1);
    }

    // ========== Sheet 3: Summary ==========

    private void createSummarySheet(Workbook wb, GanttSchedule schedule, ProjectStats stats) {
        Sheet sheet = wb.createSheet("Summary");
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle titleStyle = createTitleStyle(wb);

        int rowIdx = 0;

        // Project info
        Row titleRow = sheet.createRow(rowIdx++);
        setCell(titleRow, 0, "Project Summary", titleStyle);
        rowIdx++;

        ScheduleConfig config = schedule.config();
        if (!config.title().isEmpty()) {
            Row r = sheet.createRow(rowIdx++);
            setCell(r, 0, "Title:", null);
            setCell(r, 1, config.title(), null);
        }

        Row fileRow = sheet.createRow(rowIdx++);
        setCell(fileRow, 0, "Total Tasks:", null);
        setCell(fileRow, 1, String.valueOf(schedule.tasks().size()), null);

        Row mdRow = sheet.createRow(rowIdx++);
        setCell(mdRow, 0, "Total Man-Days:", null);
        setCell(mdRow, 1, String.format("%.1f", stats.totalManDays()), null);

        // Calendar info
        rowIdx++;
        Row calRow = sheet.createRow(rowIdx++);
        setCell(calRow, 0, "Calendar:", null);
        StringBuilder cal = new StringBuilder();
        if (config.saturdayClosed()) cal.append("Sat ");
        if (config.sundayClosed()) cal.append("Sun");
        if (cal.isEmpty()) cal.append("All days");
        setCell(calRow, 1, cal.toString(), null);

        // Person workload table
        rowIdx++;
        Row personHeader = sheet.createRow(rowIdx++);
        setCell(personHeader, 0, "Person", headerStyle);
        setCell(personHeader, 1, "Man-Days", headerStyle);
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 3500);

        TreeMap<String, Double> sorted = new TreeMap<>(stats.personManDays());
        for (Map.Entry<String, Double> entry : sorted.entrySet()) {
            Row r = sheet.createRow(rowIdx++);
            setCell(r, 0, entry.getKey(), null);
            setCell(r, 1, String.format("%.1f", entry.getValue()), null);
        }

        // Overload warnings
        if (!stats.overloads().isEmpty()) {
            rowIdx++;
            Row olTitle = sheet.createRow(rowIdx++);
            setCell(olTitle, 0, "Overload Warnings", titleStyle);

            Row olHeader = sheet.createRow(rowIdx++);
            setCell(olHeader, 0, "Person", headerStyle);
            setCell(olHeader, 1, "Date", headerStyle);
            setCell(olHeader, 2, "Load", headerStyle);
            setCell(olHeader, 3, "Tasks", headerStyle);
            sheet.setColumnWidth(2, 2500);
            sheet.setColumnWidth(3, 10000);

            for (OverloadRecord rec : stats.overloads()) {
                Row r = sheet.createRow(rowIdx++);
                setCell(r, 0, rec.person(), null);
                setCell(r, 1, rec.date().format(FULL_DATE_FMT), null);
                setCell(r, 2, String.format("%.0f%%", rec.totalLoad() * 100), null);
                String taskDesc = rec.tasks().stream()
                    .map(t -> t.taskName() + " " + (int)(t.ratio() * 100) + "%")
                    .collect(Collectors.joining(" + "));
                setCell(r, 3, taskDesc, null);
            }
        }
    }

    // ========== Sheet 4: Task Details ==========

    private void createTaskDetailSheet(Workbook wb, GanttSchedule schedule) {
        Sheet sheet = wb.createSheet("Task Details");
        CellStyle headerStyle = createHeaderStyle(wb);

        Row header = sheet.createRow(0);
        String[] columns = {"No.", "Task Name", "Group", "People", "Start", "End", "Duration (days)", "Dependencies"};
        for (int i = 0; i < columns.length; i++) {
            setCell(header, i, columns[i], headerStyle);
        }
        sheet.setColumnWidth(0, 1200);
        sheet.setColumnWidth(1, 6000);
        sheet.setColumnWidth(2, 4000);
        sheet.setColumnWidth(3, 6000);
        sheet.setColumnWidth(4, 3500);
        sheet.setColumnWidth(5, 3500);
        sheet.setColumnWidth(6, 3500);
        sheet.setColumnWidth(7, 6000);

        List<Task> tasks = schedule.tasks();
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            Row row = sheet.createRow(i + 1);
            setCell(row, 0, String.valueOf(i + 1), null);
            setCell(row, 1, t.name(), null);
            setCell(row, 2, t.group() != null ? t.group() : "", null);

            String people = t.assignments().stream()
                .map(a -> a.person() + "(" + (int)(a.ratio() * 100) + "%)")
                .collect(Collectors.joining(", "));
            setCell(row, 3, people, null);
            setCell(row, 4, t.startDate() != null ? t.startDate().format(FULL_DATE_FMT) : "TBD", null);
            setCell(row, 5, t.endDate() != null ? t.endDate().format(FULL_DATE_FMT) : "TBD", null);
            setCell(row, 6, String.valueOf(t.durationDays()), null);
            setCell(row, 7, String.join(", ", t.dependencyIds()), null);
        }
    }

    // ========== Style Helpers ==========

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle createDayOfWeekStyle(Workbook wb, LocalDate date) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 9);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);

        int dow = date.getDayOfWeek().getValue();
        if (dow == 6 || dow == 7) {
            style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return style;
    }

    private CellStyle createBarStyle(Workbook wb, int colorArgb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(
            new byte[]{(byte) ((colorArgb >> 16) & 0xFF),
                       (byte) ((colorArgb >> 8) & 0xFF),
                       (byte) (colorArgb & 0xFF)}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createHeatmapStyle(Workbook wb, double load) {
        CellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);

        int color;
        if (load >= 1.5) {
            color = COLOR_OVERLOAD_HIGH;
        } else if (load >= 1.0) {
            color = COLOR_OVERLOAD_MED;
        } else {
            color = COLOR_NORMAL;
        }

        style.setFillForegroundColor(new XSSFColor(
            new byte[]{(byte) ((color >> 16) & 0xFF),
                       (byte) ((color >> 8) & 0xFF),
                       (byte) (color & 0xFF)}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    // ========== Utility Methods ==========

    private LocalDate[] getDateRange(List<Task> tasks) {
        LocalDate earliest = tasks.stream()
            .map(Task::startDate)
            .filter(Objects::nonNull)
            .min(LocalDate::compareTo)
            .orElse(LocalDate.now());

        LocalDate latest = tasks.stream()
            .map(Task::endDate)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse(earliest.plusDays(7));

        return new LocalDate[]{earliest, latest};
    }

    private Map<String, Integer> buildPersonColorMap(List<Task> tasks) {
        Set<String> persons = new LinkedHashSet<>();
        for (Task t : tasks) {
            for (Assignment a : t.assignments()) {
                persons.add(a.person());
            }
        }

        Map<String, Integer> map = new HashMap<>();
        int idx = 0;
        for (String p : persons) {
            map.put(p, TASK_COLORS[idx % TASK_COLORS.length]);
            idx++;
        }
        return map;
    }

    private int getTaskColor(Task task, Map<String, Integer> personColorMap) {
        if (task.assignments().isEmpty()) {
            return TASK_COLORS[0];
        }
        // Use the first person's color
        String firstPerson = task.assignments().get(0).person();
        return personColorMap.getOrDefault(firstPerson, TASK_COLORS[0]);
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value);
        }
        if (style != null) {
            cell.setCellStyle(style);
        }
    }
}
