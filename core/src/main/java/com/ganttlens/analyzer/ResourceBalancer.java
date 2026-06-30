package com.ganttlens.analyzer;

import com.ganttlens.model.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates balance suggestions for overload situations.
 * Suggests moving non-critical tasks to resolve resource conflicts.
 */
public class ResourceBalancer {

    private final CriticalPathAnalyzer criticalPathAnalyzer;

    public ResourceBalancer() {
        this.criticalPathAnalyzer = new CriticalPathAnalyzer();
    }

    /**
     * Generates balance suggestions for overload situations.
     */
    public List<BalanceSuggestion> balance(GanttSchedule schedule, List<OverloadRecord> overloads) {
        List<BalanceSuggestion> suggestions = new ArrayList<>();
        CriticalPathResult criticalPath = criticalPathAnalyzer.analyze(schedule);

        for (OverloadRecord overload : overloads) {
            // Find non-critical tasks that can be moved
            for (PersonDailyLoad.TaskLoad taskLoad : overload.tasks()) {
                Task task = schedule.findTaskByName(taskLoad.taskName());
                if (task == null) continue;

                // Check if task is on critical path
                Integer float_days = criticalPath.taskFloats().get(task.name());
                if (float_days == null || float_days == 0) {
                    // Critical path task - cannot move, suggest reducing load instead
                    suggestions.add(new BalanceSuggestion(
                        overload.person(),
                        task.name(),
                        overload.date(),
                        "关键路径任务，建议减少分配比例至 " + calculateReducedRatio(overload, taskLoad) + "%"
                    ));
                } else {
                    // Non-critical task - can be postponed
                    LocalDate suggestedDate = findNextAvailableDate(schedule, overload, task);
                    if (suggestedDate != null && suggestedDate.isAfter(overload.date())) {
                        suggestions.add(new BalanceSuggestion(
                            overload.person(),
                            task.name(),
                            suggestedDate,
                            "非关键路径任务，可推迟 " + float_days + " 天"
                        ));
                    }
                }
            }
        }

        return suggestions;
    }

    /**
     * Finds the next available date for a task to start without causing overload.
     */
    private LocalDate findNextAvailableDate(GanttSchedule schedule, OverloadRecord overload, Task task) {
        LocalDate current = overload.date().plusDays(1);
        int maxSearchDays = 30;

        for (int i = 0; i < maxSearchDays; i++) {
            if (!isOverloadedOnDate(schedule, overload.person(), current)) {
                return current;
            }
            current = current.plusDays(1);
        }
        return null;
    }

    /**
     * Checks if a person is overloaded on a specific date.
     */
    private boolean isOverloadedOnDate(GanttSchedule schedule, String person, LocalDate date) {
        double totalLoad = 0;
        for (Task task : schedule.tasks()) {
            if (task.startDate() == null || task.endDate() == null) continue;
            if (date.isBefore(task.startDate()) || date.isAfter(task.endDate())) continue;

            for (Assignment assignment : task.assignments()) {
                if (assignment.person().equals(person)) {
                    totalLoad += assignment.ratio();
                }
            }
        }
        return totalLoad > 1.0;
    }

    /**
     * Calculates a reduced ratio for critical path tasks.
     */
    private double calculateReducedRatio(OverloadRecord overload, PersonDailyLoad.TaskLoad taskLoad) {
        double excess = overload.totalLoad() - 1.0;
        double reduceBy = Math.min(excess, taskLoad.ratio() * 0.5);
        return Math.round((taskLoad.ratio() - reduceBy) * 100);
    }
}
