package com.ganttlens.parser;

import com.ganttlens.model.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Walks the ANTLR4 parse tree and builds domain model objects.
 */
public class GanttParseListener extends PlantUMLGanttBaseVisitor<Void> {

    private final List<Task> tasks = new ArrayList<>();
    private final Set<LocalDate> holidays = new HashSet<>();
    private final Set<ScheduleConfig.PersonOffEntry> personOffDays = new HashSet<>();
    private boolean saturdayClosed = false;
    private boolean sundayClosed = false;
    private String title = "";
    private String printscale = "";
    private final AtomicInteger taskCounter = new AtomicInteger(0);

    @Override
    public Void visitGanttFile(PlantUMLGanttParser.GanttFileContext ctx) {
        // Visit directives
        for (PlantUMLGanttParser.DirectiveContext directive : ctx.directive()) {
            visit(directive);
        }
        // Visit tasks
        for (PlantUMLGanttParser.TaskContext task : ctx.task()) {
            visit(task);
        }
        return null;
    }

    @Override
    public Void visitWeekendsCloseDirective(PlantUMLGanttParser.WeekendsCloseDirectiveContext ctx) {
        if (ctx.SATURDAY_CLOSE() != null) {
            saturdayClosed = true;
        } else if (ctx.SUNDAY_CLOSE() != null) {
            sundayClosed = true;
        }
        return null;
    }

    @Override
    public Void visitHolidayCloseDirective(PlantUMLGanttParser.HolidayCloseDirectiveContext ctx) {
        LocalDate date = parseDate(ctx.DATE_TOKEN().getText());
        holidays.add(date);
        return null;
    }

    @Override
    public Void visitPersonOffDirective(PlantUMLGanttParser.PersonOffDirectiveContext ctx) {
        String person = extractPersonName(ctx.personName());
        LocalDate date = parseDate(ctx.DATE_TOKEN().getText());
        personOffDays.add(new ScheduleConfig.PersonOffEntry(person, date));
        return null;
    }

    @Override
    public Void visitTitleDirective(PlantUMLGanttParser.TitleDirectiveContext ctx) {
        title = extractJoinedWords(ctx.WORD());
        return null;
    }

    @Override
    public Void visitPrintscaleDirective(PlantUMLGanttParser.PrintscaleDirectiveContext ctx) {
        if (ctx.WEEKLY() != null) printscale = "weekly";
        else if (ctx.DAILY() != null) printscale = "daily";
        else if (ctx.MONTHLY() != null) printscale = "monthly";
        return null;
    }

    @Override
    public Void visitTask(PlantUMLGanttParser.TaskContext ctx) {
        String group = null;
        if (ctx.taskGroup() != null) {
            group = extractJoinedWords(ctx.taskGroup().WORD());
        }

        PlantUMLGanttParser.TaskBodyContext body = ctx.taskBody();
        String name = extractTaskName(body.taskName());
        String id = "task-" + taskCounter.incrementAndGet();

        List<Assignment> assignments = new ArrayList<>();
        if (body.taskResource() != null) {
            assignments = extractResources(body.taskResource().resourceList());
        }

        List<String> dependencyIds = new ArrayList<>();
        LocalDate startDate = null;
        int durationDays = 0;

        PlantUMLGanttParser.TaskTimingContext timing = body.taskTiming();
        if (timing.requiresClause() != null) {
            durationDays = parseDuration(timing.requiresClause().duration());
        }
        if (timing.startsAtClause() != null) {
            PlantUMLGanttParser.StartsAtClauseContext startsAt = timing.startsAtClause();
            if (startsAt.startDate().DATE_TOKEN() != null) {
                startDate = parseDate(startsAt.startDate().DATE_TOKEN().getText());
            } else if (startsAt.startDate().taskRef() != null) {
                String refName = extractTaskName(startsAt.startDate().taskRef().taskName());
                dependencyIds.add(refName);
            }
        }

        Task task = new Task(
            id,
            name,
            group,
            startDate,
            null, // endDate will be computed later
            durationDays,
            assignments,
            dependencyIds,
            TaskStatus.PENDING
        );

        tasks.add(task);
        return null;
    }

