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
    private LocalDate projectStartDate = null;
    private final AtomicInteger taskCounter = new AtomicInteger(0);

    // Alias mapping: alias -> task name
    private final Map<String, String> aliasMap = new LinkedHashMap<>();

    // Last task name for 'then' chain
    private String lastTaskName = null;

    // Current group name for task groups
    private String currentGroup = null;

    @Override
    public Void visitGanttFile(PlantUMLGanttParser.GanttFileContext ctx) {
        // Visit directives
        for (PlantUMLGanttParser.DirectiveContext directive : ctx.directive()) {
            visit(directive);
        }
        // Visit task group directives
        for (PlantUMLGanttParser.TaskGroupDirectiveContext tg : ctx.taskGroupDirective()) {
            visitTaskGroupDirective(tg);
        }
        // Visit tasks (regular, arrow dependencies, then tasks, milestones)
        for (ParseTree child : ctx.children) {
            if (child instanceof PlantUMLGanttParser.TaskContext task) {
                visit(task);
            } else if (child instanceof PlantUMLGanttParser.ArrowDependencyContext arrow) {
                visitArrowDependency(arrow);
            } else if (child instanceof PlantUMLGanttParser.ThenTaskContext then) {
                visitThenTask(then);
            } else if (child instanceof PlantUMLGanttParser.MilestoneContext milestone) {
                visitMilestone(milestone);
            }
        }
        return null;
    }

    // ========== Directive Visitors ==========

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
    public Void visitDateRangeCloseDirective(PlantUMLGanttParser.DateRangeCloseDirectiveContext ctx) {
        LocalDate start = parseDate(ctx.DATE_TOKEN(0).getText());
        LocalDate end = parseDate(ctx.DATE_TOKEN(1).getText());
        LocalDate current = start;
        while (!current.isAfter(end)) {
            holidays.add(current);
            current = current.plusDays(1);
        }
        return null;
    }

    @Override
    public Void visitDateOpenDirective(PlantUMLGanttParser.DateOpenDirectiveContext ctx) {
        LocalDate date = parseDate(ctx.DATE_TOKEN().getText());
        holidays.remove(date);
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
    public Void visitProjectStartDirective(PlantUMLGanttParser.ProjectStartDirectiveContext ctx) {
        projectStartDate = parseDate(ctx.DATE_TOKEN().getText());
        return null;
    }

    @Override
    public Void visitTaskGroupDirective(PlantUMLGanttParser.TaskGroupDirectiveContext ctx) {
        StringBuilder sb = new StringBuilder();
        // Skip first and last children (the DASH tokens)
        for (int i = 1; i < ctx.getChildCount() - 1; i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof TerminalNode) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(child.getText());
            }
        }
        currentGroup = sb.toString();
        return null;
    }

    // ========== Task Visitors ==========

    @Override
    public Void visitTask(PlantUMLGanttParser.TaskContext ctx) {
        String group = currentGroup;

        PlantUMLGanttParser.TaskBodyContext body = ctx.taskBody();
        String name = extractTaskName(body.taskName());
        String id = "task-" + taskCounter.incrementAndGet();

        // Handle alias
        if (body.alias != null) {
            String alias = body.alias.getText();
            aliasMap.put(alias, name);
        }

        List<Assignment> assignments = new ArrayList<>();
        if (body.taskResource() != null) {
            assignments = extractResources(body.taskResource().resourceList());
        }

        List<String> dependencyIds = new ArrayList<>();
        LocalDate startDate = null;
        int durationDays = 0;

        PlantUMLGanttParser.TaskTimingContext timing = body.taskTiming();
        if (timing != null) {
            if (timing.requiresOrLastsClause() != null) {
                durationDays = parseDuration(timing.requiresOrLastsClause().duration());
            }
            if (timing.startsAtOrDateClause() != null) {
                PlantUMLGanttParser.StartsAtOrDateClauseContext startsAt = timing.startsAtOrDateClause();
                if (startsAt.startDate() != null) {
                    // starts at [task]'s end/start or starts at DATE
                    if (startsAt.startDate().DATE_TOKEN() != null) {
                        startDate = parseDate(startsAt.startDate().DATE_TOKEN().getText());
                    } else if (startsAt.startDate().taskRef() != null) {
                        String refName = extractTaskName(startsAt.startDate().taskRef().taskName());
                        dependencyIds.add(resolveAlias(refName));
                    }
                } else if (startsAt.DATE_TOKEN() != null) {
                    // starts DATE
                    startDate = parseDate(startsAt.DATE_TOKEN().getText());
                } else if (startsAt.ENDS() != null) {
                    // ends DATE - set end date directly (will be handled later)
                    // For now, we treat this as a dependency if it references a task
                }
            }
        }

        // Handle 'is completed' status
        TaskStatus status = TaskStatus.PENDING;
        if (body.IS_COMPLETED() != null) {
            status = TaskStatus.COMPLETED;
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
            status
        );

        tasks.add(task);
        lastTaskName = name;
        return null;
    }

    @Override
    public Void visitThenTask(PlantUMLGanttParser.ThenTaskContext ctx) {
        String group = currentGroup;

        PlantUMLGanttParser.TaskBodyContext body = ctx.taskBody();
        String name = extractTaskName(body.taskName());
        String id = "task-" + taskCounter.incrementAndGet();

        List<Assignment> assignments = new ArrayList<>();
        if (body.taskResource() != null) {
            assignments = extractResources(body.taskResource().resourceList());
        }

        List<String> dependencyIds = new ArrayList<>();
        // 'then' implies dependency on last task
        if (lastTaskName != null) {
            dependencyIds.add(lastTaskName);
        }

        LocalDate startDate = null;
        int durationDays = 0;

        PlantUMLGanttParser.TaskTimingContext timing = body.taskTiming();
        if (timing != null) {
            if (timing.requiresOrLastsClause() != null) {
                durationDays = parseDuration(timing.requiresOrLastsClause().duration());
            }
            if (timing.startsAtOrDateClause() != null) {
                PlantUMLGanttParser.StartsAtOrDateClauseContext startsAt = timing.startsAtOrDateClause();
                if (startsAt.startDate() != null && startsAt.startDate().DATE_TOKEN() != null) {
                    startDate = parseDate(startsAt.startDate().DATE_TOKEN().getText());
                }
            }
        }

        // Handle 'is completed' status
        TaskStatus status = TaskStatus.PENDING;
        if (body.IS_COMPLETED() != null) {
            status = TaskStatus.COMPLETED;
        }

        Task task = new Task(
            id,
            name,
            group,
            startDate,
            null,
            durationDays,
            assignments,
            dependencyIds,
            status
        );

        tasks.add(task);
        lastTaskName = name;
        return null;
    }

    @Override
    public Void visitArrowDependency(PlantUMLGanttParser.ArrowDependencyContext ctx) {
        // Extract all task references in the arrow chain
        List<String> taskNames = new ArrayList<>();
        for (PlantUMLGanttParser.TaskRefContext ref : ctx.taskRef()) {
            taskNames.add(resolveAlias(extractTaskName(ref.taskName())));
        }

        // Create dependencies: each task depends on the previous one
        for (int i = 1; i < taskNames.size(); i++) {
            String fromName = taskNames.get(i - 1);
            String toName = taskNames.get(i);

            // Find the target task and add dependency
            for (Task task : tasks) {
                if (task.name().equals(toName)) {
                    List<String> newDeps = new ArrayList<>(task.dependencyIds());
                    if (!newDeps.contains(fromName)) {
                        newDeps.add(fromName);
                        updateTaskDependencies(task, newDeps);
                    }
                    break;
                }
            }
        }

        return null;
    }

    @Override
    public Void visitMilestone(PlantUMLGanttParser.MilestoneContext ctx) {
        String name = extractTaskName(ctx.taskName());
        String id = "task-" + taskCounter.incrementAndGet();

        LocalDate milestoneDate = null;
        List<String> dependencyIds = new ArrayList<>();

        if (ctx.taskRef() != null) {
            // happens at [task]'s end
            String refName = resolveAlias(extractTaskName(ctx.taskRef().taskName()));
            dependencyIds.add(refName);
        } else if (ctx.DATE_TOKEN() != null) {
            milestoneDate = parseDate(ctx.DATE_TOKEN().getText());
        }

        // Milestone is a zero-duration task
        Task milestone = new Task(
            id,
            name,
            null,
            milestoneDate,
            milestoneDate, // end = start for milestone
            0,
            List.of(),
            dependencyIds,
            TaskStatus.PENDING
        );

        tasks.add(milestone);
        lastTaskName = name;
        return null;
    }

    // ========== Helper Methods ==========

    private String resolveAlias(String name) {
        return aliasMap.getOrDefault(name, name);
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
        int value = Integer.parseInt(ctx.INTEGER().getText());
        // Convert weeks to days (5 working days per week)
        if (ctx.WEEKS() != null) {
            return value * 5;
        }
        return value;
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
            projectStartDate,
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
        // Use project start date if specified, otherwise use first task's start date
        LocalDate defaultStart = projectStartDate != null ? projectStartDate : LocalDate.now();

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
                    LocalDate startDate = defaultStart;
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

    private void updateTaskDependencies(Task task, List<String> newDeps) {
        int index = tasks.indexOf(task);
        if (index >= 0) {
            tasks.set(index, new Task(
                task.id(),
                task.name(),
                task.group(),
                task.startDate(),
                task.endDate(),
                task.durationDays(),
                task.assignments(),
                newDeps,
                task.status()
            ));
        }
    }
}