    private List<Assignment> extractResources(PlantUMLGanttParser.ResourceListContext ctx) {
        List<Assignment> result = new ArrayList<>();
        for (PlantUMLGanttParser.ResourceContext resource : ctx.resource()) {
            String person = extractPersonName(resource.personName());
            double ratio = 1.0;
            if (resource.ratio() != null) {
                ratio = Integer.parseInt(resource.ratio().INTEGER().getText()) / 100.0;
            }
            result.add(new Assignment(person, ratio));
        }
        return result;
    }

    private String extractPersonName(PlantUMLGanttParser.PersonNameContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (TerminalNode node : ctx.WORD()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(node.getText());
        }
        return sb.toString();
    }

    private String extractTaskName(PlantUMLGanttParser.TaskNameContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (TerminalNode node : ctx.WORD()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(node.getText());
        }
        return sb.toString();
    }

    private String extractJoinedWords(List<TerminalNode> words) {
        StringBuilder sb = new StringBuilder();
        for (TerminalNode node : words) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(node.getText());
        }
        return sb.toString();
    }

    private int parseDuration(PlantUMLGanttParser.DurationContext ctx) {
        return Integer.parseInt(ctx.INTEGER().getText());
    }

    private LocalDate parseDate(String dateStr) {
        return LocalDate.parse(dateStr);
    }

    /**
     * Builds the final GanttSchedule after parsing is complete.
     */
    public GanttSchedule buildSchedule() {
        // Build config first so it can be used for date calculations
        ScheduleConfig config = new ScheduleConfig(
            title,
            null, // projectStartDate - will be derived from first task
            saturdayClosed,
            sundayClosed,
            holidays,
            personOffDays
        );

        // Compute end dates and resolve dependencies
        resolveTaskDates(config);

        return new GanttSchedule(config, List.copyOf(tasks));
    }

    private void resolveTaskDates(ScheduleConfig config) {
        // First pass: resolve tasks with explicit start dates
        for (Task task : tasks) {
            if (task.startDate() != null) {
                LocalDate endDate = computeEndDate(task.startDate(), task.durationDays(), config);
                updateTask(task, task.startDate(), endDate);
            }
        }

        // Second pass: resolve tasks with dependencies
        boolean changed = true;
        int iterations = 0;
        while (changed && iterations < 100) {
            changed = false;
            iterations++;
            // Recreate map each iteration to reflect updated tasks
            Map<String, Task> taskMap = new LinkedHashMap<>();
            for (Task t : tasks) {
                taskMap.put(t.name(), t);
            }
            for (Task task : tasks) {
                if (task.startDate() != null) continue; // already resolved
                if (task.dependencyIds().isEmpty() && task.durationDays() > 0) {
                    // No dependency, no start date - use default start
                    LocalDate startDate = LocalDate.now();
                    LocalDate endDate = computeEndDate(startDate, task.durationDays(), config);
                    updateTask(task, startDate, endDate);
                    changed = true;
                    continue;
                }
                for (String depName : task.dependencyIds()) {
                    Task dep = taskMap.get(depName);
                    if (dep != null && dep.endDate() != null) {
                        LocalDate startDate = nextWorkingDay(dep.endDate(), config);
                        LocalDate endDate = computeEndDate(startDate, task.durationDays(), config);
                        updateTask(task, startDate, endDate);
                        changed = true;
                        break;
                    }
                }
            }
        }
    }

    private LocalDate computeEndDate(LocalDate startDate, int workDays, ScheduleConfig config) {
        if (workDays <= 0) return startDate;
        LocalDate current = startDate;
        int daysAdded = 0;
        while (daysAdded < workDays - 1) {
            current = nextWorkingDay(current, config);
            daysAdded++;
        }
        return current;
    }

    private LocalDate nextWorkingDay(LocalDate date, ScheduleConfig config) {
        LocalDate next = date.plusDays(1);
        while (!config.isWorkingDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    private void updateTask(Task task, LocalDate startDate, LocalDate endDate) {
        int index = tasks.indexOf(task);
        if (index >= 0) {
            tasks.set(index, new Task(
                task.id(),
                task.name(),
                task.group(),
                startDate,
                endDate,
                task.durationDays(),
                task.assignments(),
                task.dependencyIds(),
                task.status()
            ));
        }
    }
}
